package es.sergiotech.tickettracker

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.domain.VariantKind
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit operator-only probe. No private image is bundled or used by CI. */
class LocalImageOptimizationProbe {
    @Test
    fun optimizeProvidedLocalImage() = runTest {
        val inputPath = InstrumentationRegistry.getArguments().getString("optimizationInput")
        assumeTrue("No operator image supplied", inputPath != null)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val measurements = org.json.JSONArray()
        val store =
            PrivateDocumentStore(context) { bytes, recall, missingNumbers, psnr ->
                measurements.put(
                    JSONObject()
                        .put("bytes", bytes)
                        .put("recall", recall)
                        .put("missing_numeric_tokens", missingNumbers)
                        .put("luminance_psnr_db", psnr)
                )
            }
        val input =
            File(File(context.cacheDir, "captures").apply { mkdirs() }, "operator-input.jpg")
        File(requireNotNull(inputPath)).copyTo(input, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", input)
        val result = store.import(uri)
        try {
            val optimized =
                result.document.variants.singleOrNull { it.kind == VariantKind.OPTIMIZED }
            File(context.cacheDir, "operator-optimized.image").delete()
            if (optimized != null)
                store
                    .file(optimized)
                    .copyTo(File(context.cacheDir, "operator-optimized.image"), overwrite = true)
            File(context.cacheDir, "operator-optimized.json")
                .writeText(
                    JSONObject()
                        .apply {
                            put("source_bytes", input.length())
                            put("source_sha256", PrivateDocumentStore.sha256(input))
                            put("byte_size", optimized?.byteSize ?: JSONObject.NULL)
                            put("sha256", optimized?.sha256 ?: JSONObject.NULL)
                            put("mime_type", optimized?.mimeType ?: JSONObject.NULL)
                            put("optimization_failed", result.optimizationFailed)
                            put("ocr_failed", result.ocrFailed)
                            put("ocr_characters", result.document.extractedText.length)
                            put("measurements", measurements)
                        }
                        .toString()
                )
            check(!result.optimizationFailed)
        } finally {
            store.discardUncommitted(result.document)
            input.delete()
        }
    }
}
