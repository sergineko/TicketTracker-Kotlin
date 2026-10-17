package es.sergiotech.tickettracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A deadline remains active for its entire local calendar day. No legal term is inferred. */
fun deadlineState(end: LocalDate?, today: LocalDate, warningDays: Long = 30): DeadlineState {
    require(warningDays >= 0)
    if (end == null) return DeadlineState.UNKNOWN
    val remaining = ChronoUnit.DAYS.between(today, end)
    return when {
        remaining < 0 -> DeadlineState.EXPIRED
        remaining <= warningDays -> DeadlineState.ENDING_SOON
        else -> DeadlineState.ACTIVE
    }
}

data class DeadlineAlert(
    val purchaseId: String,
    val productName: String,
    val end: LocalDate,
    val isReturn: Boolean,
)

fun upcomingDeadlines(purchases: List<Purchase>, today: LocalDate): List<DeadlineAlert> =
    purchases
        .flatMap { purchase ->
            purchase.lines.flatMap { line ->
                listOfNotNull(
                    line.product.returnEnd
                        ?.takeIf { deadlineState(it, today, 7) == DeadlineState.ENDING_SOON }
                        ?.let { DeadlineAlert(purchase.id, line.product.name, it, true) },
                    line.product.warrantyEnd
                        ?.takeIf { deadlineState(it, today, 30) == DeadlineState.ENDING_SOON }
                        ?.let { DeadlineAlert(purchase.id, line.product.name, it, false) },
                )
            }
        }
        .sortedBy { it.end }
