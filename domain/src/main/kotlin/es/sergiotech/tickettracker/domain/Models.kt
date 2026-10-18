package es.sergiotech.tickettracker.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

data class Money(val minorUnits: Long, val currency: String = "EUR") {
    init {
        require(minorUnits >= 0)
        require(Currency.getInstance(currency).defaultFractionDigits == 2)
    }

    companion object {
        fun parse(input: String, currency: String = "EUR"): Money? =
            runCatching {
                    val normalized = input.trim().replace(',', '.')
                    require(normalized.matches(Regex("\\d{1,12}(\\.\\d{1,2})?")))
                    Money(BigDecimal(normalized).movePointRight(2).longValueExact(), currency)
                }
                .getOrNull()
    }
}

enum class PurchaseChannel {
    IN_STORE,
    ONLINE,
    OTHER,
}

enum class DocumentState {
    LOCAL_ONLY,
    QUEUED,
    UPLOADING,
    REMOTE_OBJECT_PENDING_COMMIT,
    SYNCED_VERIFIED,
    RETRYABLE_ERROR,
    ACTION_REQUIRED,
}

enum class VariantKind {
    ORIGINAL,
    OPTIMIZED,
    THUMBNAIL,
}

enum class DeadlineState {
    UNKNOWN,
    ACTIVE,
    ENDING_SOON,
    EXPIRED,
}

data class Product(
    val id: String = newId(),
    val name: String,
    val warrantyEnd: LocalDate? = null,
    val returnEnd: LocalDate? = null,
    val deadlineSource: String = "USER_CONFIRMED",
)

data class PurchaseLine(
    val id: String = newId(),
    val product: Product,
    val quantity: Int = 1,
    val unitPrice: Money? = null,
)

data class Purchase(
    val id: String = newId(),
    val ownerId: String = "",
    val merchant: String,
    val purchasedOn: LocalDate,
    val deliveredOn: LocalDate? = null,
    val total: Money,
    val channel: PurchaseChannel = PurchaseChannel.IN_STORE,
    val orderNumber: String = "",
    val notes: String = "",
    val lines: List<PurchaseLine>,
    val revision: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

data class DocumentVariant(
    val id: String = newId(),
    val kind: VariantKind,
    val relativePath: String,
    val mimeType: String,
    val byteSize: Long,
    val sha256: String,
)

data class ReceiptDocument(
    val id: String = newId(),
    val ownerId: String = "",
    val purchaseId: String? = null,
    val displayName: String,
    val variants: List<DocumentVariant>,
    val extractedText: String = "",
    val state: DocumentState = DocumentState.LOCAL_ONLY,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceOriginalSha256: String =
        variants.firstOrNull { it.kind == VariantKind.ORIGINAL }?.sha256.orEmpty(),
)

data class Identity(val id: String, val isLocal: Boolean = true)

data class SyncEnvelope(
    val idempotencyKey: String,
    val entityId: String,
    val expectedRevision: Long,
    val payload: String,
)

sealed interface PushResult {
    data class Accepted(val revision: Long) : PushResult

    data class Conflict(val remoteRevision: Long) : PushResult

    data object NotConfigured : PushResult
}

class RevisionConflictException :
    IllegalStateException("The purchase has changed; reload before saving")
