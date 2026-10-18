import { requireValue, json, sha256, uuid } from './http.js';

// Only metadata registered to this owner. Reserved bytes also count against quotas.
export async function storageSummary(env, owner) {
  const totals = await env.DB.prepare(
    `SELECT
    coalesce(sum(CASE WHEN kind='ORIGINAL' THEN byte_size ELSE 0 END),0) AS original_bytes,
    coalesce(sum(CASE WHEN kind='THUMBNAIL' THEN byte_size ELSE 0 END),0) AS derivative_bytes,
    coalesce(sum(CASE WHEN kind='OPTIMIZED' THEN byte_size ELSE 0 END),0) AS optimized_bytes,
    count(DISTINCT document_id) AS documents
    FROM tt_variants WHERE owner_id=?`
  )
    .bind(owner.id)
    .first();
  const { results } = await env.DB.prepare(
    `SELECT DISTINCT v.document_id AS id
    FROM tt_variants v JOIN tt_records r ON r.id=v.document_id AND r.owner_id=v.owner_id
    WHERE v.owner_id=? AND (v.kind='THUMBNAIL' OR (v.kind='ORIGINAL' AND v.mime_type LIKE 'image/%'))
    AND json_extract(r.body,'$.state')='SYNCED_VERIFIED' ORDER BY v.document_id LIMIT 20`
  )
    .bind(owner.id)
    .all();
  return json({ ...totals, optimizable_ids: results.map((row) => row.id) });
}

export async function optimizeDocument(env, owner, id) {
  requireValue(uuid(id));
  const loadRecord = () =>
    env.DB.prepare(
      "SELECT revision,body FROM tt_records WHERE id=? AND owner_id=? AND kind='DOCUMENT'"
    )
      .bind(id, owner.id)
      .first();
  let record = await loadRecord();
  requireValue(record, 'not_found', 404);
  requireValue(JSON.parse(record.body).state === 'SYNCED_VERIFIED', 'upload_incomplete', 409);
  const body = JSON.parse(record.body);
  if (body.storage_policy === 'OPTIMIZED_IMAGE') {
    const active = body.variants.find((v) => v.kind === 'OPTIMIZED');
    requireValue(active, 'integrity_mismatch', 422);
    await cleanupInactive(env, owner, id, active);
    return json({ optimized: true, cloud_copy_verified: true });
  }
  const { results: variants } = await env.DB.prepare(
    'SELECT * FROM tt_variants WHERE document_id=? AND owner_id=?'
  )
    .bind(id, owner.id)
    .all();
  const original = variants.find((row) => row.kind === 'ORIGINAL');
  requireValue(original?.verified_at, 'original_unverified', 409);
  const object = await env.DOCUMENTS.get(original.storage_key);
  requireValue(object && object.size === original.byte_size, 'original_unavailable', 503);
  requireValue(
    (await sha256(new Uint8Array(await object.arrayBuffer()))) === original.sha256,
    'integrity_mismatch',
    422
  );
  const canonical = body.variants.find((row) => row.kind === 'ORIGINAL');
  requireValue(
    canonical?.id === original.id && canonical.sha256 === original.sha256,
    'integrity_mismatch',
    422
  );
  if (body.storage_policy !== 'ORIGINAL_ONLY' || body.variants.length !== 1) {
    body.variants = [canonical];
    body.storage_policy = 'ORIGINAL_ONLY';
    body.revision = record.revision + 1;
    const result = await env.DB.prepare(
      `UPDATE tt_records SET body=?,revision=revision+1,updated_at=?
      WHERE id=? AND owner_id=? AND revision=?`
    )
      .bind(JSON.stringify(body), Date.now(), id, owner.id, record.revision)
      .run();
    if (!result.meta.changes) {
      record = await loadRecord();
      const current = JSON.parse(record.body);
      requireValue(
        current.storage_policy === 'ORIGINAL_ONLY' &&
          current.variants.length === 1 &&
          current.variants[0].id === original.id,
        'revision_conflict',
        409
      );
    }
  }
  // Publish canonical metadata first (normal change-feed trigger). Keep each derivative
  // row until its R2 deletion succeeds, so interruption leaves a durable retry list.
  for (const derivative of variants.filter((row) => row.kind !== 'ORIGINAL')) {
    requireValue(derivative.storage_key !== original.storage_key, 'integrity_mismatch', 422);
    await env.DOCUMENTS.delete(derivative.storage_key);
    await env.DB.prepare("DELETE FROM tt_variants WHERE id=? AND owner_id=? AND kind<>'ORIGINAL'")
      .bind(derivative.id, owner.id)
      .run();
  }
  return json({ optimized: true, original_preserved: true });
}

export async function cleanupInactive(env, owner, id, active) {
  const { results } = await env.DB.prepare(
    'SELECT * FROM tt_variants WHERE document_id=? AND owner_id=?'
  )
    .bind(id, owner.id)
    .all();
  const canonical = results.find((v) => v.id === active.id);
  requireValue(canonical?.verified_at, 'upload_incomplete', 409);
  const object = await env.DOCUMENTS.get(canonical.storage_key);
  requireValue(
    object &&
      object.size === active.byte_size &&
      (await sha256(new Uint8Array(await object.arrayBuffer()))) === active.sha256,
    'integrity_mismatch',
    422
  );
  for (const row of results.filter((v) => v.id !== active.id)) {
    requireValue(row.storage_key !== canonical.storage_key, 'integrity_mismatch', 422);
    await env.DOCUMENTS.delete(row.storage_key);
    await env.DB.prepare('DELETE FROM tt_variants WHERE id=? AND document_id=? AND owner_id=?')
      .bind(row.id, id, owner.id)
      .run();
  }
}
