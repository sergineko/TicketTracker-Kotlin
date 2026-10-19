package es.sergiotech.tickettracker.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReceiptTableTest {
    private val extractor = ReceiptExtractor()
    // Fictional receipt. No customer, card, address or image from a real ticket is included.
    private val receipt =
        """
        SUPERMERCADO EJEMPLO, S.A. A-12345678
        AV DE EJEMPLO
        00000 CIUDAD
        TELEFONO: 000000000
        18/10/2026 15:55 OP: 123456
        FACTURA SIMPLIFICADA: F-123456
        Cnt. Descripción P.Unit Importe
        1 BOLSA 0,15
        1 PAN INTEGRAL 1,70
        1 LECHE 1,70
        1 GALLETAS X4 1,15
        1 ARROZ 1,30
        1 YOGUR 2,75
        1 ACEITE 4,82
        2 JABON 2,95 5,90
        1 TORTILLA 2,60
        TOTAL (€) 22,07
        TARJETA BANCARIA 22,07
        IVA BASE IMP. (€) CUOTA (€) TOTAL (€)
        10% 14,56 1,46 16,02
        21% 5,00 1,05 6,05
        TOTAL 19,56 2,51 22,07
        Importe: 22,07 €
        SE ADMITEN DEVOLUCIONES CON TICKET
        """
            .trimIndent()

    @Test
    fun companyTaxIdentifierDoesNotTurnTheAddressIntoMerchant() {
        val result = extractor.extract(receipt)
        assertEquals("SUPERMERCADO EJEMPLO", result.merchant?.value)
        assertEquals(LocalDate.of(2026, 10, 18), result.date?.value)
        assertEquals(Money(2207), result.total?.value)
        assertEquals(9, result.items.size)
        assertEquals(ReceiptItem("JABON", 2, Money(295)), result.items[7])
        assertEquals(2207L, result.items.sumOf { it.unitPrice!!.minorUnits * it.quantity })
        assertTrue(result.deadlines.isEmpty())
    }

    @Test
    fun imperfectDescriptionHeaderStillUsesQuantityAndAmountColumns() {
        val result =
            extractor.extract(receipt.replace("Descripción", "Deseripción").replace("1 ", "I "))
        assertEquals(9, result.items.size)
        assertEquals("BOLSA", result.items.first().name)
        assertEquals(ReceiptItem("JABON", 2, Money(295)), result.items[7])
        assertEquals(2207L, result.items.sumOf { it.unitPrice!!.minorUnits * it.quantity })
    }

    @Test
    fun totalOnItsOwnLineAndParenthesizedCurrencyAreSupportedWithoutUsingTaxSubtotals() {
        assertEquals(
            Money(2207),
            extractor.extract("TOTAL (€)\n22,07\nTOTAL 19,56 2,51 22,07").total?.value,
        )
        assertNull(extractor.extract("SUBTOTAL 22,07\nIVA 2,51").total)
        assertNull(extractor.extract("TOTAL 22,07\nTOTAL 23,07").total)
    }

    @Test
    fun physicalRowsAreReconstructedFromColumnFirstProviderOrder() {
        fun box(text: String, x: Float, y: Float, width: Float = 100f) =
            ReceiptTextBox(text, x, y, x + width, y + 20f)
        val text =
            ReceiptLayout.readingText(
                listOf(
                    box("Cnt. Descripción", 0f, 0f, 250f),
                    box("1 PAN", 0f, 40f),
                    box("2 JABON", 0f, 80f),
                    box("TOTAL (€)", 200f, 120f),
                    box("Importe", 500f, 0f),
                    box("1,70", 500f, 40f),
                    box("5,90", 500f, 80f),
                    box("7,60", 500f, 120f),
                    box("P.Unit", 350f, 0f),
                    box("2,95", 350f, 80f),
                )
            )
        val result = extractor.extract(text)
        assertEquals(Money(760), result.total?.value)
        assertEquals(
            text,
            listOf(ReceiptItem("PAN", 1, Money(170)), ReceiptItem("JABON", 2, Money(295))),
            result.items,
        )
    }

    @Test
    fun inconsistentUnitAndLineAmountsDoNotInventAnItemPrice() {
        assertTrue(
            extractor.extract("Descripción Importe\n2 JABON 2,95 5,80\nTOTAL 5,80").items.isEmpty()
        )
    }
}
