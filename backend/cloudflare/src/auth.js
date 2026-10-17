import { HttpError, requireValue, readJson, json, keyedHash, rateLimit, uuid } from './http.js';

const nowSeconds = () => Math.floor(Date.now() / 1000);
const normalizeEmail = (value) => (typeof value === 'string' ? value.trim().toLowerCase() : '');
const validEmail = (value) =>
  value.length <= 254 &&
  /^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z]{2,}$/i.test(value);
function randomToken() {
  return btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32))))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replaceAll('=', '');
}
function randomCode() {
  const sample = new Uint32Array(1);
  do {
    crypto.getRandomValues(sample);
  } while (sample[0] >= 4294000000);
  return String(sample[0] % 1000000).padStart(6, '0');
}
function constantEqual(a, b) {
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return difference === 0;
}
export async function session(request, env, optional = false) {
  const token = request.headers.get('Authorization')?.match(/^Bearer ([A-Za-z0-9_-]{43})$/)?.[1];
  if (!token) {
    if (optional) return null;
    throw new HttpError(401, 'authentication_required');
  }
  const tokenHash = await keyedHash(env.SESSION_PEPPER, `session:${token}`);
  const user = await env.DB.prepare(
    `SELECT p.id,p.email,s.expires_at FROM tt_sessions s
    JOIN tt_profiles p ON p.id=s.owner_id WHERE s.token_hash=? AND s.expires_at>? AND p.deleted_at IS NULL`
  )
    .bind(tokenHash, nowSeconds())
    .first();
  if (!user && !optional) throw new HttpError(401, 'session_expired');
  return user ? { ...user, tokenHash } : null;
}
async function createSession(env, owner) {
  const token = randomToken();
  const expires = nowSeconds() + 30 * 86400;
  const hash = await keyedHash(env.SESSION_PEPPER, `session:${token}`);
  await env.DB.prepare(
    'INSERT INTO tt_sessions(token_hash,owner_id,expires_at,created_at) VALUES(?,?,?,?)'
  )
    .bind(hash, owner.id, expires, nowSeconds())
    .run();
  return json({ user: { id: owner.id, email: owner.email }, token, expires_at: expires });
}
async function ownerForEmail(env, email) {
  await env.DB.prepare(
    'INSERT INTO tt_profiles(id,email,created_at) VALUES(?,?,?) ON CONFLICT(email) DO NOTHING'
  )
    .bind(crypto.randomUUID(), email, nowSeconds())
    .run();
  const owner = await env.DB.prepare(
    'SELECT id,email FROM tt_profiles WHERE email=? AND deleted_at IS NULL'
  )
    .bind(email)
    .first();
  requireValue(owner, 'account_unavailable', 403);
  await env.DB.prepare(
    "INSERT INTO tt_identities(provider,subject,owner_id) VALUES('email',?,?) ON CONFLICT(provider,subject) DO NOTHING"
  )
    .bind(email, owner.id)
    .run();
  return owner;
}
async function authRate(request, env, prefix) {
  const ipHash = await keyedHash(
    env.SESSION_PEPPER,
    `ip:${request.headers.get('CF-Connecting-IP') || 'local'}`
  );
  await rateLimit(env, `${prefix}:${ipHash}`, 10, 600);
}
export async function authRoute(request, env, path) {
  if (request.method !== 'POST') throw new HttpError(405, 'method_not_allowed');
  if (path === '/v1/auth/email/request') {
    await authRate(request, env, 'email-ip');
    requireValue(env.RESEND_API_KEY && env.EMAIL_FROM, 'email_not_configured', 503);
    const input = await readJson(request, 2048);
    const email = normalizeEmail(input.email);
    requireValue(validEmail(email), 'invalid_email');
    const emailHash = await keyedHash(env.SESSION_PEPPER, `email:${email}`);
    await rateLimit(env, `email:${emailHash}`, 5, 86400);
    await rateLimit(env, 'email-global-day', 90, 86400);
    const id = crypto.randomUUID();
    const code = randomCode();
    const now = nowSeconds();
    const digest = await keyedHash(env.SESSION_PEPPER, `otp:${id}:${code}`);
    await env.DB.batch([
      env.DB.prepare(
        'UPDATE tt_email_challenges SET consumed_at=? WHERE email=? AND consumed_at IS NULL'
      ).bind(now, email),
      env.DB.prepare(
        'INSERT INTO tt_email_challenges(id,email,digest,expires_at,created_at) VALUES(?,?,?,?,?)'
      ).bind(id, email, digest, now + 600, now),
    ]);
    const response = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${env.RESEND_API_KEY}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': id,
      },
      body: JSON.stringify({
        from: env.EMAIL_FROM,
        to: [email],
        subject: 'Tu código de acceso a TicketTracker',
        text: `Tu código de acceso es ${code}. Caduca en 10 minutos. Si no lo has solicitado, ignora este correo. No compartas este código.`,
      }),
    });
    if (!response.ok) {
      await env.DB.prepare('UPDATE tt_email_challenges SET consumed_at=? WHERE id=?')
        .bind(now, id)
        .run();
      throw new HttpError(503, 'email_delivery_unavailable');
    }
    return json({ challenge_id: id, expires_in: 600 }, 202);
  }
  if (path === '/v1/auth/email/verify') {
    await authRate(request, env, 'verify-ip');
    const input = await readJson(request, 2048);
    requireValue(uuid(input.challenge_id) && /^\d{6}$/.test(input.code), 'invalid_code', 401);
    const now = nowSeconds();
    const challenge = await env.DB.prepare(
      `UPDATE tt_email_challenges SET attempts=attempts+1
      WHERE id=? AND expires_at>? AND consumed_at IS NULL AND attempts<5 RETURNING email,digest`
    )
      .bind(input.challenge_id, now)
      .first();
    requireValue(challenge, 'invalid_code', 401);
    const digest = await keyedHash(env.SESSION_PEPPER, `otp:${input.challenge_id}:${input.code}`);
    requireValue(constantEqual(digest, challenge.digest), 'invalid_code', 401);
    const consumed = await env.DB.prepare(
      'UPDATE tt_email_challenges SET consumed_at=? WHERE id=? AND consumed_at IS NULL RETURNING id'
    )
      .bind(now, input.challenge_id)
      .first();
    requireValue(consumed, 'invalid_code', 401);
    return createSession(env, await ownerForEmail(env, challenge.email));
  }
  if (path === '/v1/auth/google/nonce') {
    requireValue(env.GOOGLE_CLIENT_ID, 'google_not_configured', 503);
    await authRate(request, env, 'google-ip');
    const id = crypto.randomUUID();
    const nonce = randomToken();
    await env.DB.prepare('INSERT INTO tt_google_challenges(id,nonce,expires_at) VALUES(?,?,?)')
      .bind(id, nonce, nowSeconds() + 300)
      .run();
    return json({ challenge_id: id, nonce });
  }
  if (path === '/v1/auth/google') {
    requireValue(env.GOOGLE_CLIENT_ID, 'google_not_configured', 503);
    await authRate(request, env, 'google-verify-ip');
    const input = await readJson(request, 20000);
    requireValue(uuid(input.challenge_id));
    const challenge = await env.DB.prepare(
      'SELECT nonce FROM tt_google_challenges WHERE id=? AND expires_at>? AND consumed_at IS NULL'
    )
      .bind(input.challenge_id, nowSeconds())
      .first();
    requireValue(challenge, 'invalid_google_challenge', 401);
    const claims = await verifyGoogleToken(
      input.id_token,
      env.GOOGLE_CLIENT_ID,
      challenge.nonce,
      env.GOOGLE_ANDROID_CLIENT_ID
    );
    const consumed = await env.DB.prepare(
      'UPDATE tt_google_challenges SET consumed_at=? WHERE id=? AND consumed_at IS NULL RETURNING id'
    )
      .bind(nowSeconds(), input.challenge_id)
      .first();
    requireValue(consumed, 'invalid_google_challenge', 401);
    let owner = await env.DB.prepare(
      "SELECT p.id,p.email FROM tt_identities i JOIN tt_profiles p ON p.id=i.owner_id WHERE i.provider='google' AND i.subject=? AND p.deleted_at IS NULL"
    )
      .bind(claims.sub)
      .first();
    if (!owner) {
      const email = normalizeEmail(claims.email);
      requireValue(validEmail(email), 'invalid_google_email', 401);
      const existing = await env.DB.prepare(
        'SELECT id,email FROM tt_profiles WHERE email=? AND deleted_at IS NULL'
      )
        .bind(email)
        .first();
      const signedIn = await session(request, env, true);
      // Google is authoritative for Gmail and Workspace; other email domains require a code first.
      const authoritative = email.endsWith('@gmail.com') || typeof claims.hd === 'string';
      requireValue(authoritative || signedIn?.email === email, 'verify_email_before_linking', 409);
      owner = existing || (await ownerForEmail(env, email));
      if (signedIn) requireValue(signedIn.id === owner.id, 'identity_conflict', 409);
      await env.DB.prepare(
        "INSERT INTO tt_identities(provider,subject,owner_id) VALUES('google',?,?) ON CONFLICT(provider,subject) DO NOTHING"
      )
        .bind(claims.sub, owner.id)
        .run();
    }
    const currentSession = await session(request, env, true);
    if (currentSession) requireValue(currentSession.id === owner.id, 'identity_conflict', 409);
    return createSession(env, owner);
  }
  if (path === '/v1/auth/logout') {
    const user = await session(request, env);
    await env.DB.prepare('DELETE FROM tt_sessions WHERE token_hash=?').bind(user.tokenHash).run();
    return json({ logged_out: true });
  }
  throw new HttpError(404, 'not_found');
}

