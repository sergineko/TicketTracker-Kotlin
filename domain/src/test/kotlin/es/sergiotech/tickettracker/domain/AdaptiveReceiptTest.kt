package es.sergiotech.tickettracker.domain

import org.junit.Assert.*
import org.junit.Test

class AdaptiveReceiptTest {
    private val reader = ReceiptExtractor()

    @Test
    fun columnOrderComesFromLabelsForFashionHardwareRestaurantsAndServices() {
        val cases =
            listOf(
                Triple(
                    "Artículo Uds Precio Importe",
                    "CAMISETA AZUL 2 15,00 30,00",
                    ReceiptItem("CAMISETA AZUL", 2, Money(1500)),
                ),
                Triple(
                    "Importe Concepto Cantidad Precio",
                    "60,00 MONTAJE MUEBLE 2 30,00",
                    ReceiptItem("MONTAJE MUEBLE", 2, Money(3000)),
                ),
                Triple(
                    "Cantidad Concepto Importe Precio",
                    "2 MENU DEL DIA 25,00 12,50",
                    ReceiptItem("MENU DEL DIA", 2, Money(1250)),
                ),
                Triple(
                    "SKU Producto Precio Uds Importe",
                    "AB-321 TORNILLOS ACERO 2,50 4 10,00",
                    ReceiptItem("TORNILLOS ACERO", 4, Money(250)),
                ),
                Triple(
                    "Unit price Description Qty Amount",
                    "19.95 USB ADAPTER 2 39.90",
                    ReceiptItem("USB ADAPTER", 2, Money(1995)),
                ),
                Triple(
                    "Código Artículo IVA Cantidad Precio Importe",
                    "123456 VITAMINAS 10% 1 12,50 12,50",
                    ReceiptItem("VITAMINAS", 1, Money(1250)),
                ),
                Triple(
                    "Concepto Importe",
                    "REPARACION CERRADURA 85,00",
                    ReceiptItem("REPARACION CERRADURA", 1, Money(8500)),
                ),
            )
        cases.forEach { (header, row, expected) ->
            val read =
                reader.extract(
                    "PROVEEDOR EJEMPLO\nFecha 21/10/2026\n" +
                        header +
                        "\n" +
                        row +
                        "\nTOTAL 99,00 EUR"
                )
            assertEquals(header, listOf(expected), read.items)
        }
    }

    @Test
    fun ambiguousPriceColumnsKeepTheProductWithoutInventingAUnitPrice() {
        val read =
            reader.extract(
                "TIENDA\nImporte Concepto Cantidad Precio\n90,00 SILLA ROBLE 2 50,00\nTOTAL 90,00"
            )
        assertEquals(listOf(ReceiptItem("SILLA ROBLE", 2, null)), read.items)
    }

    @Test
    fun codesTaxesAndDiscountsAreNotProductNamesOrExtraInventory() {
        val read =
            reader.extract(
                "TIENDA\nRef. Producto Cantidad Precio Dto. Importe\nABC-123 TALADRO 2 50,00 10% 90,00\nGastos de envío 4,00\nTOTAL 94,00"
            )
        assertEquals(listOf(ReceiptItem("TALADRO", 2, null)), read.items)
    }

    @Test
    fun unreadableRowsAreExplicitAndNeverPreventReadingFollowingValidRows() {
        val read =
            reader.extract(
                "TIENDA\nImporte Concepto Cantidad Precio\n12,00 PINTURA 0,5 24,00\n10,00 BROCHA 1 10,00\nTOTAL 22,00"
            )
        assertEquals(listOf(ReceiptItem("BROCHA", 1, Money(1000))), read.items)
        assertEquals(1, read.unreadableTableRows)
    }

    @Test
    fun labelledIssuerSupportsNumbersWithoutSelectingCustomerName() {
        val read =
            reader.extract(
                "Factura simplificada\nProveedor: Ferretería 24\nDatos de cliente\nCLIENTE PARTICULAR\nTOTAL 10,00"
            )
        assertEquals("Ferretería 24", read.merchant?.value)
    }

    @Test
    fun unknownExtraColumnsAndDuplicateHeadersDoNotGuessAnAmount() {
        assertNull(AdaptiveReceiptTable.from("Descripción Cantidad Precio Precio"))
        assertNull(AdaptiveReceiptTable.from("Descripción Identificador secreto Importe"))
        val read =
            reader.extract(
                "TIENDA\nImporte Concepto Cantidad Precio\n10,00 PRODUCTO 1 10,00 15,00\nTOTAL 10,00"
            )
        assertTrue(read.items.isEmpty())
        assertEquals(1, read.unreadableTableRows)
    }

    @Test
    fun namedDatesAndAlternativeTotalsSupportReceiptsWithoutRetailerRules() {
        val read =
            reader.extract(
                "CAFETERIA 24\nFecha: 21 de octubre de 2026\nConcepto Importe\nDESAYUNO 8,50\nA PAGAR 8,50 EUR"
            )
        assertEquals("CAFETERIA 24", read.merchant?.value)
        assertEquals(java.time.LocalDate.of(2026, 10, 21), read.date?.value)
        assertEquals(Money(850), read.total?.value)
        assertEquals(listOf(ReceiptItem("DESAYUNO", 1, Money(850))), read.items)
        assertEquals(
            java.time.LocalDate.of(2026, 10, 21),
            reader.extract("Invoice date: 21 October 2026").date?.value,
        )
        assertNull(
            reader
                .extract("Fecha de factura: 31 de febrero de 2026\nFecha de entrega: 01/03/2026")
                .date
        )
    }

    @Test
    fun fractionalPrintedQuantitiesCannotFallBackToARoundedOrInferredInteger() {
        listOf(
                "Cantidad Concepto Importe\n0,5 PINTURA 12,00",
                "Concepto Cantidad Precio Importe\nPINTURA 0,5 24,00 48,00",
            )
            .forEach { table ->
                val read = reader.extract("TIENDA\n" + table + "\nTOTAL 12,00")
                assertTrue(table, read.items.isEmpty())
                assertEquals(table, 1, read.unreadableTableRows)
            }
    }
}
