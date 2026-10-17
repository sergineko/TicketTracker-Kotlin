package es.sergiotech.tickettracker.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

enum class ContractKind {
    SUBSCRIPTION,
    INSURANCE,
}

enum class BillingPeriod {
    MONTHLY,
    YEARLY,
    OTHER,
}

data class TrackedContract(
    val id: String = newId(),
    val ownerId: String = "",
    val kind: ContractKind,
    val name: String,
    val provider: String,
    val amount: Money,
    val period: BillingPeriod,
    val nextDate: LocalDate,
    val active: Boolean = true,
    val revision: Long = 0,
) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 200)
        require(provider.length <= 200)
        require(amount.currency in setOf("EUR", "USD", "GBP"))
    }
}

data class CoverageSnapshot(val contracts: List<TrackedContract>, val ownerId: String = "")

interface CoverageRepository {
    fun observe(): Flow<CoverageSnapshot>

    suspend fun save(contract: TrackedContract)

    suspend fun archive(id: String, revision: Long)
}
