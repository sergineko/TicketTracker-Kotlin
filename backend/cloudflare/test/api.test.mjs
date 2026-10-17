import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { randomUUID, createHash } from 'node:crypto';
import { Miniflare } from 'miniflare';

let mf;
let db;
const emails = [];
const base = 'https://api.tickettracker.test';
before(async () => {
  mf = new Miniflare({
    modules: true,
    modulesRules: [{ type: 'ESModule', include: ['**/*.js'] }],
    scriptPath: new URL('../src/worker.js', import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1'),
    compatibilityDate: '2026-01-11',
    d1Databases: ['DB'],
    r2Buckets: ['DOCUMENTS'],
    r2Persist: '../../.local/r2-test',
    bindings: {
      SESSION_PEPPER: 'local-test-only-secret-at-least-32-characters',
      RESEND_API_KEY: 'test-only',
      EMAIL_FROM: 'TicketTracker <login@example.test>',
      MAX_USER_BYTES: '104857600',
      MAX_USER_RECORDS: '2000',
      MAX_TOTAL_BYTES: '150000000',
    },
    outboundService: async (request) => {
      if (new URL(request.url).hostname === 'api.resend.com') {
        emails.push(await request.json());
        return Response.json({ id: randomUUID() });
      }
      return new Response('Unexpected outbound call', { status: 502 });
    },
  });
  db = await mf.getD1Database('DB');
  const sql = await readFile(new URL('../migrations/0001_initial.sql', import.meta.url), 'utf8');
  let statement = '';
  let trigger = false;
  for (const line of sql.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('--') || trimmed.startsWith('PRAGMA foreign_keys')) continue;
    if (trimmed.startsWith('CREATE TRIGGER')) trigger = true;
    statement += `${line}\n`;
    if ((trigger && trimmed === 'END;') || (!trigger && trimmed.endsWith(';'))) {
      await db.prepare(statement).run();
      statement = '';
      trigger = false;
    }
  }
});
after(async () => {
  await mf?.dispose();
});

