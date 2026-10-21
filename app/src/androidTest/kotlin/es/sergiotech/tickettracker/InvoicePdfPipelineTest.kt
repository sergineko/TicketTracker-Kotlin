package es.sergiotech.tickettracker

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.domain.*
import java.io.File
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InvoicePdfPipelineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun pdf(pages: List<List<String>>): File {
        val file =
            File(File(context.cacheDir, "captures").apply { mkdirs() }, "invoice-${newId()}.pdf")
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, lines ->
                val page =
                    document.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 16f
                        typeface = Typeface.MONOSPACE
                    }
                lines.forEachIndexed { row, line ->
                    page.canvas.drawText(line, 30f, 40f + row * 35f, paint)
                }
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }

    private fun uri(file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.documents", file)

    @Test
    fun twoPageInvoiceReadsAllProductsAndKeepsTheOriginalInDigitalAndOcrModes() =
        runTest(timeout = 120.seconds) {
            val header = "Descripcion Cant. P.Unit Total"
            val source =
                pdf(
                    listOf(
                        listOf(
                            "TECNICA EJEMPLO",
                            "Fecha de factura: 21.08.2026",
                            "Pedido: P-9876",
                            header,
                            "AURICULARES 2 50,00 100,00",
                            "Subtotal 100,00",
                        ),
                        listOf(
                            "Fecha de entrega: 23.08.2026",
                            header,
                            "FUNDA 1 10,00 10,00",
                            "TOTAL FACTURA 110,00 EUR",
                        ),
                    )
                )
            try {
                for (native in
                    if (android.os.Build.VERSION.SDK_INT >= 35) listOf(true, false)
                    else listOf(false)) {
                    val store = PrivateDocumentStore(context, nativePdfText = native)
                    val result = store.import(uri(source))
                    try {
                        assertFalse(result.ocrFailed)
                        assertFalse(result.partialRead)
                        assertEquals(2, result.processedPages)
                        assertEquals(2, result.totalPages)
                        assertEquals("TECNICA EJEMPLO", result.extraction.merchant?.value)
                        assertEquals(
                            "native=$native\n${result.document.extractedText}",
                            LocalDate.of(2026, 8, 21),
                            result.extraction.date?.value,
                        )
                        assertEquals(Money(11000), result.extraction.total?.value)
                        assertEquals(
                            listOf(
                                ReceiptItem("AURICULARES", 2, Money(5000)),
                                ReceiptItem("FUNDA", 1, Money(1000)),
                            ),
                            result.extraction.items,
                        )
                        assertEquals("P-9876", result.extraction.reference?.value)
                        assertEquals(
                            LocalDate.of(2026, 8, 23),
                            result.extraction.deliveredOn?.value,
                        )
                        val original =
                            result.document.variants.single { it.kind == VariantKind.ORIGINAL }
                        assertArrayEquals(source.readBytes(), store.file(original).readBytes())
                    } finally {
                        store.discardUncommitted(result.document)
                    }
                }
            } finally {
                source.delete()
            }
        }

    @Test
    fun pageLimitIsExplicitAndNeverRemovesUnreadPagesFromTheStoredPdf() =
        runTest(timeout = 120.seconds) {
            val source =
                pdf(
                    (1..11).map { index ->
                        if (index == 1) listOf("TECNICA EJEMPLO", "Fecha 21.08.2026", "TOTAL 10,00")
                        else if (index == 11) listOf("TOTAL FACTURA 999,00") else emptyList()
                    }
                )
            val store = PrivateDocumentStore(context)
            try {
                val result = store.import(uri(source))
                try {
                    assertEquals(10, result.processedPages)
                    assertEquals(11, result.totalPages)
                    assertTrue(result.partialRead)
                    assertEquals(Money(1000), result.extraction.total?.value)
                    val original =
                        result.document.variants.single { it.kind == VariantKind.ORIGINAL }
                    assertArrayEquals(source.readBytes(), store.file(original).readBytes())
                } finally {
                    store.discardUncommitted(result.document)
                }
            } finally {
                source.delete()
            }
        }

    @Test
    fun reorderedServiceColumnsAreReadThroughTheRealPdfPipeline() =
        runTest(timeout = 120.seconds) {
            val source =
                pdf(
                    listOf(
                        listOf(
                            "SERVICIOS EJEMPLO",
                            "Fecha: 21 octubre 2026",
                            "Importe Concepto Cantidad Precio",
                            "40,00 REVISION BICICLETA 2 20,00",
                            "A PAGAR 40,00 EUR",
                        )
                    )
                )
            val store = PrivateDocumentStore(context, nativePdfText = false)
            try {
                val result = store.import(uri(source))
                try {
                    assertEquals(LocalDate.of(2026, 10, 21), result.extraction.date?.value)
                    assertEquals(Money(4000), result.extraction.total?.value)
                    assertEquals(
                        listOf(ReceiptItem("REVISION BICICLETA", 2, Money(2000))),
                        result.extraction.items,
                    )
                    assertFalse(result.ocrFailed)
                } finally {
                    store.discardUncommitted(result.document)
                }
            } finally {
                source.delete()
            }
        }
}
