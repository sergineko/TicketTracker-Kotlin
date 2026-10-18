package es.sergiotech.tickettracker.data

import androidx.room.withTransaction
import es.sergiotech.tickettracker.core.database.*
import es.sergiotech.tickettracker.domain.*
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class RoomPurchaseRepository(
    private val db: TicketDatabase,
    private val identity: IdentityGateway,
) : PurchaseRepository {
    private val dao = db.tickets()

    override fun observePurchases() =
        identity.identity.flatMapLatest { user ->
            dao.purchases(user.id).map { it.map(PurchaseRecord::model) }
        }

    override fun observeDocuments() =
        identity.identity.flatMapLatest { user ->
            dao.documents(user.id).map { it.map(DocumentRecord::model) }
        }

    override fun observePendingCount() = identity.identity.flatMapLatest { dao.pendingCount(it.id) }

    override suspend fun save(purchase: Purchase, documentIds: List<String>): String {
        val owner = identity.identity.first().id
        require(purchase.ownerId.isEmpty() || purchase.ownerId == owner)
        return db.withTransaction {
            val old = dao.purchaseById(purchase.id)
            require(old == null || old.ownerId == owner)
            if ((old?.revision ?: 0) != purchase.revision || old?.deletedAt != null)
                throw RevisionConflictException()
            val now = System.currentTimeMillis()
            val saved =
                purchase.copy(
                    ownerId = owner,
                    merchant = purchase.merchant.trim(),
                    revision = purchase.revision + 1,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                )
            dao.putPurchase(
                PurchaseEntity(
                    saved.id,
                    owner,
                    saved.merchant,
                    saved.purchasedOn.toString(),
                    saved.deliveredOn?.toString(),
                    saved.total.minorUnits,
                    saved.total.currency,
                    saved.channel.name,
                    saved.orderNumber,
                    saved.notes,
                    saved.revision,
                    old?.remoteRevision ?: 0,
                    saved.createdAt,
                    now,
                )
            )
            dao.clearProducts(saved.id)
            dao.putProducts(
                saved.lines.map {
                    ProductEntity(
                        it.product.id,
                        saved.id,
                        it.product.name.trim(),
                        it.product.warrantyEnd?.toString(),
                        it.product.returnEnd?.toString(),
                        it.product.deadlineSource,
                    )
                }
            )
            dao.putLines(
                saved.lines.mapIndexed { index, line ->
                    LineEntity(
                        line.id,
                        saved.id,
                        line.product.id,
                        index,
                        line.quantity,
                        line.unitPrice?.minorUnits,
                    )
                }
            )
            dao.detachDocuments(owner, saved.id, documentIds)
            documentIds.distinct().forEach { documentId ->
                val document = requireNotNull(dao.document(documentId, owner))
                require(document.purchaseId == null || document.purchaseId == saved.id)
                dao.putDocument(document.copy(purchaseId = saved.id))
                // The purchase aggregate owns its document associations in protocol v1.
            }
            val payload =
                saved
                    .json()
                    .apply { put("document_ids", org.json.JSONArray(documentIds)) }
                    .toString()
            dao.enqueue(
                OutboxEntity(
                    saved.id,
                    owner,
                    newId(),
                    "PURCHASE",
                    old?.remoteRevision ?: 0,
                    payload,
                    now,
                )
            )
            saved.id
        }
    }

    override suspend fun delete(id: String, expectedRevision: Long) {
        val owner = identity.identity.first().id
        db.withTransaction {
            val old = requireNotNull(dao.purchase(id, owner))
            if (old.revision != expectedRevision || old.deletedAt != null)
                throw RevisionConflictException()
            val now = System.currentTimeMillis()
            dao.putPurchase(old.copy(revision = old.revision + 1, deletedAt = now, updatedAt = now))
            val tombstone =
                org.json.JSONObject().apply {
                    put("schema_version", 1)
                    put("id", id)
                    put("deleted_at", now)
                    put("revision", old.revision + 1)
                }
            dao.enqueue(
                OutboxEntity(
                    id,
                    owner,
                    newId(),
                    "PURCHASE_TOMBSTONE",
                    old.remoteRevision,
                    tombstone.toString(),
                    now,
                )
            )
        }
    }

    override suspend fun addDocument(document: ReceiptDocument): ReceiptDocument {
        val owner = identity.identity.first().id
        val original = document.variants.first { it.kind == VariantKind.ORIGINAL }
        return db.withTransaction {
            val duplicate = dao.duplicate(owner, original.sha256)
            if (duplicate != null) {
                // A restored cloud image may have no local source yet. Keep the newly
                // supplied original under its existing private path, attached to that ID.
                if (duplicate.variants.none { it.kind == VariantKind.ORIGINAL.name }) {
                    dao.putVariants(
                        listOf(
                            VariantEntity(
                                original.id,
                                duplicate.document.id,
                                original.kind.name,
                                original.relativePath,
                                original.mimeType,
                                original.byteSize,
                                original.sha256,
                            )
                        )
                    )
                    return@withTransaction requireNotNull(
                            dao.documentRecord(duplicate.document.id, owner)
                        )
                        .model()
                }
                return@withTransaction duplicate.model()
            }
            val saved = document.copy(ownerId = owner)
            dao.putDocument(
                DocumentEntity(
                    saved.id,
                    owner,
                    null,
                    saved.displayName,
                    original.sha256,
                    saved.extractedText,
                    saved.state.name,
                    saved.createdAt,
                )
            )
            dao.putVariants(
                saved.variants.map {
                    VariantEntity(
                        it.id,
                        saved.id,
                        it.kind.name,
                        it.relativePath,
                        it.mimeType,
                        it.byteSize,
                        it.sha256,
                    )
                }
            )
            dao.enqueue(
                OutboxEntity(
                    saved.id,
                    owner,
                    newId(),
                    "DOCUMENT",
                    0,
                    saved.cloudJson().toString(),
                    saved.createdAt,
                )
            )
            saved
        }
    }
}

private fun PurchaseRecord.model() =
    Purchase(
        id = purchase.id,
        ownerId = purchase.ownerId,
        merchant = purchase.merchant,
        purchasedOn = LocalDate.parse(purchase.purchasedOn),
        deliveredOn = purchase.deliveredOn?.let(LocalDate::parse),
        total = Money(purchase.totalMinor, purchase.currency),
        channel = PurchaseChannel.valueOf(purchase.channel),
        orderNumber = purchase.orderNumber,
        notes = purchase.notes,
        revision = purchase.revision,
        createdAt = purchase.createdAt,
        updatedAt = purchase.updatedAt,
        deletedAt = purchase.deletedAt,
        lines =
            lines
                .sortedBy { it.position }
                .map { line ->
                    val product = products.first { it.id == line.productId }
                    PurchaseLine(
                        line.id,
                        Product(
                            product.id,
                            product.name,
                            product.warrantyEnd?.let(LocalDate::parse),
                            product.returnEnd?.let(LocalDate::parse),
                            product.deadlineSource,
                        ),
                        line.quantity,
                        line.unitPriceMinor?.let { Money(it, purchase.currency) },
                    )
                },
    )

internal fun DocumentRecord.model() =
    ReceiptDocument(
        document.id,
        document.ownerId,
        document.purchaseId,
        document.displayName,
        variants.map {
            DocumentVariant(
                it.id,
                VariantKind.valueOf(it.kind),
                it.relativePath,
                it.mimeType,
                it.byteSize,
                it.sha256,
            )
        },
        document.extractedText,
        DocumentState.valueOf(document.state),
        document.createdAt,
        document.originalSha256,
    )
