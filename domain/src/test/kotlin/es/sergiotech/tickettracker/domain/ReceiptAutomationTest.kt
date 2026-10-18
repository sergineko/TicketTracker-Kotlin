package es.sergiotech.tickettracker.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReceiptAutomationTest {
    private val extractor = ReceiptExtractor()

    @Test
    fun `items keep exact prices and do not mistake tax and payment for products`() {
        val result =
            extractor.extract(
                "TIENDA\nFecha 18/10/2026\nCable USB 2 x 9,95 19,90\nAuriculares 39,95\nIVA 21% 10,39\nTOTAL 59,85 EUR\nTarjeta 59,85\nCambio 0,00"
            )
        assertEquals(
            listOf(
                ReceiptItem("Cable USB", 2, Money(995)),
                ReceiptItem("Auriculares", 1, Money(3995)),
            ),
            result.items,
        )
        assertEquals(Money(5985), result.total?.value)
    }

    @Test
    fun `quantity with ambiguous price has no invented unit price`() {
        assertEquals(
            listOf(ReceiptItem("Cable USB", 2, null)),
            extractor.extract("2 x Cable USB 19,90").items,
        )
        assertTrue(extractor.extract("Cable USB 2 x 9,95 18,90").items.isEmpty())
    }

    @Test
    fun `purchase date stays independent of printed policy dates`() {
        val result =
            extractor.extract(
                "Fecha de compra: 18/10/2026\nDevoluciones hasta 17/11/2026\nGarantía válida hasta 18/10/2028"
            )
        assertEquals(LocalDate.of(2026, 10, 18), result.date?.value)
        assertEquals(2, result.deadlines.size)
        assertEquals(
            LocalDate.of(2026, 11, 17),
            result.deadlines.first { it.kind == PrintedDeadlineKind.RETURN }.date,
        )
        assertTrue(
            result.deadlines.all {
                it.evidence.isNotBlank() && it.ruleVersion == "printed_absolute_date_v1"
            }
        )
    }

    @Test
    fun `dates and periods never create statutory or conditional rights`() {
        listOf(
                "18/10/2026\nCompra online",
                "Garantía 3 años\nDevoluciones 30 días",
                "No devoluciones hasta 17/11/2026",
                "Devoluciones hasta 17/11/2026 si está precintado",
                "Garantía hasta 31/02/2028",
            )
            .forEach { assertTrue(it, extractor.extract(it).deadlines.isEmpty()) }
        assertTrue(
            extractor
                .extract("Devoluciones hasta 17/11/2026\nDevoluciones hasta 18/11/2026")
                .deadlines
                .isEmpty()
        )
    }

    @Test
    fun `explicit currencies remain attached to prices`() {
        val result = extractor.extract("Headphones 19.95\nTOTAL 19.95 GBP")
        assertEquals(Money(1995, "GBP"), result.total?.value)
        assertEquals(Money(1995, "GBP"), result.items.single().unitPrice)
        assertNull(extractor.extract("TOTAL 19.95 USD\nTOTAL 18.00 EUR").total)
    }
}
