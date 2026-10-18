package es.sergiotech.tickettracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.sergiotech.tickettracker.core.documents.DocumentImportException
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.domain.*
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentPipelineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = PrivateDocumentStore(context)

    private fun temp(suffix: String): File =
        File(File(context.cacheDir, "captures").apply { mkdirs() }, "test-${newId()}.$suffix")

    private fun uri(file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.documents", file)

    @Test
    fun imageOcrRecognizesTotalAndRetainsOriginalBytes() = runTest {
        val input = temp("png")
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 60f
                    typeface = android.graphics.Typeface.MONOSPACE
                }
            drawText("TIENDA EJEMPLO", 60f, 150f, paint)
            drawText("17/10/2026", 60f, 270f, paint)
            drawText("Monitor 39,95", 60f, 380f, paint)
            drawText("TOTAL 39,95", 60f, 490f, paint)
            paint.textSize = 38f
            drawText("Devoluciones hasta 17/11/2026", 60f, 620f, paint)
        }
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val result = store.import(uri(input))
            try {
                assertFalse(result.ocrFailed)
                assertTrue(result.document.extractedText.contains("39,95"))
                assertEquals(Money(3995), result.extraction.total?.value)
                assertEquals(
                    listOf(ReceiptItem("Monitor", 1, Money(3995))),
                    result.extraction.items,
                )
                assertEquals(
                    java.time.LocalDate.of(2026, 11, 17),
                    result.extraction.deadlines.single().date,
                )
                val original = result.document.variants.first { it.kind == VariantKind.ORIGINAL }
                assertEquals(PrivateDocumentStore.sha256(input), original.sha256)
                assertTrue(store.verify(original))
                assertTrue(result.document.variants.any { it.kind == VariantKind.THUMBNAIL })
                assertFalse(result.document.variants.any { it.kind == VariantKind.OPTIMIZED })
                val restored = result.document.copy(variants = listOf(original))
                val preview = store.preview(restored)
                assertNotNull(preview)
                assertTrue(preview!!.length() < original.byteSize)
                assertTrue(store.verify(original))
                preview.delete()
            } finally {
                store.discardUncommitted(result.document)
            }
        } finally {
            input.delete()
        }
    }

    @Test
    fun pdfIsPreservedWithoutRasterizingItsStoredOriginal() = runTest {
        val input = temp("pdf")
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
            page.canvas.drawText("TOTAL 12,50", 40f, 100f, Paint().apply { textSize = 30f })
            pdf.finishPage(page)
            input.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
        try {
            val result = store.import(uri(input))
            try {
                val original = result.document.variants.first { it.kind == VariantKind.ORIGINAL }
                assertEquals("application/pdf", original.mimeType)
                assertArrayEquals(input.readBytes(), store.file(original).readBytes())
                assertFalse(result.document.variants.any { it.kind == VariantKind.OPTIMIZED })
            } finally {
                store.discardUncommitted(result.document)
            }
        } finally {
            input.delete()
        }
    }

    @Test
    fun corruptAndOversizedFilesAreRejected() = runTest {
        val corrupt = temp("jpg").apply { writeText("not a receipt") }
        val large = temp("pdf")
        RandomAccessFile(large, "rw").use { it.setLength(PrivateDocumentStore.MAX_BYTES + 1) }
        try {
            try {
                store.import(uri(corrupt))
                fail("Corrupt file accepted")
            } catch (_: DocumentImportException) {}
            try {
                store.import(uri(large))
                fail("Oversized file accepted")
            } catch (failure: DocumentImportException) {
                assertEquals(DocumentImportException.Reason.TOO_LARGE, failure.reason)
            }
        } finally {
            corrupt.delete()
            large.delete()
        }
    }
}
