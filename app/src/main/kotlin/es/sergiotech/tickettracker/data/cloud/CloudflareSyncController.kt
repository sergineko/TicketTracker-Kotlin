package es.sergiotech.tickettracker.data.cloud

import androidx.room.withTransaction
import es.sergiotech.tickettracker.core.database.*
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.domain.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class CloudflareSyncController(
    private val db: TicketDatabase,
    private val api: RemoteApi,
    private val sessions: CloudSessionStore,
    private val store: PrivateDocumentStore,
) : SyncController {
    private val lock = Mutex()
    private val dao = db.tickets()
    private val _status = MutableStateFlow(CloudSyncState())
    override val status = _status.asStateFlow()

    override fun conflicts() =
        sessions.identity.flatMapLatest { user ->
            dao.conflicts(user.id).map { list ->
                list.map {
                    SyncConflict(
                        it.entityId,
                        it.kind,
                        JSONObject(it.remotePayload).isNull("deleted_at"),
                    )
                }
            }
        }

    override suspend fun synchronize() =
        lock.withLock {
            val account = sessions.current() ?: return@withLock
            if (!api.configured) return@withLock
            _status.value =
                if (_status.value.ownerId == account.userId)
                    _status.value.copy(running = true, error = null)
                else CloudSyncState(running = true, ownerId = account.userId)
            try {
                val operations =
                    dao.outbox(account.userId).sortedBy {
                        if (it.entityType == "DOCUMENT") 0 else 1
                    }
                for (operation in operations) {
                    currentCoroutineContext().ensureActive()
                    check(sessions.current()?.userId == account.userId)
                    if (dao.conflict(operation.entityId, account.userId) != null) continue
                    try {
                        val response =
                            api.request(
                                "/v1/sync/mutate",
                                account.token,
                                "POST",
                                JSONObject().apply {
                                    put("operation_id", operation.operationId)
                                    put("id", operation.entityId)
                                    put("expected_revision", operation.expectedRemoteRevision)
                                    put("type", operation.entityType)
                                    put("payload", JSONObject(operation.payload))
                                },
                            )
                        if (operation.entityType == "DOCUMENT") {
                            val record =
                                requireNotNull(
                                    dao.documentRecord(operation.entityId, account.userId)
                                )
                            // Persisted payloads are immutable idempotency requests. Honour legacy
                            // queued uploads too, without uploading new local preview caches.
                            val declared = JSONObject(operation.payload).getJSONArray("variants")
                            val uploadIds =
                                (0 until declared.length())
                                    .map { declared.getJSONObject(it).getString("id") }
                                    .toSet()
                            for (variant in record.variants.filter { it.id in uploadIds }) api
                                .upload(
                                    objectPath(record.document.id, variant.id),
                                    account.token,
                                    variant.model(),
                                    store,
                                )
                            val verified =
                                api.request(
                                    "/v1/documents/${operation.entityId}/finalize",
                                    account.token,
                                    "POST",
                                )
                            db.withTransaction {
                                val current =
                                    requireNotNull(dao.document(operation.entityId, account.userId))
                                dao.putDocument(
                                    current.copy(
                                        state = DocumentState.SYNCED_VERIFIED.name,
                                        remoteRevision = verified.getLong("revision"),
                                    )
                                )
                                dao.acknowledge(
                                    operation.entityId,
                                    account.userId,
                                    operation.operationId,
                                )
                            }
                        } else {
                            db.withTransaction {
                                val current =
                                    requireNotNull(dao.purchase(operation.entityId, account.userId))
                                val remoteRevision = response.getLong("revision")
                                dao.putPurchase(
                                    current.copy(
                                        remoteRevision =
                                            maxOf(current.remoteRevision, remoteRevision)
                                    )
                                )
                                dao.acknowledge(
                                    operation.entityId,
                                    account.userId,
                                    operation.operationId,
                                )
                                // An edit made during the request stays queued and follows the
                                // acknowledged version.
                                dao.rebasePending(
                                    operation.entityId,
                                    account.userId,
                                    remoteRevision,
                                )
                            }
                        }
                    } catch (failure: CloudApiException) {
                        if (failure.status != 409) throw failure
                        val remote = api.request("/v1/records/${operation.entityId}", account.token)
                        dao.putConflict(
                            SyncConflictEntity(
                                operation.entityId,
                                account.userId,
                                remote.getString("kind"),
                                remote.getLong("revision"),
                                remote.getJSONObject("body").toString(),
                            )
                        )
                    }
                }
                var more: Boolean
                do {
                    currentCoroutineContext().ensureActive()
                    val cursor = dao.cursor(account.userId) ?: 0
                    val response = api.request("/v1/sync/changes?cursor=$cursor", account.token)
                    check(sessions.current()?.userId == account.userId)
                    val changes = response.getJSONArray("changes")
                    db.withTransaction {
                        for (index in 0 until changes.length()) {
                            val change = changes.getJSONObject(index)
                            applyRemote(
                                account.userId,
                                change.getString("kind"),
                                change.getLong("revision"),
                                change.getJSONObject("body"),
                            )
                        }
                        val next = response.getLong("cursor")
                        require(next >= cursor)
                        dao.putCursor(SyncCursorEntity(account.userId, next))
                    }
                    more = response.getBoolean("has_more")
                } while (more)
                _status.value =
                    CloudSyncState(
                        lastSuccess = System.currentTimeMillis(),
                        ownerId = account.userId,
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _status.value =
                    _status.value.copy(
                        error = (failure as? CloudApiException)?.code ?: "sync_failed"
                    )
                throw failure
            } finally {
                _status.update { it.copy(running = false) }
            }
        }

    override suspend fun ensureDocument(document: ReceiptDocument) {
        val original = document.variants.first { it.kind == VariantKind.ORIGINAL }
        if (store.verify(original)) return
        val account = requireNotNull(sessions.current())
        require(
            document.ownerId == account.userId && document.state == DocumentState.SYNCED_VERIFIED
        )
        api.download(objectPath(document.id, original.id), account.token, original, store)
    }

    override suspend fun storageUsage(): CloudStorageUsage {
        val account = requireNotNull(sessions.current())
        return storageUsage(account)
    }

    private suspend fun storageUsage(account: CloudSession): CloudStorageUsage {
        val result = api.request("/v1/storage", account.token)
        check(sessions.current()?.userId == account.userId)
        val ids = result.getJSONArray("optimizable_ids")
        return CloudStorageUsage(
            result.getLong("original_bytes"),
            result.getLong("derivative_bytes"),
            result.getInt("documents"),
            (0 until ids.length()).map { ids.getString(it) },
        )
    }

    override suspend fun optimizeStorage() =
        lock.withLock {
            val account = requireNotNull(sessions.current())
            check(
                dao.outbox(account.userId).isEmpty() &&
                    dao.conflicts(account.userId).first().isEmpty()
            )
            for (id in storageUsage(account).optimizableIds) {
                currentCoroutineContext().ensureActive()
                check(sessions.current()?.userId == account.userId)
                api.request("/v1/documents/$id/optimize", account.token, "POST")
            }
        }

    override suspend fun resolveConflict(id: String, keepLocal: Boolean) =
        lock.withLock {
            val account = requireNotNull(sessions.current())
            db.withTransaction {
                val conflict = requireNotNull(dao.conflict(id, account.userId))
                require(conflict.kind == "PURCHASE")
                if (keepLocal) {
                    require(JSONObject(conflict.remotePayload).isNull("deleted_at"))
                    val pending = requireNotNull(dao.pending(id, account.userId))
                    dao.enqueue(
                        pending.copy(
                            operationId = newId(),
                            expectedRemoteRevision = conflict.remoteRevision,
                        )
                    )
                    dao.purchase(id, account.userId)?.let {
                        dao.putPurchase(it.copy(remoteRevision = conflict.remoteRevision))
                    }
                } else {
                    dao.discardOperation(id, account.userId)
                    applyPurchase(
                        account.userId,
                        conflict.remoteRevision,
                        JSONObject(conflict.remotePayload),
                    )
                }
                dao.clearConflict(id, account.userId)
            }
        }

    private suspend fun applyRemote(owner: String, kind: String, revision: Long, body: JSONObject) {
        require(body.getInt("schema_version") == 1 && body.getString("owner_id") == owner)
        val id = body.getString("id")
        val localRevision =
            if (kind == "PURCHASE") dao.purchase(id, owner)?.remoteRevision ?: 0
            else dao.document(id, owner)?.remoteRevision ?: 0
        if (revision <= localRevision) return
        if (dao.pending(id, owner) != null) {
            dao.putConflict(SyncConflictEntity(id, owner, kind, revision, body.toString()))
            return
        }
        when (kind) {
            "PURCHASE" -> applyPurchase(owner, revision, body)
            "DOCUMENT" -> applyDocument(owner, revision, body)
            else -> error("Unsupported entity type")
        }
    }

    private suspend fun applyPurchase(owner: String, remoteRevision: Long, body: JSONObject) {
        val id = body.getString("id")
        val old = dao.purchaseById(id)
        require(old == null || old.ownerId == owner)
        if (!body.isNull("deleted_at")) {
            old?.let {
                dao.putPurchase(
                    it.copy(
                        revision = it.revision + 1,
                        remoteRevision = remoteRevision,
                        deletedAt = body.getLong("deleted_at"),
                    )
                )
            }
            return
        }
        val now = System.currentTimeMillis()
        val purchase =
            PurchaseEntity(
                id,
                owner,
                body.getString("merchant"),
                body.getString("purchased_on"),
                body.nullableString("delivered_on"),
                body.getLong("total_minor"),
                body.getString("currency"),
                body.getString("channel"),
                body.optString("order_number"),
                body.optString("notes"),
                (old?.revision ?: 0) + 1,
                remoteRevision,
                body.optLong("created_at", now),
                body.optLong("updated_at", now),
            )
        LocalDate.parse(purchase.purchasedOn)
        Money(purchase.totalMinor, purchase.currency)
        PurchaseChannel.valueOf(purchase.channel)
        dao.putPurchase(purchase)
        dao.clearProducts(id)
        val lines = body.getJSONArray("lines")
        val products = mutableListOf<ProductEntity>()
        val entries = mutableListOf<LineEntity>()
        for (index in 0 until lines.length()) {
            val line = lines.getJSONObject(index)
            val product = line.getJSONObject("product")
            products +=
                ProductEntity(
                    product.getString("id"),
                    id,
                    product.getString("name"),
                    product.nullableString("warranty_end"),
                    product.nullableString("return_end"),
                    product.optString("deadline_source", "USER_CONFIRMED"),
                )
            entries +=
                LineEntity(
                    line.getString("id"),
                    id,
                    product.getString("id"),
                    index,
                    line.getInt("quantity"),
                    if (line.isNull("unit_price_minor")) null else line.getLong("unit_price_minor"),
                )
        }
        dao.putProducts(products)
        dao.putLines(entries)
        val documents = body.optJSONArray("document_ids")
        dao.detachDocuments(
            owner,
            id,
            documents?.let { list -> (0 until list.length()).map { list.getString(it) } }
                ?: emptyList(),
        )
        if (documents != null)
            for (index in 0 until documents.length()) {
                val document = dao.document(documents.getString(index), owner) ?: continue
                require(document.purchaseId == null || document.purchaseId == id)
                dao.putDocument(document.copy(purchaseId = id))
            }
    }

    private suspend fun applyDocument(owner: String, remoteRevision: Long, body: JSONObject) {
        val id = body.getString("id")
        val old = dao.documentRecord(id, owner)
        val variants = body.getJSONArray("variants")
        val entries =
            (0 until variants.length()).map { index ->
                val value = variants.getJSONObject(index)
                val variantId = value.getString("id")
                val kind = VariantKind.valueOf(value.getString("kind"))
                VariantEntity(
                    variantId,
                    id,
                    kind.name,
                    old?.variants?.find { it.id == variantId }?.relativePath
                        ?: "$id/${kind.name.lowercase()}",
                    value.getString("mime_type"),
                    value.getLong("byte_size"),
                    value.getString("sha256"),
                )
            }
        val original = entries.first { it.kind == VariantKind.ORIGINAL.name }
        val state = DocumentState.valueOf(body.getString("state"))
        dao.putDocument(
            DocumentEntity(
                id,
                owner,
                old?.document?.purchaseId,
                body.getString("display_name"),
                original.sha256,
                body.optString("extracted_text"),
                state.name,
                body.getLong("created_at"),
                remoteRevision,
            )
        )
        dao.putVariants(entries)
    }

    private fun objectPath(documentId: String, variantId: String) =
        "/v1/documents/$documentId/variants/$variantId"
}

private fun JSONObject.nullableString(key: String) = if (isNull(key)) null else getString(key)

private fun VariantEntity.model() =
    DocumentVariant(id, VariantKind.valueOf(kind), relativePath, mimeType, byteSize, sha256)
