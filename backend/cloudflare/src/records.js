import { HttpError, requireValue, readJson, readBytes, json, sha256, uuid } from './http.js';
import { cleanupInactive } from './storage.js';

const mimeTypes = new Set([
  'application/pdf',
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/heic',
  'image/heif',
  'image/avif',
]);
const date = (value) =>
  typeof value === 'string' &&
  /^\d{4}-\d{2}-\d{2}$/.test(value) &&
  !Number.isNaN(Date.parse(`${value}T00:00:00Z`)) &&
  new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
const text = (value, max, optional = false) =>
  typeof value === 'string' && value.length <= max && (optional || value.trim().length > 0);
const cents = (value) => Number.isSafeInteger(value) && value >= 0 && value <= 100000000000000;

function purchasePayload(input, ownerId, id, deleted, revision) {
  if (deleted)
    return { schema_version: 1, id, owner_id: ownerId, deleted_at: Date.now(), revision };
  requireValue(
    text(input.merchant, 200) &&
      date(input.purchased_on) &&
      cents(input.total_minor) &&
      ['EUR', 'USD', 'GBP'].includes(input.currency)
  );
  requireValue(
    input.delivered_on == null ||
      (date(input.delivered_on) && input.delivered_on >= input.purchased_on)
  );
  requireValue(
    ['IN_STORE', 'ONLINE', 'OTHER'].includes(input.channel) &&
      text(input.order_number || '', 200, true) &&
      text(input.notes || '', 10000, true)
  );
  requireValue(Array.isArray(input.lines) && input.lines.length > 0 && input.lines.length <= 100);
  const ids = new Set();
  const productIds = new Set();
  const lines = input.lines.map((line) => {
    requireValue(
      uuid(line.id) &&
        !ids.has(line.id) &&
        Number.isInteger(line.quantity) &&
        line.quantity >= 1 &&
        line.quantity <= 999
    );
    requireValue(line.unit_price_minor == null || cents(line.unit_price_minor));
    ids.add(line.id);
    const product = line.product;
    requireValue(
      product && uuid(product.id) && !productIds.has(product.id) && text(product.name, 200)
    );
    requireValue(
      (product.warranty_end == null || date(product.warranty_end)) &&
        (product.return_end == null || date(product.return_end))
    );
    productIds.add(product.id);
    return {
      id: line.id,
      quantity: line.quantity,
      unit_price_minor: line.unit_price_minor ?? null,
      product: {
        id: product.id,
        name: product.name.trim(),
        warranty_end: product.warranty_end ?? null,
        return_end: product.return_end ?? null,
        deadline_source: 'USER_CONFIRMED',
      },
    };
  });
  const documentIds = input.document_ids ?? [];
  requireValue(
    Array.isArray(documentIds) &&
      documentIds.length <= 20 &&
      documentIds.every(uuid) &&
      new Set(documentIds).size === documentIds.length
  );
  return {
    schema_version: 1,
    id,
    owner_id: ownerId,
    merchant: input.merchant.trim(),
    purchased_on: input.purchased_on,
    delivered_on: input.delivered_on ?? null,
    total_minor: input.total_minor,
    currency: input.currency,
    channel: input.channel,
    order_number: input.order_number || '',
    notes: input.notes || '',
    lines,
    document_ids: documentIds,
    revision,
    created_at: input.created_at || Date.now(),
    updated_at: Date.now(),
    deleted_at: null,
  };
}

