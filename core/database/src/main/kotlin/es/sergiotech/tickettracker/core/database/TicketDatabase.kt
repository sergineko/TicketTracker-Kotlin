package es.sergiotech.tickettracker.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {
    @Transaction
    @Query(
        "SELECT * FROM purchases WHERE ownerId = :ownerId AND deletedAt IS NULL ORDER BY purchasedOn DESC, createdAt DESC"
    )
    fun purchases(ownerId: String): Flow<List<PurchaseRecord>>

    @Query("SELECT * FROM purchases WHERE id = :id AND ownerId = :ownerId")
    suspend fun purchase(id: String, ownerId: String): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun purchaseById(id: String): PurchaseEntity?

    @Upsert suspend fun putPurchase(purchase: PurchaseEntity)

    @Insert suspend fun putProducts(products: List<ProductEntity>)

    @Insert suspend fun putLines(lines: List<LineEntity>)

    @Query("DELETE FROM products WHERE purchaseId = :purchaseId")
    suspend fun clearProducts(purchaseId: String)

    @Transaction
    @Query("SELECT * FROM documents WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    fun documents(ownerId: String): Flow<List<DocumentRecord>>

    @Transaction
    @Query("SELECT * FROM documents WHERE ownerId = :ownerId AND originalSha256 = :hash LIMIT 1")
    suspend fun duplicate(ownerId: String, hash: String): DocumentRecord?

    @Query("SELECT * FROM documents WHERE id = :id AND ownerId = :ownerId")
    suspend fun document(id: String, ownerId: String): DocumentEntity?

    @Upsert suspend fun putDocument(document: DocumentEntity)

    @Upsert suspend fun putVariants(variants: List<VariantEntity>)

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id AND ownerId = :ownerId")
    suspend fun documentRecord(id: String, ownerId: String): DocumentRecord?

    @Query("SELECT * FROM sync_outbox WHERE entityId=:id AND ownerId=:ownerId")
    suspend fun pending(id: String, ownerId: String): OutboxEntity?

    @Query(
        "DELETE FROM sync_outbox WHERE entityId=:id AND ownerId=:ownerId AND operationId=:operationId"
    )
    suspend fun acknowledge(id: String, ownerId: String, operationId: String)

    @Query("DELETE FROM sync_outbox WHERE entityId=:id AND ownerId=:ownerId")
    suspend fun discardOperation(id: String, ownerId: String)

    @Query(
        "UPDATE sync_outbox SET expectedRemoteRevision=:revision WHERE entityId=:id AND ownerId=:ownerId"
    )
    suspend fun rebasePending(id: String, ownerId: String, revision: Long)

    @Query("UPDATE purchases SET ownerId=:toOwner WHERE ownerId=:fromOwner")
    suspend fun adoptPurchases(fromOwner: String, toOwner: String)

    @Query("UPDATE documents SET ownerId=:toOwner WHERE ownerId=:fromOwner")
    suspend fun adoptDocuments(fromOwner: String, toOwner: String)

    @Query("UPDATE sync_outbox SET ownerId=:toOwner WHERE ownerId=:fromOwner")
    suspend fun adoptOutbox(fromOwner: String, toOwner: String)

    @Query(
        "UPDATE documents SET purchaseId=NULL WHERE ownerId=:ownerId AND purchaseId=:purchaseId AND id NOT IN (:documentIds)"
    )
    suspend fun detachDocuments(ownerId: String, purchaseId: String, documentIds: List<String>)

    @Query("SELECT cursor FROM sync_cursors WHERE ownerId=:ownerId")
    suspend fun cursor(ownerId: String): Long?

    @Upsert suspend fun putCursor(cursor: SyncCursorEntity)

    @Upsert suspend fun putConflict(conflict: SyncConflictEntity)

    @Query("SELECT * FROM sync_conflicts WHERE ownerId=:ownerId")
    fun conflicts(ownerId: String): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE entityId=:id AND ownerId=:ownerId")
    suspend fun conflict(id: String, ownerId: String): SyncConflictEntity?

    @Query("DELETE FROM sync_conflicts WHERE entityId=:id AND ownerId=:ownerId")
    suspend fun clearConflict(id: String, ownerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(operation: OutboxEntity)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE ownerId = :ownerId")
    fun pendingCount(ownerId: String): Flow<Int>

    @Query("SELECT * FROM sync_outbox WHERE ownerId = :ownerId ORDER BY createdAt")
    suspend fun outbox(ownerId: String): List<OutboxEntity>
}

@Database(
    entities =
        [
            PurchaseEntity::class,
            ProductEntity::class,
            LineEntity::class,
            DocumentEntity::class,
            VariantEntity::class,
            OutboxEntity::class,
            SyncCursorEntity::class,
            SyncConflictEntity::class,
        ],
    version = 2,
    exportSchema = true,
)
abstract class TicketDatabase : RoomDatabase() {
    abstract fun tickets(): TicketDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE documents ADD COLUMN remoteRevision INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS sync_cursors (ownerId TEXT NOT NULL PRIMARY KEY, cursor INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS sync_conflicts (entityId TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, kind TEXT NOT NULL, remoteRevision INTEGER NOT NULL, remotePayload TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_sync_conflicts_ownerId ON sync_conflicts(ownerId)"
                    )
                }
            }
    }
}
