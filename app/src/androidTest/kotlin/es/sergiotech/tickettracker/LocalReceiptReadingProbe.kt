package es.sergiotech.tickettracker

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit local validation only; the input image and OCR never become CI fixtures. */
class LocalReceiptReadingProbe {
    @Test
    fun readProvidedReceipt() = runTest {
        val arguments = InstrumentationRegistry.getArguments()
        val path = arguments.getString("receiptInput")
        assumeTrue("No private receipt supplied", path != null)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store =
            PrivateDocumentStore(
                context,
                nativePdfText = arguments.getString("receiptOcrOnly") != "true",
            )
        val file =
            File(File(context.cacheDir, "captures").apply { mkdirs() }, "receipt-probe.image")
        File(requireNotNull(path)).copyTo(file, overwrite = true)
        val result =
            store.import(
                FileProvider.getUriForFile(context, "${context.packageName}.documents", file)
            )
        try {
            val extraction = result.extraction
            File(context.cacheDir, "receipt-probe.json")
                .writeText(
                    JSONObject()
                        .apply {
                            put("merchant", extraction.merchant?.value ?: JSONObject.NULL)
                            put("date", extraction.date?.value?.toString() ?: JSONObject.NULL)
                            put(
                                "total_minor",
                                extraction.total?.value?.minorUnits ?: JSONObject.NULL,
                            )
                            put(
                                "items",
                                JSONArray().apply {
                                    extraction.items.forEach {
                                        put(
                                            JSONObject()
                                                .put("name", it.name)
                                                .put("quantity", it.quantity)
                                                .put(
                                                    "unit_minor",
                                                    it.unitPrice?.minorUnits ?: JSONObject.NULL,
                                                )
                                        )
                                    }
                                },
                            )
                            put("ocr_failed", result.ocrFailed)
                            put("optimization_failed", result.optimizationFailed)
                            put("reference", extraction.reference?.value ?: JSONObject.NULL)
                            put(
                                "delivered_on",
                                extraction.deliveredOn?.value?.toString() ?: JSONObject.NULL,
                            )
                            put("processed_pages", result.processedPages)
                            put("total_pages", result.totalPages)
                            put("partial_read", result.partialRead)
                            put("text", result.document.extractedText)
                        }
                        .toString()
                )
            arguments.getString("expectedMerchant")?.let {
                assertEquals(it, extraction.merchant?.value)
            }
            arguments.getString("expectedDate")?.let {
                assertEquals(it, extraction.date?.value?.toString())
            }
            arguments.getString("expectedTotal")?.let {
                assertEquals(it.toLong(), extraction.total?.value?.minorUnits)
            }
            arguments.getString("expectedItems")?.let {
                assertEquals(it.toInt(), extraction.items.size)
            }
            arguments.getString("expectedItemContains")?.let {
                assertTrue(extraction.items.any { item -> item.name.contains(it, true) })
            }
            arguments.getString("expectedUnit")?.let {
                assertEquals(it.toLong(), extraction.items.single().unitPrice?.minorUnits)
            }
            assertFalse(result.ocrFailed)
        } finally {
            store.discardUncommitted(result.document)
            file.delete()
        }
    }
}