async function api(
  path,
  { token, method = 'GET', body, bytes, type = 'application/json', ip = '127.0.0.1' } = {}
) {
  const headers = { 'CF-Connecting-IP': ip };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined || bytes) headers['Content-Type'] = type;
  const response = await mf.dispatchFetch(`${base}${path}`, {
    method,
    headers,
    body: bytes || (body === undefined ? undefined : JSON.stringify(body)),
  });
  return response;
}
async function login(email) {
  const start = await api('/v1/auth/email/request', { method: 'POST', body: { email }, ip: email });
  assert.equal(start.status, 202, await start.clone().text());
  const { challenge_id } = await start.json();
  const code = emails.findLast((message) => message.to[0] === email).text.match(/\b\d{6}\b/)[0];
  const finish = await api('/v1/auth/email/verify', {
    method: 'POST',
    body: { challenge_id, code },
    ip: email,
  });
  assert.equal(finish.status, 200, await finish.clone().text());
  return finish.json();
}
function purchase(overrides = {}) {
  return {
    schema_version: 1,
    merchant: 'Tienda de prueba',
    purchased_on: '2026-10-17',
    delivered_on: null,
    total_minor: 3995,
    currency: 'EUR',
    channel: 'IN_STORE',
    notes: '',
    order_number: '',
    lines: [
      {
        id: randomUUID(),
        quantity: 1,
        unit_price_minor: 3995,
        product: { id: randomUUID(), name: 'Auriculares', warranty_end: null, return_end: null },
      },
    ],
    document_ids: [],
    ...overrides,
  };
}
function mutation(type, payload, overrides = {}) {
  return {
    operation_id: randomUUID(),
    id: randomUUID(),
    expected_revision: 0,
    type,
    payload,
    ...overrides,
  };
}
let alice;
let bob;
test('health is public and sensitive routes require a session', async () => {
  assert.equal((await api('/health')).status, 200);
  assert.equal((await api('/v1/sync/changes')).status, 401);
});
test('JSON null and arrays are rejected as client errors without sending email', async () => {
  for (const body of [null, []])
    assert.equal(
      (await api('/v1/auth/email/request', { method: 'POST', body, ip: randomUUID() })).status,
      400
    );
  assert.equal(emails.length, 0);
});
test('email codes issue opaque sessions without exposing codes or stored tokens', async () => {
  alice = await login('alice@example.test');
  bob = await login('bob@example.test');
  assert.equal(alice.token.length, 43);
  const stored = await db
    .prepare('SELECT token_hash FROM tt_sessions WHERE owner_id=?')
    .bind(alice.user.id)
    .first();
  assert.notEqual(stored.token_hash, alice.token);
  assert.equal((await api('/v1/me', { token: alice.token })).status, 200);
});
test('a code can only be consumed once', async () => {
  const email = 'replay@example.test';
  const response = await api('/v1/auth/email/request', {
    method: 'POST',
    body: { email },
    ip: email,
  });
  const start = await response.json();
  const code = emails.at(-1).text.match(/\b\d{6}\b/)[0];
  assert.equal(
    (
      await api('/v1/auth/email/verify', {
        method: 'POST',
        body: { challenge_id: start.challenge_id, code },
        ip: email,
      })
    ).status,
    200
  );
  assert.equal(
    (
      await api('/v1/auth/email/verify', {
        method: 'POST',
        body: { challenge_id: start.challenge_id, code },
        ip: email,
      })
    ).status,
    401
  );
});
test('five incorrect attempts invalidate a challenge', async () => {
  const email = 'attempts@example.test';
  const response = await api('/v1/auth/email/request', {
    method: 'POST',
    body: { email },
    ip: email,
  });
  const start = await response.json();
  const code = emails.at(-1).text.match(/\b\d{6}\b/)[0];
  const wrong = code === '000000' ? '999999' : '000000';
  for (let i = 0; i < 5; i++)
    assert.equal(
      (
        await api('/v1/auth/email/verify', {
          method: 'POST',
          body: { challenge_id: start.challenge_id, code: wrong },
          ip: email,
        })
      ).status,
      401
    );
  assert.equal(
    (
      await api('/v1/auth/email/verify', {
        method: 'POST',
        body: { challenge_id: start.challenge_id, code },
        ip: email,
      })
    ).status,
    401
  );
});
let saved;
test('mutations are idempotent and feed changes stay scoped to their owner', async () => {
  saved = mutation('PURCHASE', purchase({ owner_id: bob.user.id }));
  const first = await api('/v1/sync/mutate', { token: alice.token, method: 'POST', body: saved });
  assert.equal(first.status, 200, await first.clone().text());
  const again = await api('/v1/sync/mutate', { token: alice.token, method: 'POST', body: saved });
  assert.deepEqual(await again.json(), await first.json());
  const a = await (await api('/v1/sync/changes', { token: alice.token })).json();
  const b = await (await api('/v1/sync/changes', { token: bob.token })).json();
  assert.equal(a.changes.length, 1);
  assert.equal(a.changes[0].body.owner_id, alice.user.id);
  assert.equal(b.changes.length, 0);
});
test('cross-owner update and stale revisions cannot overwrite data', async () => {
  const stolen = {
    ...saved,
    operation_id: randomUUID(),
    expected_revision: 1,
    payload: purchase({ merchant: 'Stolen' }),
  };
  assert.equal(
    (await api('/v1/sync/mutate', { token: bob.token, method: 'POST', body: stolen })).status,
    409
  );
  const stale = { ...saved, operation_id: randomUUID(), payload: purchase({ merchant: 'Stale' }) };
  assert.equal(
    (await api('/v1/sync/mutate', { token: alice.token, method: 'POST', body: stale })).status,
    409
  );
  const row = await db.prepare('SELECT body FROM tt_records WHERE id=?').bind(saved.id).first();
  assert.equal(JSON.parse(row.body).merchant, 'Tienda de prueba');
});
test('idempotency keys cannot be reused with different payloads', async () => {
  const altered = { ...saved, payload: purchase({ merchant: 'Changed' }) };
  const response = await api('/v1/sync/mutate', {
    token: alice.token,
    method: 'POST',
    body: altered,
  });
  assert.equal(response.status, 409);
  assert.equal((await response.json()).error, 'idempotency_key_reused');
});
test('a missing record cannot be inserted with a nonzero expected revision', async () => {
  const body = mutation('PURCHASE', purchase(), { expected_revision: 2 });
  assert.equal(
    (await api('/v1/sync/mutate', { token: alice.token, method: 'POST', body })).status,
    409
  );
  assert.equal(
    await db.prepare('SELECT id FROM tt_records WHERE id=?').bind(body.id).first(),
    null
  );
});
test('documents require matching bytes and hashes, stay private, and finalize only after upload', async () => {
  const bytes = new TextEncoder().encode('original receipt bytes');
  const variant = {
    id: randomUUID(),
    kind: 'ORIGINAL',
    mime_type: 'application/pdf',
    byte_size: bytes.length,
    sha256: createHash('sha256').update(bytes).digest('hex'),
  };
  const body = mutation('DOCUMENT', {
    display_name: 'Factura',
    extracted_text: '',
    variants: [variant],
  });
  assert.equal(
    (await api('/v1/sync/mutate', { token: alice.token, method: 'POST', body })).status,
    200
  );
  const objectPath = `/v1/documents/${body.id}/variants/${variant.id}`;
  assert.equal((await api(objectPath, { token: bob.token })).status, 404);
  assert.equal(
    (await api(`/v1/documents/${body.id}/finalize`, { token: alice.token, method: 'POST' })).status,
    409
  );
  assert.equal(
    (
      await api(objectPath, {
        token: alice.token,
        method: 'PUT',
        bytes: new TextEncoder().encode('wrong'),
        type: 'application/pdf',
      })
    ).status,
    422
  );
  assert.equal(
    (await api(objectPath, { token: alice.token, method: 'PUT', bytes, type: 'application/pdf' }))
      .status,
    200
  );
  assert.equal(
    (await api(`/v1/documents/${body.id}/finalize`, { token: alice.token, method: 'POST' })).status,
    200
  );
  const download = await api(objectPath, { token: alice.token });
  assert.equal(download.status, 200);
  assert.equal(download.headers.get('cache-control'), 'private, no-store');
  assert.deepEqual(new Uint8Array(await download.arrayBuffer()), bytes);
  const replay = await (
    await api(`/v1/documents/${body.id}/finalize`, { token: alice.token, method: 'POST' })
  ).json();
  assert.equal(replay.revision, 2);
});
test('document references cannot point to another account', async () => {
  const a = await db
    .prepare("SELECT id FROM tt_records WHERE owner_id=? AND kind='DOCUMENT' LIMIT 1")
    .bind(alice.user.id)
    .first();
  const body = mutation('PURCHASE', purchase({ document_ids: [a.id] }));
  assert.equal(
    (await api('/v1/sync/mutate', { token: bob.token, method: 'POST', body })).status,
    409
  );
});
test('tombstones are carried in the incremental feed', async () => {
  const body = mutation('PURCHASE_TOMBSTONE', {}, { id: saved.id, expected_revision: 1 });
  assert.equal(
    (await api('/v1/sync/mutate', { token: alice.token, method: 'POST', body })).status,
    200
  );
  const feed = await (await api('/v1/sync/changes', { token: alice.token })).json();
  assert.ok(feed.changes.find((row) => row.id === saved.id && row.deleted_at));
});
test('logout revokes only the presented session', async () => {
  assert.equal((await api('/v1/auth/logout', { token: bob.token, method: 'POST' })).status, 200);
  assert.equal((await api('/v1/me', { token: bob.token })).status, 401);
  assert.equal((await api('/v1/me', { token: alice.token })).status, 200);
});
test('Google remains explicitly unavailable until its audience is configured', async () => {
  assert.equal((await api('/v1/auth/google/nonce', { method: 'POST' })).status, 503);
});

