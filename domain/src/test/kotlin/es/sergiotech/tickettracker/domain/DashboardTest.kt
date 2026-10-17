package es.sergiotech.tickettracker.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DashboardTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun criticalWarrantyIncludesTodayAndDayThirtyButNotExpiredOrUnknown() {
        for ((offset, expected) in listOf(-1L to false, 0L to true, 30L to true, 31L to false)) {
            val product = Product(name = "Monitor", warrantyEnd = today.plusDays(offset))
            assertEquals(expected, product.matches(ProductFilter.CRITICAL, today))
            assertEquals(offset >= 0, product.matches(ProductFilter.WARRANTY, today))
        }
        assertFalse(Product(name = "Unknown").matches(ProductFilter.CRITICAL, today))
    }

    @Test
    fun returnsOnlyUseRecordedReturnDateIndependentOfWarranty() {
        val product = Product(name = "Monitor", warrantyEnd = today.plusYears(2))
        assertFalse(product.matches(ProductFilter.RETURNS, today))
        assertTrue(product.copy(returnEnd = today).matches(ProductFilter.RETURNS, today))
        assertTrue(
            product.copy(returnEnd = today.plusDays(90)).matches(ProductFilter.RETURNS, today)
        )
        assertFalse(
            product.copy(returnEnd = today.minusDays(1)).matches(ProductFilter.RETURNS, today)
        )
    }

    @Test
    fun overdueActiveContractsStayVisibleForRenewalUntilManuallyUpdated() {
        val record =
            TrackedContract(
                kind = ContractKind.SUBSCRIPTION,
                name = "Plan",
                provider = "",
                amount = Money(1999),
                period = BillingPeriod.MONTHLY,
                nextDate = today.minusDays(1),
            )
        assertTrue(record.needsRenewal(today))
        assertTrue(record.copy(nextDate = today.plusDays(30)).needsRenewal(today))
        assertFalse(record.copy(nextDate = today.plusDays(31)).needsRenewal(today))
        assertFalse(record.copy(active = false).needsRenewal(today))
    }
}
