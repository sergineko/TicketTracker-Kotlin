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
}