function documentPayload(input, ownerId, id, revision) {
  requireValue(text(input.display_name, 255) && text(input.extracted_text || '', 100000, true));
  requireValue(
    Array.isArray(input.variants) && input.variants.length >= 1 && input.variants.length <= 3
  );
  const kinds = new Set();
  const ids = new Set();
  const variants = input.variants.map((variant) => {
    requireValue(
      uuid(variant.id) &&
        !ids.has(variant.id) &&
        ['ORIGINAL', 'OPTIMIZED', 'THUMBNAIL'].includes(variant.kind) &&
        !kinds.has(variant.kind)
    );
    requireValue(
      mimeTypes.has(variant.mime_type) &&
        Number.isSafeInteger(variant.byte_size) &&
        variant.byte_size > 0 &&
        variant.byte_size <= 26214400 &&
        /^[a-f0-9]{64}$/.test(variant.sha256)
    );
    kinds.add(variant.kind);
    ids.add(variant.id);
    return {
      id: variant.id,
      kind: variant.kind,
      mime_type: variant.mime_type,
      byte_size: variant.byte_size,
      sha256: variant.sha256,
    };
  });
  const original = variants.find((variant) => variant.kind === 'ORIGINAL');
  const optimized =
    original?.mime_type === 'application/pdf'
      ? null
      : variants.find((variant) => variant.kind === 'OPTIMIZED');
  requireValue(
    optimized || original?.mime_type === 'application/pdf',
    'image_optimization_required',
    422
  );
  const sourceHash = input.source_original_sha256 || original?.sha256;
  const sourceBytes = input.source_original_byte_size || original?.byte_size;
  requireValue(
    /^[a-f0-9]{64}$/.test(sourceHash) &&
      Number.isSafeInteger(sourceBytes) &&
      sourceBytes > 0 &&
      sourceBytes <= 26214400
  );
  if (optimized)
    requireValue(
      optimized.mime_type.startsWith('image/') &&
        optimized.byte_size <= 2097152 &&
        optimized.byte_size <= sourceBytes,
      'image_optimization_required',
      422
    );
  return {
    schema_version: 1,
    id,
    owner_id: ownerId,
    purchase_id: null,
    display_name: input.display_name,
    extracted_text: input.extracted_text || '',
    created_at: Date.now(),
    retention_policy: optimized ? 'KEEP_LOCAL_ORIGINAL' : 'KEEP_ORIGINAL',
    storage_policy: optimized ? 'OPTIMIZED_IMAGE' : 'ORIGINAL_ONLY',
    source_original_sha256: sourceHash,
    source_original_byte_size: sourceBytes,
    optimization_profile: optimized ? 'receipt-webp-v1' : null,
    state: 'REMOTE_OBJECT_PENDING_COMMIT',
    variants: [optimized || original],
    revision,
  };
}

async function previousMutation(env, owner, operationId, hash) {
  const previous = await env.DB.prepare(
    'SELECT entity_id,revision,request_hash FROM tt_mutations WHERE owner_id=? AND operation_id=?'
  )
    .bind(owner.id, operationId)
    .first();
  if (!previous) return null;
  requireValue(previous.request_hash === hash, 'idempotency_key_reused', 409);
  return json({ id: previous.entity_id, revision: previous.revision, accepted: true });
}

