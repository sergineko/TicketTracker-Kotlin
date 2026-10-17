package es.sergiotech.tickettracker.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracked_contracts", indices = [Index("ownerId")])
data class ContractEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val kind: String,
    val name: String,
    val provider: String,
    val amountMinor: Long,
    val currency: String,
    val period: String,
    val nextDate: String,
    val active: Boolean,
    val revision: Long,
    val deletedAt: Long? = null,
)

@Dao
interface CoverageDao {
    @Query(
        "SELECT * FROM tracked_contracts WHERE ownerId=:owner AND deletedAt IS NULL ORDER BY nextDate,name"
    )
    fun observeContracts(owner: String): Flow<List<ContractEntity>>

    @Query("SELECT * FROM tracked_contracts WHERE id=:id")
    suspend fun contract(id: String): ContractEntity?

    @Upsert suspend fun put(contract: ContractEntity)

    @Query("UPDATE tracked_contracts SET ownerId=:toOwner WHERE ownerId=:fromOwner")
    suspend fun adoptContracts(fromOwner: String, toOwner: String)
}