let googleKeys = { expires: 0, keys: [] };
async function verifyGoogleToken(token, audience, nonce, androidClientId) {
  try {
    requireValue(typeof token === 'string' && token.length < 18000, 'invalid_google_token', 401);
    const parts = token.split('.');
    requireValue(parts.length === 3, 'invalid_google_token', 401);
    const decode = (part) =>
      Uint8Array.from(atob(part.replaceAll('-', '+').replaceAll('_', '/')), (c) => c.charCodeAt(0));
    const header = JSON.parse(new TextDecoder().decode(decode(parts[0])));
    const claims = JSON.parse(new TextDecoder().decode(decode(parts[1])));
    requireValue(
      header.alg === 'RS256' && typeof header.kid === 'string',
      'invalid_google_token',
      401
    );
    if (Date.now() > googleKeys.expires || !googleKeys.keys.some((key) => key.kid === header.kid)) {
      const response = await fetch('https://www.googleapis.com/oauth2/v3/certs');
      requireValue(response.ok, 'google_unavailable', 503);
      const jwks = await response.json();
      googleKeys = { expires: Date.now() + 3600000, keys: jwks.keys };
    }
    const jwk = googleKeys.keys.find((key) => key.kid === header.kid);
    requireValue(jwk, 'invalid_google_token', 401);
    const key = await crypto.subtle.importKey(
      'jwk',
      jwk,
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
      false,
      ['verify']
    );
    const valid = await crypto.subtle.verify(
      'RSASSA-PKCS1-v1_5',
      key,
      decode(parts[2]),
      new TextEncoder().encode(`${parts[0]}.${parts[1]}`)
    );
    requireValue(
      valid &&
        ['https://accounts.google.com', 'accounts.google.com'].includes(claims.iss) &&
        claims.aud === audience &&
        (!claims.azp ||
          claims.azp === audience ||
          (androidClientId && claims.azp === androidClientId)) &&
        Number.isSafeInteger(claims.exp) &&
        claims.exp > nowSeconds() &&
        Number.isSafeInteger(claims.iat) &&
        claims.iat <= nowSeconds() + 120 &&
        claims.email_verified === true &&
        claims.nonce === nonce &&
        typeof claims.sub === 'string' &&
        claims.sub.length > 0,
      'invalid_google_token',
      401
    );
    return claims;
  } catch (error) {
    if (error instanceof HttpError) throw error;
    throw new HttpError(401, 'invalid_google_token');
  }
}