export async function mutate(request, env, owner) {
  const input = await readJson(request);
  requireValue(
    uuid(input.operation_id) &&
      uuid(input.id) &&
      Number.isSafeInteger(input.expected_revision) &&
      input.expected_revision >= 0 &&
      input.expected_revision < 1000000000
  );
  requireValue(
    ['PURCHASE', 'PURCHASE_TOMBSTONE', 'DOCUMENT'].includes(input.type) &&
      input.payload &&
      typeof input.payload === 'object'
  );
  const requestHash = await sha256(new TextEncoder().encode(JSON.stringify(input)));
  const previous = await previousMutation(env, owner, input.operation_id, requestHash);
  if (previous) return previous;
  const deleted = input.type === 'PURCHASE_TOMBSTONE';
  const kind = input.type === 'DOCUMENT' ? 'DOCUMENT' : 'PURCHASE';
  const revision = input.expected_revision + 1;
  let body =
    kind === 'PURCHASE'
      ? purchasePayload(input.payload, owner.id, input.id, deleted, revision)
      : documentPayload(input.payload, owner.id, input.id, revision);
  let reservations = kind === 'DOCUMENT' ? body.variants : [];
  if (kind === 'DOCUMENT' && input.expected_revision > 0) {
    requireValue(body.storage_policy === 'OPTIMIZED_IMAGE', 'document_immutable', 409);
    const old = await env.DB.prepare(
      "SELECT body FROM tt_records WHERE id=? AND owner_id=? AND kind='DOCUMENT'"
    )
      .bind(input.id, owner.id)
      .first();
    requireValue(old, 'not_found', 404);
    const previousBody = JSON.parse(old.body);
    requireValue(previousBody.storage_policy !== 'OPTIMIZED_IMAGE', 'document_immutable', 409);
    const source = previousBody.variants.find((v) => v.kind === 'ORIGINAL');
    requireValue(
      source?.sha256 === body.source_original_sha256 &&
        source.byte_size === body.source_original_byte_size,
      'integrity_mismatch',
      422
    );
    const existing = await env.DB.prepare(
      "SELECT * FROM tt_variants WHERE document_id=? AND owner_id=? AND kind='OPTIMIZED'"
    )
      .bind(input.id, owner.id)
      .first();
    if (existing) {
      requireValue(
        existing.id === body.variants[0].id &&
          existing.sha256 === body.variants[0].sha256 &&
          existing.byte_size === body.variants[0].byte_size,
        'cleanup_required',
        409
      );
      reservations = [];
    }
    body = {
      ...previousBody,
      ...body,
      created_at: previousBody.created_at,
      display_name: previousBody.display_name,
      extracted_text: previousBody.extracted_text,
      variants: [source, body.variants[0]],
      storage_policy: 'IMAGE_OPTIMIZATION_PENDING',
      state: previousBody.state,
      revision,
    };
  }
  const encoded = JSON.stringify(body);
  const now = Date.now();
  const maxRecords = Number(env.MAX_USER_RECORDS || 2000);
  const statements = [
    env.DB.prepare(
      `INSERT INTO tt_records(id,owner_id,kind,revision,last_operation_id,body,updated_at,deleted_at)
    SELECT ?,?,?,?,?,?,?,? WHERE (?=0 AND (SELECT count(*) FROM tt_records WHERE owner_id=?)<?) OR EXISTS(SELECT 1 FROM tt_records WHERE id=? AND owner_id=?)
    ON CONFLICT(id) DO UPDATE SET revision=excluded.revision,last_operation_id=excluded.last_operation_id,body=excluded.body,updated_at=excluded.updated_at,deleted_at=excluded.deleted_at
    WHERE tt_records.owner_id=? AND tt_records.kind=? AND tt_records.revision=? AND tt_records.deleted_at IS NULL`
    ).bind(
      input.id,
      owner.id,
      kind,
      revision,
      input.operation_id,
      encoded,
      now,
      deleted ? now : null,
      input.expected_revision,
      owner.id,
      maxRecords,
      input.id,
      owner.id,
      owner.id,
      kind,
      input.expected_revision
    ),
    env.DB.prepare(
      'INSERT INTO tt_write_guard(ok) SELECT CASE WHEN EXISTS(SELECT 1 FROM tt_records WHERE id=? AND owner_id=? AND last_operation_id=?) THEN 1 ELSE 0 END'
    ).bind(input.id, owner.id, input.operation_id),
  ];
  if (kind === 'PURCHASE' && !deleted && body.document_ids.length) {
    statements.push(
      env.DB.prepare(
        `INSERT INTO tt_write_guard(ok) SELECT CASE WHEN (SELECT count(*) FROM tt_records
      WHERE owner_id=? AND kind='DOCUMENT' AND id IN (SELECT value FROM json_each(?)))=? THEN 1 ELSE 0 END`
      ).bind(owner.id, JSON.stringify(body.document_ids), body.document_ids.length)
    );
  }
  if (kind === 'DOCUMENT') {
    const bytes = reservations.reduce((sum, variant) => sum + variant.byte_size, 0);
    statements.push(
      env.DB.prepare(
        'INSERT INTO tt_write_guard(ok) SELECT CASE WHEN coalesce((SELECT sum(byte_size) FROM tt_variants WHERE owner_id=?),0)+?<=? THEN 1 ELSE 0 END'
      ).bind(owner.id, bytes, Number(env.MAX_USER_BYTES || 104857600))
    );
    statements.push(
      env.DB.prepare(
        'INSERT INTO tt_write_guard(ok) SELECT CASE WHEN coalesce((SELECT sum(byte_size) FROM tt_variants),0)+?<=? THEN 1 ELSE 0 END'
      ).bind(bytes, Number(env.MAX_TOTAL_BYTES || 1073741824))
    );
    for (const variant of reservations) {
      statements.push(
        env.DB.prepare(
          'INSERT INTO tt_variants(id,owner_id,document_id,kind,storage_key,mime_type,byte_size,sha256) VALUES(?,?,?,?,?,?,?,?)'
        ).bind(
          variant.id,
          owner.id,
          input.id,
          variant.kind,
          `${owner.id}/${crypto.randomUUID()}`,
          variant.mime_type,
          variant.byte_size,
          variant.sha256
        )
      );
    }
  }
  statements.push(
    env.DB.prepare(
      'INSERT INTO tt_mutations(owner_id,operation_id,entity_id,revision,request_hash,created_at) VALUES(?,?,?,?,?,?)'
    ).bind(owner.id, input.operation_id, input.id, revision, requestHash, now)
  );
  statements.push(env.DB.prepare('DELETE FROM tt_write_guard'));
  try {
    await env.DB.batch(statements);
  } catch (_) {
    const replay = await previousMutation(env, owner, input.operation_id, requestHash);
    if (replay) return replay;
    const current = await env.DB.prepare(
      'SELECT revision FROM tt_records WHERE id=? AND owner_id=?'
    )
      .bind(input.id, owner.id)
      .first();
    if (!current || (kind === 'DOCUMENT' && current.revision === input.expected_revision)) {
      const counts = await env.DB.prepare(
        'SELECT (SELECT count(*) FROM tt_records WHERE owner_id=?) AS records,(SELECT coalesce(sum(byte_size),0) FROM tt_variants WHERE owner_id=?) AS user_bytes,(SELECT coalesce(sum(byte_size),0) FROM tt_variants) AS total_bytes'
      )
        .bind(owner.id, owner.id)
        .first();
      const bytes =
        kind === 'DOCUMENT' ? reservations.reduce((sum, variant) => sum + variant.byte_size, 0) : 0;
      if (
        (!current && counts.records >= maxRecords) ||
        (kind === 'DOCUMENT' &&
          (counts.user_bytes + bytes > Number(env.MAX_USER_BYTES || 104857600) ||
            counts.total_bytes + bytes > Number(env.MAX_TOTAL_BYTES || 1073741824)))
      )
        throw new HttpError(413, 'quota_exceeded');
    }
    throw new HttpError(409, current ? 'revision_conflict' : 'record_conflict');
  }
  return json({ id: input.id, revision, accepted: true });
}

