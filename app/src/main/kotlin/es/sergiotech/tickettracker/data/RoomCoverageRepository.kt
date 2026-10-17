package es.sergiotech.tickettracker.data

import androidx.room.withTransaction
import es.sergiotech.tickettracker.core.database.*
import es.sergiotech.tickettracker.domain.*
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class RoomCoverageRepository(
    private val db: TicketDatabase,
    private val identity: IdentityGateway,
) : CoverageRepository {
    override fun observe(): Flow<CoverageSnapshot> =
        identity.identity.flatMapLatest { owner ->
            db.coverage().observeContracts(owner.id).map { records ->
                CoverageSnapshot(records.map { it.model() }, owner.id)
            }
        }

    override suspend fun save(contract: TrackedContract) {
        contract.validate()
        val owner = identity.identity.first().id
        require(contract.ownerId.isEmpty() || contract.ownerId == owner)
        db.withTransaction {
            val old = db.coverage().contract(contract.id)
            require(old == null || old.ownerId == owner)
            if (old?.deletedAt != null || (old?.revision ?: 0) != contract.revision)
                throw RevisionConflictException()
            db.coverage()
                .put(
                    ContractEntity(
                        contract.id,
                        owner,
                        contract.kind.name,
                        contract.name.trim(),
                        contract.provider.trim(),
                        contract.amount.minorUnits,
                        contract.amount.currency,
                        contract.period.name,
                        contract.nextDate.toString(),
                        contract.active,
                        contract.revision + 1,
                    )
                )
        }
    }

    override suspend fun archive(id: String, revision: Long) {
        val owner = identity.identity.first().id
        db.withTransaction {
            val old = requireNotNull(db.coverage().contract(id))
            require(old.ownerId == owner)
            if (old.revision != revision || old.deletedAt != null) throw RevisionConflictException()
            db.coverage()
                .put(old.copy(deletedAt = System.currentTimeMillis(), revision = revision + 1))
        }
    }
}

private fun ContractEntity.model() =
    TrackedContract(
        id,
        ownerId,
        ContractKind.valueOf(kind),
        name,
        provider,
        Money(amountMinor, currency),
        BillingPeriod.valueOf(period),
        LocalDate.parse(nextDate),
        active,
        revision,
    )
