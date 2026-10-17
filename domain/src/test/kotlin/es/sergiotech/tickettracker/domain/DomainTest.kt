package es.sergiotech.tickettracker.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun `Spanish comma and English decimal retain exact cents`() {
        assertEquals(Money(1999), Money.parse("19,99"))
        assertEquals(Money(1999), Money.parse("19.99"))
        assertEquals(Money(0), Money.parse("0"))
    }

    @Test
    fun `ambiguous or excessive amounts are rejected`() {
        listOf("1.234,56", "-5", "12.345", "NaN", "", "1e6", "999999999999999999999999").forEach {
            assertNull(it, Money.parse(it))
        }
    }

    @Test
    fun `deadline stays valid through its final day`() {
        assertEquals(DeadlineState.ENDING_SOON, deadlineState(today, today, 7))
        assertEquals(DeadlineState.EXPIRED, deadlineState(today.minusDays(1), today))
        assertEquals(DeadlineState.ACTIVE, deadlineState(today.plusDays(31), today))
    }

    @Test
    fun `unknown dates remain unknown`() {
        assertEquals(DeadlineState.UNKNOWN, deadlineState(null, today))
    }

    @Test
    fun `returns and warranties use different reminder windows`() {
        val p =
            purchase()
                .copy(
                    lines =
                        listOf(
                            PurchaseLine(
                                product =
                                    Product(
                                        name = "Monitor",
                                        warrantyEnd = today.plusDays(20),
                                        returnEnd = today.plusDays(10),
                                    )
                            )
                        )
                )
        val alerts = upcomingDeadlines(listOf(p), today)
        assertEquals(1, alerts.size)
        assertFalse(alerts.single().isReturn)
    }

    @Test
    fun `leap day and year boundaries are compared as calendar dates`() {
        assertEquals(
            DeadlineState.ENDING_SOON,
            deadlineState(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 28), 1),
        )
        assertEquals(
            DeadlineState.ENDING_SOON,
            deadlineState(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31), 1),
        )
    }

    @Test
    fun `unlabelled prices do not become totals`() {
        assertNull(ReceiptExtractor().extract("TIENDA\nIVA 21% 4,20\nSubtotal 20,00\n5,00").total)
    }

    @Test
    fun `extracts explicitly labelled total rather than subtotal or tax`() {
        val result =
            ReceiptExtractor()
                .extract("TIENDA EJEMPLO\n17/10/2026\nSUBTOTAL 10,00\nIVA 2,10\nTOTAL 12,10 EUR")
        assertEquals("TIENDA EJEMPLO", result.merchant?.value)
        assertEquals(today, result.date?.value)
        assertEquals(Money(1210), result.total?.value)
        assertTrue(result.merchant!!.confidence < 1f)
    }

    @Test
    fun `multiple totals require review`() {
        assertNull(ReceiptExtractor().extract("TOTAL 10,00\nTOTAL 20,00").total)
    }

    @Test
    fun `conflicting and invalid dates require review`() {
        assertNull(ReceiptExtractor().extract("31/02/2026").date)
        assertNull(ReceiptExtractor().extract("17/10/2026\n18/10/2026").date)
    }

    @Test
    fun `receipt without fields stays empty`() {
        assertEquals(ReceiptExtraction(), ReceiptExtractor().extract(""))
    }

    @Test
    fun `several products are saved as separate stable assets`() = runTest {
        val repository = FakeRepository()
        val p =
            purchase()
                .copy(
                    lines =
                        listOf(
                            PurchaseLine(product = Product(name = "Monitor")),
                            PurchaseLine(product = Product(name = "Teclado")),
                        )
                )
        SavePurchase(repository)(p)
        assertEquals(2, repository.saved!!.lines.size)
        assertNotEquals(
            repository.saved!!.lines[0].product.id,
            repository.saved!!.lines[1].product.id,
        )
    }

    @Test
    fun `validation failure does not write to storage`() = runTest {
        val repository = FakeRepository()
        assertThrowsSuspend { SavePurchase(repository)(purchase().copy(merchant = " ")) }
        assertNull(repository.saved)
    }

    @Test
    fun `delivery before purchase cannot be saved`() = runTest {
        assertThrowsSuspend {
            SavePurchase(FakeRepository())(purchase().copy(deliveredOn = today.minusDays(1)))
        }
    }

    @Test
    fun `zero quantities and duplicate asset ids are rejected`() = runTest {
        val line = PurchaseLine(product = Product(name = "Monitor"))
        assertThrowsSuspend {
            SavePurchase(FakeRepository())(purchase().copy(lines = listOf(line.copy(quantity = 0))))
        }
        assertThrowsSuspend {
            SavePurchase(FakeRepository())(
                purchase().copy(lines = listOf(line, line.copy(id = newId())))
            )
        }
    }

    private fun purchase() =
        Purchase(
            merchant = "Tienda",
            purchasedOn = today,
            total = Money(1000),
            lines = listOf(PurchaseLine(product = Product(name = "Producto"))),
        )

    private suspend fun assertThrowsSuspend(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }

    private class FakeRepository : PurchaseRepository {
        var saved: Purchase? = null

        override fun observePurchases() = flowOf(emptyList<Purchase>())

        override fun observeDocuments() = flowOf(emptyList<ReceiptDocument>())

        override fun observePendingCount() = flowOf(0)

        override suspend fun save(purchase: Purchase, documentIds: List<String>): String {
            saved = purchase
            return purchase.id
        }

        override suspend fun delete(id: String, expectedRevision: Long) = Unit

        override suspend fun addDocument(document: ReceiptDocument) = document
    }
}