export async function changes(request, env, owner) {
  const url = new URL(request.url);
  const cursor = Number(url.searchParams.get('cursor') || 0);
  requireValue(Number.isSafeInteger(cursor) && cursor >= 0, 'invalid_cursor');
  const { results } = await env.DB.prepare(
    'SELECT cursor,entity_id AS id,kind,revision,body,deleted_at FROM tt_changes WHERE owner_id=? AND cursor>? ORDER BY cursor LIMIT 25'
  )
    .bind(owner.id, cursor)
    .all();
  return json({
    changes: results.map((row) => ({ ...row, body: JSON.parse(row.body) })),
    cursor: results.at(-1)?.cursor ?? cursor,
    has_more: results.length === 25,
  });
}

export async function documentObject(request, env, owner, documentId, variantId) {
  requireValue(uuid(documentId) && uuid(variantId), 'not_found', 404);
  const variant = await env.DB.prepare(
    'SELECT * FROM tt_variants WHERE id=? AND document_id=? AND owner_id=?'
  )
    .bind(variantId, documentId, owner.id)
    .first();
  if (!variant && request.method === 'PUT') {
    // Older Android versions still send their local derivatives. Accept the no-op only
    // for an owned canonical document; never create an object or disclose another owner.
    const record = await env.DB.prepare(
      "SELECT body FROM tt_records WHERE id=? AND owner_id=? AND kind='DOCUMENT'"
    )
      .bind(documentId, owner.id)
      .first();
    if (
      record &&
      ['ORIGINAL_ONLY', 'OPTIMIZED_IMAGE'].includes(JSON.parse(record.body).storage_policy)
    )
      return json({ skipped: true, reason: 'local_derivative' });
  }
  requireValue(variant, 'not_found', 404);
  if (request.method === 'PUT') {
    const before = await env.DB.prepare('SELECT body FROM tt_records WHERE id=? AND owner_id=?')
      .bind(documentId, owner.id)
      .first();
    const beforeBody = before && JSON.parse(before.body);
    if (
      beforeBody &&
      ['ORIGINAL_ONLY', 'OPTIMIZED_IMAGE'].includes(beforeBody.storage_policy) &&
      !beforeBody.variants.some((v) => v.id === variantId)
    )
      return json({ skipped: true, reason: 'local_derivative' });
    const bytes = await readBytes(request, Number(env.MAX_DOCUMENT_BYTES || 26214400));
    requireValue(
      bytes.length === variant.byte_size && (await sha256(bytes)) === variant.sha256,
      'integrity_mismatch',
      422
    );
    requireValue(
      request.headers.get('content-type')?.split(';')[0] === variant.mime_type,
      'mime_mismatch',
      415
    );
    await env.DOCUMENTS.put(variant.storage_key, bytes, {
      sha256: variant.sha256,
      httpMetadata: { contentType: variant.mime_type, cacheControl: 'private, no-store' },
      customMetadata: { sha256: variant.sha256 },
    });
    {
      const record = await env.DB.prepare('SELECT body FROM tt_records WHERE id=? AND owner_id=?')
        .bind(documentId, owner.id)
        .first();
      const currentBody = record && JSON.parse(record.body);
      if (
        currentBody &&
        ['ORIGINAL_ONLY', 'OPTIMIZED_IMAGE'].includes(currentBody.storage_policy) &&
        !currentBody.variants.some((v) => v.id === variantId)
      ) {
        // A legacy upload may have started just before optimization. Re-check after
        // its put so it cannot recreate an orphan derivative behind the cleanup.
        await env.DOCUMENTS.delete(variant.storage_key);
        await env.DB.prepare('DELETE FROM tt_variants WHERE id=? AND owner_id=?')
          .bind(variantId, owner.id)
          .run();
        return json({ skipped: true, reason: 'local_derivative' });
      }
    }
    await env.DB.prepare('UPDATE tt_variants SET verified_at=? WHERE id=? AND owner_id=?')
      .bind(Date.now(), variantId, owner.id)
      .run();
    return json({ uploaded: true, sha256: variant.sha256, byte_size: variant.byte_size });
  }
  if (request.method === 'GET') {
    requireValue(variant.verified_at, 'document_not_uploaded', 409);
    const object = await env.DOCUMENTS.get(variant.storage_key);
    requireValue(object && object.size === variant.byte_size, 'object_unavailable', 503);
    let bytes;
    if (object.customMetadata?.sha256) {
      requireValue(object.customMetadata.sha256 === variant.sha256, 'object_unavailable', 503);
      bytes = object.body;
    } else {
      // Administrative imports can lack custom metadata. Verify actual bytes instead.
      bytes = new Uint8Array(await object.arrayBuffer());
      requireValue((await sha256(bytes)) === variant.sha256, 'object_unavailable', 503);
    }
    return new Response(bytes, {
      headers: {
        'Content-Type': variant.mime_type,
        'Content-Length': String(variant.byte_size),
        'Cache-Control': 'private, no-store',
        'X-Content-Type-Options': 'nosniff',
        'Content-Disposition': 'attachment',
        'X-Checksum-Sha256': variant.sha256,
      },
    });
  }
  throw new HttpError(405, 'method_not_allowed');
}

