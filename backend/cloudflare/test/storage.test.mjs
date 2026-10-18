import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID, createHash } from 'node:crypto';
import { Miniflare } from 'miniflare';
import { applySchema } from './helpers.mjs';
import { optimizeDocument, storageSummary } from '../src/storage.js';
import { documentObject } from '../src/records.js';

let mf, db, bucket, env;
const owner = { id: randomUUID() };
before(async () => {
  mf = new Miniflare({
    modules: true,
    script: 'export default {fetch(){return new Response("test")}}',
    compatibilityDate: '2026-01-11',
    d1Databases: ['DB'],
    r2Buckets: ['DOCUMENTS'],
  });
  db = await mf.getD1Database('DB');
  bucket = await mf.getR2Bucket('DOCUMENTS');
  await applySchema(db);
  await db
    .prepare('INSERT INTO tt_profiles(id,email,created_at) VALUES(?,?,?)')
    .bind(owner.id, 'storage@example.test', Date.now())
    .run();
  env = { DB: db, DOCUMENTS: bucket };
});
after(async () => mf?.dispose());

async function legacy() {
  const id = randomUUID();
  const variants = [];
  for (const kind of ['ORIGINAL', 'OPTIMIZED', 'THUMBNAIL']) {
    const bytes = new TextEncoder().encode(`fixture-${kind}-${id}`);
    const row = {
      id: randomUUID(),
      kind,
      mime_type: 'image/png',
      byte_size: bytes.length,
      sha256: createHash('sha256').update(bytes).digest('hex'),
      storage_key: `${owner.id}/${randomUUID()}`,
    };
    await bucket.put(row.storage_key, bytes);
    variants.push(row);
  }
  const body = {
    schema_version: 1,
    id,
    owner_id: owner.id,
    state: 'SYNCED_VERIFIED',
    revision: 2,
    variants,
  };
  await db
    .prepare(
      "INSERT INTO tt_records(id,owner_id,kind,revision,last_operation_id,body,updated_at) VALUES(?,?,'DOCUMENT',2,?,?,?)"
    )
    .bind(id, owner.id, randomUUID(), JSON.stringify(body), Date.now())
    .run();
  for (const row of variants)
    await db
      .prepare(
        'INSERT INTO tt_variants(id,owner_id,document_id,kind,storage_key,mime_type,byte_size,sha256,verified_at) VALUES(?,?,?,?,?,?,?,?,?)'
      )
      .bind(
        row.id,
        owner.id,
        id,
        row.kind,
        row.storage_key,
        row.mime_type,
        row.byte_size,
        row.sha256,
        Date.now()
      )
      .run();
  return { id, variants };
}

test('cleanup preserves the original bytes, publishes canonical metadata and is idempotent', async () => {
  const { id, variants } = await legacy();
  const bytes = await (await bucket.get(variants[0].storage_key)).arrayBuffer();
  await optimizeDocument(env, owner, id);
  assert.deepEqual(await (await bucket.get(variants[0].storage_key)).arrayBuffer(), bytes);
  for (const derivative of variants.slice(1))
    assert.equal(await bucket.get(derivative.storage_key), null);
  const record = await db
    .prepare('SELECT revision,body FROM tt_records WHERE id=?')
    .bind(id)
    .first();
  assert.equal(record.revision, 3);
  assert.equal(JSON.parse(record.body).variants.length, 1);
  assert.equal(
    (
      await db
        .prepare('SELECT body FROM tt_changes WHERE entity_id=? ORDER BY cursor DESC LIMIT 1')
        .bind(id)
        .first()
    ).body,
    record.body
  );
  await optimizeDocument(env, owner, id);
  assert.equal(
    (await db.prepare('SELECT revision FROM tt_records WHERE id=?').bind(id).first()).revision,
    3
  );
});

test('missing or corrupt original blocks every derivative deletion', async () => {
  const { id, variants } = await legacy();
  await bucket.put(variants[0].storage_key, new Uint8Array(variants[0].byte_size));
  await assert.rejects(optimizeDocument(env, owner, id), { code: 'integrity_mismatch' });
  assert.ok(await bucket.get(variants[1].storage_key));
  assert.equal(
    (await db.prepare('SELECT revision FROM tt_records WHERE id=?').bind(id).first()).revision,
    2
  );
  await bucket.delete(variants[0].storage_key);
  await assert.rejects(optimizeDocument(env, owner, id), { code: 'original_unavailable' });
  assert.ok(await bucket.get(variants[2].storage_key));
});

test('interrupted cleanup keeps a retry list and never leaves feed references to removed copies', async () => {
  const { id, variants } = await legacy();
  let deleted = 0;
  const failing = {
    DB: db,
    DOCUMENTS: {
      get: (...args) => bucket.get(...args),
      delete: async (key) => {
        if (++deleted === 2) throw Error('simulated R2 failure');
        await bucket.delete(key);
      },
    },
  };
  await assert.rejects(optimizeDocument(failing, owner, id));
  assert.equal(
    (await db.prepare('SELECT count(*) AS n FROM tt_variants WHERE document_id=?').bind(id).first())
      .n,
    2
  );
  assert.equal(
    JSON.parse((await db.prepare('SELECT body FROM tt_records WHERE id=?').bind(id).first()).body)
      .variants.length,
    1
  );
  await optimizeDocument(env, owner, id);
  assert.equal(
    (await db.prepare('SELECT count(*) AS n FROM tt_variants WHERE document_id=?').bind(id).first())
      .n,
    1
  );
  assert.ok(await bucket.get(variants[0].storage_key));
});

test('storage inventory and cleanup are isolated by owner', async () => {
  const { id } = await legacy();
  const outsider = { id: randomUUID() };
  await assert.rejects(optimizeDocument(env, outsider, id), { code: 'not_found' });
  const usage = await (await storageSummary(env, outsider)).json();
  assert.equal(usage.original_bytes, 0);
  assert.deepEqual(usage.optimizable_ids, []);
});

test('a legacy upload racing cleanup cannot recreate an orphan derivative', async () => {
  const { id, variants } = await legacy();
  const derivative = variants[1];
  const bytes = await (await bucket.get(derivative.storage_key)).arrayBuffer();
  const racing = {
    DB: db,
    DOCUMENTS: {
      put: async (...args) => {
        await optimizeDocument(env, owner, id);
        return bucket.put(...args);
      },
      delete: (...args) => bucket.delete(...args),
    },
  };
  const request = new Request('https://fixture.test/object', {
    method: 'PUT',
    headers: { 'content-type': derivative.mime_type },
    body: bytes,
  });
  const response = await documentObject(request, racing, owner, id, derivative.id);
  assert.equal((await response.json()).skipped, true);
  assert.equal(await bucket.get(derivative.storage_key), null);
  assert.ok(await bucket.get(variants[0].storage_key));
});