function reservedDocument() {
  return mutation('DOCUMENT', {
    display_name: 'Quota fixture',
    extracted_text: '',
    variants: ['ORIGINAL', 'THUMBNAIL', 'OPTIMIZED'].map((kind) => ({
      id: randomUUID(),
      kind,
      mime_type: 'image/png',
      byte_size: 26214400,
      sha256: 'a'.repeat(64),
    })),
  });
}
test('per-user reservation quota aborts metadata, variants and change feed together', async () => {
  const user = await login('quota@example.test');
  assert.equal(
    (await api('/v1/sync/mutate', { token: user.token, method: 'POST', body: reservedDocument() }))
      .status,
    200
  );
  const rejected = reservedDocument();
  const response = await api('/v1/sync/mutate', {
    token: user.token,
    method: 'POST',
    body: rejected,
  });
  assert.equal(response.status, 413);
  assert.equal((await response.json()).error, 'quota_exceeded');
  assert.equal(
    await db.prepare('SELECT id FROM tt_records WHERE id=?').bind(rejected.id).first(),
    null
  );
  assert.equal(
    (
      await db
        .prepare('SELECT count(*) AS count FROM tt_variants WHERE owner_id=?')
        .bind(user.user.id)
        .first()
    ).count,
    3
  );
  assert.equal(
    (
      await db
        .prepare('SELECT count(*) AS count FROM tt_changes WHERE owner_id=?')
        .bind(user.user.id)
        .first()
    ).count,
    1
  );
});
test('the project storage cap also applies across separate accounts', async () => {
  const user = await login('global-quota@example.test');
  const response = await api('/v1/sync/mutate', {
    token: user.token,
    method: 'POST',
    body: reservedDocument(),
  });
  assert.equal(response.status, 413);
  assert.equal((await response.json()).error, 'quota_exceeded');
  assert.equal(
    (
      await db
        .prepare('SELECT count(*) AS count FROM tt_records WHERE owner_id=?')
        .bind(user.user.id)
        .first()
    ).count,
    0
  );
});