export async function finalizeDocument(env, owner, id) {
  requireValue(uuid(id));
  const row = await env.DB.prepare(
    "SELECT revision,body FROM tt_records WHERE id=? AND owner_id=? AND kind='DOCUMENT'"
  )
    .bind(id, owner.id)
    .first();
  requireValue(row, 'not_found', 404);
  const body = JSON.parse(row.body);
  const optimized = body.variants.find((v) => v.kind === 'OPTIMIZED');
  if (
    optimized &&
    ['OPTIMIZED_IMAGE', 'IMAGE_OPTIMIZATION_PENDING'].includes(body.storage_policy)
  ) {
    const variant = await env.DB.prepare(
      'SELECT * FROM tt_variants WHERE id=? AND document_id=? AND owner_id=?'
    )
      .bind(optimized.id, id, owner.id)
      .first();
    requireValue(variant?.verified_at, 'upload_incomplete', 409);
    const object = await env.DOCUMENTS.get(variant.storage_key);
    requireValue(
      object &&
        object.size === optimized.byte_size &&
        (await sha256(new Uint8Array(await object.arrayBuffer()))) === optimized.sha256,
      'integrity_mismatch',
      422
    );
    if (
      body.storage_policy !== 'OPTIMIZED_IMAGE' ||
      body.state !== 'SYNCED_VERIFIED' ||
      body.variants.length !== 1
    ) {
      const next = {
        ...body,
        variants: [optimized],
        state: 'SYNCED_VERIFIED',
        storage_policy: 'OPTIMIZED_IMAGE',
        retention_policy: 'KEEP_LOCAL_ORIGINAL',
        revision: row.revision + 1,
      };
      const update = await env.DB.prepare(
        'UPDATE tt_records SET body=?,revision=revision+1,updated_at=? WHERE id=? AND owner_id=? AND revision=?'
      )
        .bind(JSON.stringify(next), Date.now(), id, owner.id, row.revision)
        .run();
      requireValue(update.meta.changes, 'revision_conflict', 409);
    }
    await cleanupInactive(env, owner, id, optimized);
    const final = await env.DB.prepare('SELECT revision FROM tt_records WHERE id=? AND owner_id=?')
      .bind(id, owner.id)
      .first();
    return json({ state: 'SYNCED_VERIFIED', revision: final.revision });
  }
  const variants = await env.DB.prepare(
    'SELECT id,verified_at FROM tt_variants WHERE document_id=? AND owner_id=?'
  )
    .bind(id, owner.id)
    .all();
  requireValue(
    variants.results.length > 0 && variants.results.every((row) => row.verified_at),
    'upload_incomplete',
    409
  );
  await env.DB.prepare(
    `UPDATE tt_records SET revision=revision+1,body=json_set(body,'$.state','SYNCED_VERIFIED','$.revision',revision+1),updated_at=?
    WHERE id=? AND owner_id=? AND kind='DOCUMENT' AND json_extract(body,'$.state')<>'SYNCED_VERIFIED'`
  )
    .bind(Date.now(), id, owner.id)
    .run();
  const record = await env.DB.prepare('SELECT revision FROM tt_records WHERE id=? AND owner_id=?')
    .bind(id, owner.id)
    .first();
  return json({ state: 'SYNCED_VERIFIED', revision: record.revision });
}
