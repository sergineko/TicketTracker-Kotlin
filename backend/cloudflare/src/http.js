export class HttpError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
    this.code = code;
  }
}
export function requireValue(condition, code = 'invalid_request', status = 400) {
  if (!condition) throw new HttpError(status, code);
}
export function json(body, status = 200) {
  return Response.json(body, {
    status,
    headers: {
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
    },
  });
}
export async function readBytes(request, limit) {
  const length = Number(request.headers.get('content-length'));
  if (length > limit) throw new HttpError(413, 'body_too_large');
  const reader = request.body?.getReader();
  if (!reader) return new Uint8Array();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > limit) {
        await reader.cancel();
        throw new HttpError(413, 'body_too_large');
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.length;
  }
  return bytes;
}
export async function readJson(request, limit = 196608) {
  requireValue(
    request.headers.get('content-type')?.split(';')[0] === 'application/json',
    'json_required',
    415
  );
  try {
    return JSON.parse(new TextDecoder().decode(await readBytes(request, limit)));
  } catch (error) {
    if (error instanceof HttpError) throw error;
    throw new HttpError(400, 'invalid_json');
  }
}
export const uuid = (value) =>
  typeof value === 'string' &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
export const hex = (bytes) =>
  Array.from(new Uint8Array(bytes), (b) => b.toString(16).padStart(2, '0')).join('');
export const sha256 = async (bytes) => hex(await crypto.subtle.digest('SHA-256', bytes));
export async function keyedHash(secret, value) {
  requireValue(typeof secret === 'string' && secret.length >= 32, 'service_not_configured', 503);
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  return hex(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(value)));
}
export async function rateLimit(env, key, limit, windowSeconds) {
  const now = Math.floor(Date.now() / 1000);
  const result = await env.DB.prepare(
    `INSERT INTO tt_rate_limits(key,hits,resets_at) VALUES(?,1,?)
    ON CONFLICT(key) DO UPDATE SET hits=CASE WHEN resets_at<=? THEN 1 ELSE hits+1 END,
    resets_at=CASE WHEN resets_at<=? THEN excluded.resets_at ELSE resets_at END RETURNING hits`
  )
    .bind(key, now + windowSeconds, now, now)
    .first();
  requireValue(result.hits <= limit, 'rate_limited', 429);
}
