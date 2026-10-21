package es.sergiotech.tickettracker

import es.sergiotech.tickettracker.domain.*
import es.sergiotech.tickettracker.ui.*
import org.junit.Assert.*
import org.junit.Test

class OcrDraftTest {
    private val extraction =
        ReceiptExtractor()
            .extract(
                "TIENDA\nFecha 18/10/2026\nMonitor 39,95\nTOTAL 39,95 EUR\nDevoluciones hasta 17/11/2026"
            )

    @Test
    fun recognizedItemsFillAnEmptyDraftAndDeadlinesRequireAChoice() {
        val draft = EditorState().withExtraction(extraction)
        assertEquals("TIENDA", draft.merchant)
        assertEquals("39.95", draft.total)
        assertEquals("Monitor", draft.lines.single().name)
        assertEquals("39.95", draft.lines.single().price)
        assertEquals("", draft.lines.single().returnEnd)
        assertEquals(1, draft.printedDeadlines.size)
    }

    @Test
    fun missingDateDoesNotPretendTodayWasRecognizedAndExplicitUserDateWins() {
        assertEquals("", EditorState().withExtraction(ReceiptExtraction()).date)
        val typed = EditorState(date = java.time.LocalDate.now().toString(), dateEdited = true)
        assertEquals(typed.date, typed.withExtraction(extraction).date)
    }

    @Test
    fun labelledReferenceAndDeliveryFillOnlyEmptyFields() {
        val read =
            ReceiptExtractor()
                .extract(
                    "TIENDA\nFecha de factura 21.08.2026\nPedido online número\nP-9876\nFecha de entrega\n23.08.2026\nTOTAL 20,00"
                )
        val empty = EditorState().withExtraction(read)
        assertEquals("P-9876", empty.order)
        assertEquals("2026-08-23", empty.delivered)
        val typed = EditorState(order = "Escrita", delivered = "2026-09-01").withExtraction(read)
        assertEquals("Escrita", typed.order)
        assertEquals("2026-09-01", typed.delivered)
    }

    @Test
    fun importsPreserveTypedItemsPricesCurrencyAndDeadlines() {
        val line =
            LineDraft(name = "Escrito por el usuario", price = "5.00", warrantyEnd = "2028-10-18")
        val old =
            EditorState(
                merchant = "Mi tienda",
                date = "2026-09-01",
                total = "5.00",
                currency = "GBP",
                lines = listOf(line),
            )
        val draft = old.withExtraction(extraction)
        assertEquals(old.merchant, draft.merchant)
        assertEquals(old.date, draft.date)
        assertEquals(old.total, draft.total)
        assertEquals(old.currency, draft.currency)
        assertEquals(old.lines, draft.lines)
    }

    @Test
    fun unreadableRowsRemainVisibleInTheReviewAndNeverOverwriteTypedLines() {
        val read =
            ReceiptExtractor()
                .extract(
                    "TIENDA\nImporte Concepto Cantidad Precio\n12,00 PINTURA 0,5 24,00\n10,00 BROCHA 1 10,00\nTOTAL 22,00"
                )
        val typed = EditorState(lines = listOf(LineDraft(name = "Escrito")))
        val result = typed.withExtraction(read)
        assertTrue(result.unreadableRows)
        assertEquals(typed.lines, result.lines)
    }

    @Test
    fun validationAndSumsKeepDatesCivilAndTotalsIndependent() {
        val draft =
            EditorState(
                merchant = "Tienda",
                date = "21/10/2026",
                total = "12.00",
                delivered = "20/10/2026",
                lines = listOf(LineDraft(name = "Cable", quantity = "2", price = "5.00")),
            )
        assertEquals(java.time.LocalDate.of(2026, 10, 21), editorDate(draft.date))
        assertNull(editorDate("31/02/2026"))
        assertEquals(Money(1000), draft.lineSum())
        assertEquals("12.00", draft.total)
        assertEquals(listOf(DraftProblem(DraftField.DELIVERY)), draft.problems())
        assertNull(draft.copy(lines = listOf(LineDraft(name = "Cable"))).lineSum())
    }
}
