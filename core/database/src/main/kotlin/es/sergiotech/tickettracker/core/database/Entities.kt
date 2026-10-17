package es.sergiotech.tickettracker.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "purchases", indices = [Index("ownerId"), Index("purchasedOn")])
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val merchant: String,
    val purchasedOn: String,
    val deliveredOn: String?,
    val totalMinor: Long,
    val currency: String,
    val channel: String,
    val orderNumber: String,
    val notes: String,
    val revision: Long,
    val remoteRevision: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "products",
    foreignKeys =
        [ForeignKey(PurchaseEntity::class, ["id"], ["purchaseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("purchaseId")],
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val purchaseId: String,
    val name: String,
    val warrantyEnd: String?,
    val returnEnd: String?,
    val deadlineSource: String,
)

@Entity(
    tableName = "purchase_lines",
    foreignKeys =
        [
            ForeignKey(
                PurchaseEntity::class,
                ["id"],
                ["purchaseId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(ProductEntity::class, ["id"], ["productId"], onDelete = ForeignKey.CASCADE),
        ],
    indices = [Index("purchaseId"), Index(value = ["productId"], unique = true)],
)
data class LineEntity(
    @PrimaryKey val id: String,
    val purchaseId: String,
    val productId: String,
    val position: Int,
    val quantity: Int,
    val unitPriceMinor: Long?,
)

data class PurchaseRecord(
    @Embedded val purchase: PurchaseEntity,
    @Relation(parentColumn = "id", entityColumn = "purchaseId") val products: List<ProductEntity>,
    @Relation(parentColumn = "id", entityColumn = "purchaseId") val lines: List<LineEntity>,
)

@Entity(
    tableName = "documents",
    foreignKeys =
        [ForeignKey(PurchaseEntity::class, ["id"], ["purchaseId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("ownerId"), Index("purchaseId"), Index(value = ["ownerId", "originalSha256"])],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val purchaseId: String?,
    val displayName: String,
    val originalSha256: String,
    val extractedText: String,
    val state: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val remoteRevision: Long = 0,
)

@Entity(
    tableName = "document_variants",
    foreignKeys =
        [ForeignKey(DocumentEntity::class, ["id"], ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentId")],
)
data class VariantEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val kind: String,
    val relativePath: String,
    val mimeType: String,
    val byteSize: Long,
    val sha256: String,
)

data class DocumentRecord(
    @Embedded val document: DocumentEntity,
    @Relation(parentColumn = "id", entityColumn = "documentId") val variants: List<VariantEntity>,
)

@Entity(tableName = "sync_outbox", indices = [Index("ownerId")])
data class OutboxEntity(
    @PrimaryKey val entityId: String,
    val ownerId: String,
    val operationId: String,
    val entityType: String,
    val expectedRemoteRevision: Long,
    val payload: String,
    val createdAt: Long,
)

@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(@PrimaryKey val ownerId: String, val cursor: Long)

@Entity(tableName = "sync_conflicts", indices = [Index("ownerId")])
data class SyncConflictEntity(
    @PrimaryKey val entityId: String,
    val ownerId: String,
    val kind: String,
    val remoteRevision: Long,
    val remotePayload: String,
)
