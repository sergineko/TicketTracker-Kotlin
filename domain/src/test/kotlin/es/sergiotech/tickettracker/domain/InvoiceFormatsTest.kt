package es.sergiotech.tickettracker.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class InvoiceFormatsTest {
    private val reader = ReceiptExtractor()
    // Synthetic documents, without private customer details or references.
    private val invoice =
        """
        TECNICA EJEMPLO NORTE
        AV DE EJEMPLO 10
        Factura Simpliﬁcada
        Datos de cliente Referencia Método de pago
        CLIENTE EJEMPLO F-123 Pago realizado online
        Fecha de factura Medio de entrega
        21.08.2026 Recogida en tienda
        Fecha de pago Fecha de entrega
        22.08.2026 23.08.2026
        Pos N° producto Descripción Cant. Tipo IVA P.v.p. unitario Total
        1 7654321 AURICULARES AZ 8 (ANULACION 1 21,00% 259,90 EUR 259,90 EUR
        RUIDO)
        2 Gastos de envío de entrega estándar 1 21,00% 0,00 EUR 0,00 EUR
        Tipo IVA Base Imponible Cuota IVA Total Total 259,90 EUR
        21,00% 214,79 EUR 45,11 EUR 259,90 EUR Pago inicial 259,90 EUR
        Importe a pagar 0,00 EUR
        """
            .trimIndent()

    @Test
    fun spacingNoiseInsideDatesAndDecimalsIsSupportedWithoutReadingSkuDatesOrNegativePrices() {
        val result =
            reader.extract(
                "TIENDA\nFecha de factura: 21.08. 2026\nAuriculares WH-2000 1.299, 00\nTOTAL FACTURA 1.299, 00 EUR"
            )
        assertEquals(LocalDate.of(2026, 8, 21), result.date?.value)
        assertEquals(Money(129900), result.total?.value)
        assertEquals(listOf(ReceiptItem("Auriculares WH-2000", 1, Money(129900))), result.items)
        assertNull(reader.extract("REF2026-08-21XYZ").date)
        assertTrue(reader.extract("Auriculares -10,00").items.isEmpty())
    }

    @Test
    fun missingQuantityGlyphCanOnlyBeRecoveredFromConsistentUnitAndLineAmounts() {
        val result =
            reader.extract(
                "Pos N° producto Descripción Cant. Tipo IVA P.v.p. unitario Total\nAURICULARES AZ (ANULACION\n1 7654321 21,00% 259,90 EUR 259,90 EUR\nRUIDO)\nTOTAL 259,90"
            )
        assertEquals(
            listOf(ReceiptItem("AURICULARES AZ (ANULACION RUIDO)", 1, Money(25990))),
            result.items,
        )
        val ambiguous =
            reader.extract(
                "Descripción Cantidad Precio unitario Total\nAURICULARES\n21,00% 259,90 250,00\nTOTAL 250,00"
            )
        assertTrue(ambiguous.items.isEmpty())
    }

    @Test
    fun labelGeometryKeepsInvoiceDeliveryAndOrderValuesInTheirOwnColumns() {
        fun box(text: String, x: Float, y: Float, w: Float = 170f) =
            ReceiptTextBox(text, x, y, x + w, y + 20f)
        val boxes =
            listOf(
                box("Fecha de factura", 300f, 0f),
                box("Medio de entrega", 600f, 0f),
                box("21.08.2026", 300f, 30f),
                box("Recogida", 600f, 30f),
                box("Fecha de pago", 300f, 70f),
                box("Fecha de entrega", 600f, 70f),
                box("22.08.2026", 300f, 100f),
                box("23.08.2026", 600f, 100f),
                box("Número de factura", 300f, 140f),
                box("Pedido online número", 600f, 140f, 220f),
                box("cliente", 0f, 170f),
                box("F-9876", 300f, 170f),
                box("P-1234", 600f, 170f),
            )
        val text = ReceiptLabelLayout.readingText(boxes)
        val result = reader.extract(text)
        assertEquals(LocalDate.of(2026, 8, 21), result.date?.value)
        assertEquals(LocalDate.of(2026, 8, 23), result.deliveredOn?.value)
        assertEquals("P-1234", result.reference?.value)
    }

    @Test
    fun paidInvoiceKeepsPurchaseTotalAndWrappedProductWithoutShippingAsInventory() {
        val result = reader.extract(invoice)
        assertEquals("TECNICA EJEMPLO NORTE", result.merchant?.value)
        assertEquals(LocalDate.of(2026, 8, 21), result.date?.value)
        assertEquals(Money(25990), result.total?.value)
        assertEquals(
            listOf(ReceiptItem("AURICULARES AZ 8 (ANULACION RUIDO)", 1, Money(25990))),
            result.items,
        )
        assertTrue(result.deadlines.isEmpty())
    }

    @Test
    fun labelsBeatDeliveryAndPaymentDatesAndInvalidDatesStayUnknown() {
        assertEquals(
            LocalDate.of(2026, 8, 21),
            reader
                .extract(
                    "Fecha de factura: 2026-08-21\nFecha de entrega 23.08.2026\nFecha de pago 22/08/2026"
                )
                .date
                ?.value,
        )
        assertNull(reader.extract("Fecha de factura: 31.02.2026\nFecha de entrega 01.03.2026").date)
        assertNull(reader.extract("Fecha de factura 21.08.2026\nFecha de factura 22.08.2026").date)
    }

    @Test
    fun thousandsAndEnglishColumnLabelsRetainExactAmountsAndQuantities() {
        val result =
            reader.extract(
                "EXAMPLE SHOP\nInvoice date: 2026-08-21\nDescription Qty Unit price Total\nLaptop Pro 2 1,299.00 USD 2,598.00 USD\nGrand total 2,598.00 USD\nAmount due 0.00 USD"
            )
        assertEquals(Money(259800, "USD"), result.total?.value)
        assertEquals(listOf(ReceiptItem("Laptop Pro", 2, Money(129900, "USD"))), result.items)
        assertEquals(Money(129900), reader.extract("Total factura 1.299,00 EUR").total?.value)
    }

    @Test
    fun differentQuantityPositionsAndAmbiguousPricesNeverInventAUnitValue() {
        val result =
            reader.extract(
                "Descripción Cantidad Precio unitario Importe\nMonitor 2 100,00 190,00\nTOTAL 190,00"
            )
        assertEquals(listOf(ReceiptItem("Monitor", 2, null)), result.items)
        assertNull(reader.extract("TOTAL 100,00\nTOTAL 200,00\nImporte a pagar 0,00").total)
        assertEquals(
            Money(10000),
            reader.extract("TOTAL 100,00\nImporte a pagar 0,00").total?.value,
        )
    }

    @Test
    fun repeatedHeadersAcrossPagesKeepAllLinesAndPreferExplicitInvoiceTotal() {
        val result =
            reader.extract(
                "Descripción Cantidad Precio Total\nCable 1 10,00 10,00\nSubtotal 10,00\nPágina 1 de 2\nDescripción Cantidad Precio Total\nMonitor 1 190,00 190,00\nTOTAL FACTURA 200,00"
            )
        assertEquals(2, result.items.size)
        assertEquals(Money(20000), result.total?.value)
    }

    @Test
    fun descriptionBeforeThePriceRowIsCombinedWithoutLosingItsFirstLine() {
        val result =
            reader.extract(
                invoice.replace(
                    "1 21,00% 259,90 EUR 259,90 EUR\nRUIDO)",
                    "\nRUIDO)\n1 21,00% 259,90 EUR 259,90 EUR",
                )
            )
        assertEquals(
            listOf(ReceiptItem("AURICULARES AZ 8 (ANULACION RUIDO)", 1, Money(25990))),
            result.items,
        )
    }
}
