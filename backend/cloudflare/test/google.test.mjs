import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, randomUUID, sign } from 'node:crypto';
import { Miniflare } from 'miniflare';
import { applySchema } from './helpers.mjs';

let mf;
let db;
const audience = 'web-client.apps.googleusercontent.com';
const androidClient = 'android-client.apps.googleusercontent.com';
const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'test-key', alg: 'RS256', use: 'sig' };
before(async () => {
  mf = new Miniflare({
    modules: true,
    modulesRules: [{ type: 'ESModule', include: ['**/*.js'] }],
    scriptPath: new URL('../src/worker.js', import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1'),
    compatibilityDate: '2026-01-11',
    d1Databases: ['DB'],
    r2Buckets: ['DOCUMENTS'],
    bindings: {
      SESSION_PEPPER: 'google-test-only-at-least-32-characters',
      GOOGLE_CLIENT_ID: audience,
      GOOGLE_ANDROID_CLIENT_ID: androidClient,
    },
    outboundService: async (request) =>
      new URL(request.url).hostname === 'www.googleapis.com'
        ? Response.json({ keys: [jwk] })
        : new Response('Unexpected request', { status: 502 }),
  });
  db = await mf.getD1Database('DB');
  await applySchema(db);
});
after(async () => {
  await mf?.dispose();
});
async function api(path, body, token) {
  const headers = { 'Content-Type': 'application/json', 'CF-Connecting-IP': randomUUID() };
  if (token) headers.Authorization = `Bearer ${token}`;
  return mf.dispatchFetch(`https://tickettracker.test/v1/auth/${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body ?? {}),
  });
}
async function challenge() {
  const response = await api('google/nonce');
  assert.equal(response.status, 200);
  return response.json();
}
function jwt(nonce, overrides = {}, wrongSignature = false) {
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', kid: jwk.kid })).toString('base64url');
  const claims = Buffer.from(
    JSON.stringify({
      iss: 'https://accounts.google.com',
      sub: 'test-google-sub',
      aud: audience,
      azp: androidClient,
      iat: now,
      exp: now + 3600,
      email: 'user@gmail.com',
      email_verified: true,
      nonce,
      ...overrides,
    })
  ).toString('base64url');
  const data = `${header}.${claims}`;
  return `${data}.${wrongSignature ? Buffer.alloc(256).toString('base64url') : sign('RSA-SHA256', Buffer.from(data), privateKey).toString('base64url')}`;
}
test('signed Google tokens accept the configured Android party and consume the nonce once', async () => {
  const value = await challenge();
  const body = { challenge_id: value.challenge_id, id_token: jwt(value.nonce) };
  const response = await api('google', body);
  assert.equal(response.status, 200, await response.clone().text());
  const session = await response.json();
  assert.equal(session.user.email, 'user@gmail.com');
  assert.equal((await api('google', body)).status, 401);
});
test('signature, audience, authorized party, expiry, verification and nonce are all checked', async () => {
  for (const [claims, badSignature] of [
    [{}, true],
    [{ aud: 'other-client' }, false],
    [{ azp: 'untrusted-client' }, false],
    [{ exp: 1 }, false],
    [{ email_verified: false }, false],
    [{ nonce: 'wrong-nonce' }, false],
    [{ iss: 'https://evil.test' }, false],
  ]) {
    const value = await challenge();
    const response = await api('google', {
      challenge_id: value.challenge_id,
      id_token: jwt(value.nonce, claims, badSignature),
    });
    assert.equal(response.status, 401, await response.clone().text());
  }
});
test('Google cannot silently claim an email outside authoritative domains', async () => {
  const value = await challenge();
  const response = await api('google', {
    challenge_id: value.challenge_id,
    id_token: jwt(value.nonce, { sub: 'external-google-sub', email: 'external@example.test' }),
  });
  assert.equal(response.status, 409);
  assert.equal((await response.json()).error, 'verify_email_before_linking');
  assert.equal(
    (
      await db
        .prepare("SELECT count(*) AS count FROM tt_profiles WHERE email='external@example.test'")
        .first()
    ).count,
    0
  );
});
