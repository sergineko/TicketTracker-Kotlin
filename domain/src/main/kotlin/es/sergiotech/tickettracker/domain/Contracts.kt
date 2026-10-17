package es.sergiotech.tickettracker.domain

import kotlinx.coroutines.flow.Flow

interface IdentityGateway {
    val identity: Flow<Identity>
}

interface PurchaseRepository {
    fun observePurchases(): Flow<List<Purchase>>

    fun observeDocuments(): Flow<List<ReceiptDocument>>

    fun observePendingCount(): Flow<Int>

    suspend fun save(purchase: Purchase, documentIds: List<String> = emptyList()): String

    suspend fun delete(id: String, expectedRevision: Long)

    suspend fun addDocument(document: ReceiptDocument): ReceiptDocument
}

interface DocumentStore {
    suspend fun verify(variant: DocumentVariant): Boolean
}

/** Provider-neutral contract retained for versioned per-aggregate transports. */
interface SyncGateway {
    suspend fun push(envelope: SyncEnvelope): PushResult
}

class SavePurchase(private val repository: PurchaseRepository) {
    suspend operator fun invoke(
        purchase: Purchase,
        documentIds: List<String> = emptyList(),
    ): String {
        require(purchase.merchant.isNotBlank() && purchase.merchant.length <= 200)
        require(purchase.lines.isNotEmpty() && purchase.lines.size <= 100)
        require(
            purchase.lines.all {
                it.product.name.isNotBlank() &&
                    it.product.name.length <= 200 &&
                    it.quantity in 1..999
            }
        )
        require(purchase.lines.map { it.id }.distinct().size == purchase.lines.size)
        require(purchase.lines.map { it.product.id }.distinct().size == purchase.lines.size)
        require(purchase.notes.length <= 10_000 && purchase.orderNumber.length <= 200)
        require(
            purchase.deliveredOn == null || !purchase.deliveredOn.isBefore(purchase.purchasedOn)
        )
        return repository.save(purchase, documentIds)
    }
}
