package es.sergiotech.tickettracker.core.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import es.sergiotech.tickettracker.domain.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ImportResult(
    val document: ReceiptDocument,
    val extraction: ReceiptExtraction,
    val ocrFailed: Boolean,
    val optimizationFailed: Boolean = false,
    val processedPages: Int = 1,
    val totalPages: Int = 1,
    val partialRead: Boolean = false,
)

class DocumentImportException(val reason: Reason) : IOException(reason.name) {
    enum class Reason {
        TOO_LARGE,
        UNSUPPORTED,
        CORRUPT,
        LOW_SPACE,
    }
}

class PrivateDocumentStore(
    private val context: Context,
    private val nativePdfText: Boolean = true,
    private val optimizationMeasurement: ((Long, Double, Int, Double) -> Unit)? = null,
) : DocumentStore {
    private val root = File(context.filesDir, "vault").apply { mkdirs() }

    fun displayName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0)?.take(180) else null }

    fun file(variant: DocumentVariant): File {
        val candidate = File(root, variant.relativePath).canonicalFile
        require(candidate.path.startsWith(root.canonicalPath + File.separator))
        return candidate
    }

    override suspend fun verify(variant: DocumentVariant): Boolean =
        withContext(Dispatchers.IO) {
            val target = file(variant)
            target.isFile && target.length() == variant.byteSize && sha256(target) == variant.sha256
        }

    suspend fun availableVariant(document: ReceiptDocument): DocumentVariant? =
        withContext(Dispatchers.IO) {
            for (variant in
                document.variants.sortedBy { if (it.kind == VariantKind.ORIGINAL) 0 else 1 }) {
                if (variant.kind != VariantKind.THUMBNAIL && verify(variant))
                    return@withContext variant
            }
            null
        }

    /** Original remains untouched. Re-encoded cloud copies strip EXIF/location metadata. */
    suspend fun optimizedImage(document: ReceiptDocument): DocumentVariant =
        withContext(Dispatchers.IO) {
            document.variants
                .firstOrNull {
                    it.kind == VariantKind.OPTIMIZED &&
                        (it.relativePath.endsWith("cloud-v1.webp") ||
                            it.sha256 == document.sourceOriginalSha256)
                }
                ?.let { if (verify(it)) return@withContext it }
            val original = document.variants.first { it.kind == VariantKind.ORIGINAL }
            check(original.mimeType.startsWith("image/") && verify(original))
            val source = file(original)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            val bitmap = decodeImage(source, bounds)
            try {
                compressImage(document.id, original, bitmap, document.extractedText)
            } finally {
                bitmap.recycle()
            }
        }

    /** Regenerable, private device cache. It is never part of cloud metadata or the outbox. */
    suspend fun preview(document: ReceiptDocument): File? =
        withContext(Dispatchers.IO) {
            document.variants
                .firstOrNull { it.kind == VariantKind.THUMBNAIL }
                ?.let { if (verify(it)) return@withContext file(it) }
            val original = availableVariant(document) ?: return@withContext null
            val cache =
                File(
                    File(context.cacheDir, "document-previews").apply { mkdirs() },
                    "${original.sha256}.jpg",
                )
            if (cache.isFile && cache.length() > 0) return@withContext cache
            if (!verify(original)) return@withContext null
            try {
                val source = file(original)
                val bitmap =
                    if (original.mimeType == "application/pdf") pdfPreview(source)
                    else {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(source.path, bounds)
                        decodeImage(source, bounds)
                    }
                try {
                    val thumb = scaled(bitmap, 320)
                    try {
                        cache.outputStream().use {
                            check(thumb.compress(Bitmap.CompressFormat.JPEG, 80, it))
                        }
                    } finally {
                        if (thumb !== bitmap) thumb.recycle()
                    }
                } finally {
                    bitmap.recycle()
                }
                cache
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                cache.delete()
                null
            }
        }

    suspend fun saveDownloaded(variant: DocumentVariant, input: InputStream) =
        withContext(Dispatchers.IO) {
            require(variant.byteSize in 1..MAX_BYTES)
            val target = file(variant)
            target.parentFile!!.mkdirs()
            val partial = File(target.parentFile, ".${newId()}.partial")
            try {
                var size = 0L
                partial.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= variant.byteSize)
                        output.write(buffer, 0, count)
                    }
                }
                check(size == variant.byteSize && sha256(partial) == variant.sha256)
                java.nio.file.Files.move(
                    partial.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
                Unit
            } finally {
                partial.delete()
            }
        }

    suspend fun import(uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            require(uri.scheme == "content")
            if (StatFs(root.path).availableBytes < 80L * 1024 * 1024)
                throw DocumentImportException(DocumentImportException.Reason.LOW_SPACE)
            val id = newId()
            val directory = File(root, id).apply { mkdirs() }
            try {
                val original = File(directory, "original")
                context.contentResolver.openInputStream(uri)?.use { source ->
                    original.outputStream().use { target ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BYTES)
                                throw DocumentImportException(
                                    DocumentImportException.Reason.TOO_LARGE
                                )
                            target.write(buffer, 0, read)
                        }
                        if (total == 0L)
                            throw DocumentImportException(DocumentImportException.Reason.CORRUPT)
                    }
                } ?: throw DocumentImportException(DocumentImportException.Reason.CORRUPT)

                val pdf =
                    original.inputStream().use { stream ->
                        val header = ByteArray(5)
                        stream.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
                    }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (!pdf) BitmapFactory.decodeFile(original.path, bounds)
                val mime = if (pdf) "application/pdf" else bounds.outMimeType
                if (
                    mime !in
                        setOf(
                            "application/pdf",
                            "image/jpeg",
                            "image/png",
                            "image/webp",
                            "image/heif",
                            "image/heic",
                            "image/avif",
                        )
                ) {
                    throw DocumentImportException(DocumentImportException.Reason.UNSUPPORTED)
                }
                val variants = mutableListOf(variant(id, original, VariantKind.ORIGINAL, mime!!))
                val bitmap = if (pdf) pdfPreview(original) else decodeImage(original, bounds)
                var ocrFailed = false
                var optimizationFailed = false
                var pdfReading: PdfReading? = null
                val text: String
                try {
                    val recognized =
                        try {
                            if (pdf) {
                                pdfReading = readPdf(original)
                                pdfReading!!.text
                            } else recognize(bitmap)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            ""
                        }
                    text = recognized
                    ocrFailed = text.isBlank()
                    val thumb = scaled(bitmap, 320)
                    try {
                        val thumbnailFile = File(directory, "thumbnail.jpg")
                        thumbnailFile.outputStream().use {
                            check(thumb.compress(Bitmap.CompressFormat.JPEG, 80, it))
                        }
                        variants += variant(id, thumbnailFile, VariantKind.THUMBNAIL, "image/jpeg")
                    } finally {
                        if (thumb !== bitmap) thumb.recycle()
                    }

                    if (!pdf) {
                        try {
                            variants += compressImage(id, variants.first(), bitmap, text)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            optimizationFailed = true
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
                ImportResult(
                    ReceiptDocument(
                        id = id,
                        displayName = "${if (pdf) "PDF" else "Ticket"} · ${id.take(8)}",
                        variants = variants,
                        extractedText = text,
                    ),
                    ReceiptExtractor().extract(text),
                    ocrFailed,
                    optimizationFailed,
                    pdfReading?.processed ?: 1,
                    pdfReading?.total ?: 1,
                    pdfReading?.partial ?: false,
                )
            } catch (failure: Throwable) {
                directory.deleteRecursively()
                throw failure
            }
        }

    /** Only for an uncommitted import created by this instance; never used for cache eviction. */
    suspend fun discardUncommitted(document: ReceiptDocument) =
        withContext(Dispatchers.IO) {
            require(runCatching { java.util.UUID.fromString(document.id) }.isSuccess)
            File(root, document.id).deleteRecursively()
            Unit
        }

    private fun pdfPreview(file: File): Bitmap =
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount !in 1..500)
                        throw DocumentImportException(DocumentImportException.Reason.CORRUPT)
                    renderer.openPage(0).use { page ->
                        val scale = 2000f / maxOf(page.width, page.height)
                        Bitmap.createBitmap(
                                maxOf(1, (page.width * scale).toInt()),
                                maxOf(1, (page.height * scale).toInt()),
                                Bitmap.Config.ARGB_8888,
                            )
                            .also {
                                it.eraseColor(android.graphics.Color.WHITE)
                                page.render(
                                    it,
                                    null,
                                    null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                )
                            }
                    }
                }
            }
        } catch (failure: Exception) {
            throw DocumentImportException(DocumentImportException.Reason.CORRUPT).also {
                it.initCause(failure)
            }
        }

    private data class PdfReading(
        val text: String,
        val processed: Int,
        val total: Int,
        val partial: Boolean,
    )

    private suspend fun readPdf(file: File): PdfReading =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val text = StringBuilder()
                val count = minOf(MAX_PDF_PAGES, renderer.pageCount)
                var processed = 0
                var partial = renderer.pageCount > count
                for (index in 0 until count) {
                    currentCoroutineContext().ensureActive()
                    val pageText =
                        renderer.openPage(index).use { page ->
                            val embedded =
                                if (nativePdfText && android.os.Build.VERSION.SDK_INT >= 35)
                                    pdfText(page)
                                else ""
                            val recognized =
                                try {
                                    val scale =
                                        minOf(
                                            3200f / maxOf(page.width, page.height),
                                            kotlin.math.sqrt(
                                                8_000_000f / (page.width.toLong() * page.height)
                                            ),
                                        )
                                    val bitmap =
                                        Bitmap.createBitmap(
                                            maxOf(1, (page.width * scale).toInt()),
                                            maxOf(1, (page.height * scale).toInt()),
                                            Bitmap.Config.ARGB_8888,
                                        )
                                    try {
                                        bitmap.eraseColor(Color.WHITE)
                                        page.render(
                                            bitmap,
                                            null,
                                            null,
                                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                        )
                                        recognize(bitmap)
                                    } finally {
                                        bitmap.recycle()
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    ""
                                }
                            // On equal completeness, digital text preserves spelling and
                            // references.
                            listOf(embedded, recognized)
                                .filter(String::isNotBlank)
                                .maxByOrNull(::extractionScore)
                                .orEmpty()
                        }
                    processed++
                    if (pageText.isBlank()) partial = true
                    if (text.isNotEmpty()) text.append("\n\n")
                    text.append(pageText)
                    if (text.length >= 100_000) {
                        partial = true
                        break
                    }
                }
                PdfReading(text.toString().take(100_000), processed, renderer.pageCount, partial)
            }
        }

    @androidx.annotation.RequiresApi(35)
    private fun pdfText(page: PdfRenderer.Page): String =
        try {
            page.textContents.joinToString("\n") { it.text }.take(100_000)
        } catch (_: Exception) {
            ""
        }

    private fun extractionScore(text: String): Int {
        val extraction = ReceiptExtractor().extract(text)
        return (if (extraction.merchant != null) 3 else 0) +
            (if (extraction.date != null) 5 else 0) +
            (if (extraction.total != null) 8 else 0) +
            minOf(30, extraction.items.size)
    }

    /**
     * Word positions preserve table rows across OCR blocks. Small screenshots need larger glyphs.
     */
    private suspend fun recognize(source: Bitmap): String {
        val scale =
            minOf(
                    3f,
                    1000f / minOf(source.width, source.height),
                    kotlin.math.sqrt(8_000_000f / (source.width.toLong() * source.height)),
                    12000f / maxOf(source.width, source.height),
                )
                .coerceAtLeast(1f)
        val bitmap =
            if (scale > 1f)
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt(),
                    (source.height * scale).toInt(),
                    true,
                )
            else source
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val boxes =
                result.textBlocks
                    .flatMap { it.lines }
                    .flatMap { line ->
                        line.elements.mapNotNull { word ->
                            word.boundingBox?.let {
                                ReceiptTextBox(
                                    word.text,
                                    it.left.toFloat(),
                                    it.top.toFloat(),
                                    it.right.toFloat(),
                                    it.bottom.toFloat(),
                                )
                            }
                        }
                    }
            val physical = ReceiptLayout.readingText(boxes).ifBlank { result.text }
            val labelled = ReceiptLabelLayout.readingText(boxes)
            return listOf(physical, labelled)
                .filter(String::isNotBlank)
                .joinToString("\n")
                .take(100_000)
        } finally {
            recognizer.close()
            if (bitmap !== source) bitmap.recycle()
        }
    }

    private fun decodeImage(file: File, bounds: BitmapFactory.Options): Bitmap {
        if (
            bounds.outWidth <= 0 ||
                bounds.outHeight <= 0 ||
                bounds.outWidth.toLong() * bounds.outHeight > 400_000_000
        ) {
            throw DocumentImportException(DocumentImportException.Reason.CORRUPT)
        }
        var sample = 1
        while (
            bounds.outWidth.toLong() * bounds.outHeight / sample / sample > 12_000_000 ||
                maxOf(bounds.outWidth, bounds.outHeight) / sample > 12000
        ) sample *= 2
        val bitmap =
            BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: throw DocumentImportException(DocumentImportException.Reason.CORRUPT)
        val orientation =
            ExifInterface(file)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix =
            Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        setRotate(90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        setRotate(270f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun scaled(bitmap: Bitmap, edge: Int): Bitmap {
        val scale = minOf(1f, edge.toFloat() / maxOf(bitmap.width, bitmap.height))
        return Bitmap.createScaledBitmap(
            bitmap,
            maxOf(1, (bitmap.width * scale).toInt()),
            maxOf(1, (bitmap.height * scale).toInt()),
            true,
        )
    }

    private suspend fun compressImage(
        id: String,
        original: DocumentVariant,
        source: Bitmap,
        sourceText: String,
    ): DocumentVariant {
        val target = File(file(original).parentFile, "cloud-v1.webp")
        val tokens =
            sourceText
                .uppercase(java.util.Locale.ROOT)
                .split(Regex("[^\\p{L}\\p{N}.,/-]+"))
                .filter { it.length >= 2 }
                .toSet()
        val numbers = tokens.filter { it.any(Char::isDigit) }.toSet()
        val expected = ReceiptExtractor().extract(sourceText)
        for ((edge, quality) in
            listOf(2400 to 84, 12000 to 78, 12000 to 84, 3600 to 90, 12000 to 94, 12000 to 100)) {
            currentCoroutineContext().ensureActive()
            val ratio =
                minOf(
                    1f,
                    maxOf(
                        edge.toFloat() / maxOf(source.width, source.height),
                        1000f / minOf(source.width, source.height),
                    ),
                )
            val width = maxOf(1, (source.width * ratio).toInt())
            val height = maxOf(1, (source.height * ratio).toInt())
            val candidate = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                candidate.eraseColor(Color.WHITE)
                Canvas(candidate)
                    .drawBitmap(
                        source,
                        null,
                        android.graphics.Rect(0, 0, width, height),
                        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                    )
                // Lossless is a final option for compact digital receipts: even a tiny
                // lossy glyph change can move an otherwise valid table outside the OCR gate.
                val format =
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        if (quality == 100) Bitmap.CompressFormat.WEBP_LOSSLESS
                        else Bitmap.CompressFormat.WEBP_LOSSY
                    } else Bitmap.CompressFormat.WEBP
                target.outputStream().use { check(candidate.compress(format, quality, it)) }
            } finally {
                candidate.recycle()
            }
            // A small input can already be more efficient (e.g. monochrome PNG). Reuse its
            // exact bytes instead of enlarging or recompressing it again.
            if (
                target.length() >= original.byteSize && original.byteSize <= MAX_CLOUD_IMAGE_BYTES
            ) {
                target.delete()
                return original.copy(id = newId(), kind = VariantKind.OPTIMIZED)
            }
            if (target.length() !in 1..MAX_CLOUD_IMAGE_BYTES || target.length() > original.byteSize)
                continue
            val decoded = BitmapFactory.decodeFile(target.path) ?: continue
            val psnr = imagePsnr(source, decoded)
            val readBack =
                try {
                    recognize(decoded)
                } finally {
                    decoded.recycle()
                }
            // OCR can split the same price/date/code at different spaces or punctuation.
            val recovered =
                readBack.uppercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
            fun retained(token: String) =
                token.replace(Regex("[^\\p{L}\\p{N}]"), "").let {
                    it.isNotEmpty() && it in recovered
                }
            val recall =
                if (tokens.isEmpty()) 1.0 else tokens.count(::retained).toDouble() / tokens.size
            optimizationMeasurement?.invoke(
                target.length(),
                recall,
                numbers.count { !retained(it) },
                psnr,
            )
            val actual = ReceiptExtractor().extract(readBack)
            val criticalFieldsRetained =
                (expected.total == null || expected.total?.value == actual.total?.value) &&
                    (expected.date == null || expected.date?.value == actual.date?.value) &&
                    expected.deadlines.all { before ->
                        actual.deadlines.any { it.kind == before.kind && it.date == before.date }
                    } &&
                    expected.items
                        .mapNotNull { it.unitPrice }
                        .all { price -> actual.items.any { it.unitPrice == price } }
            val numericRecall =
                if (numbers.isEmpty()) 1.0 else numbers.count(::retained).toDouble() / numbers.size
            // With very high visual fidelity, OCR noise must not force raw-photo uploads.
            // Critical extracted values still have to survive; no legal date is inferred.
            if (
                psnr >= 36.0 &&
                    (psnr >= 39.0 || (recall >= .90 && numericRecall >= .90)) &&
                    criticalFieldsRetained
            )
                return variant(id, target, VariantKind.OPTIMIZED, "image/webp")
        }
        target.delete()
        throw java.io.IOException("image_optimization_failed")
    }

    /** Luminance PSNR at a common 1600px view, independent of OCR layout differences. */
    private fun imagePsnr(source: Bitmap, candidate: Bitmap): Double {
        val reference = scaled(source, 1600)
        val result = Bitmap.createScaledBitmap(candidate, reference.width, reference.height, true)
        try {
            val before = IntArray(reference.width)
            val after = IntArray(reference.width)
            var squaredError = 0.0
            for (y in 0 until reference.height) {
                reference.getPixels(before, 0, reference.width, 0, y, reference.width, 1)
                result.getPixels(after, 0, result.width, 0, y, result.width, 1)
                for (x in before.indices) {
                    fun luma(pixel: Int) =
                        (.299 * Color.red(pixel) +
                            .587 * Color.green(pixel) +
                            .114 * Color.blue(pixel))
                    val delta = luma(before[x]) - luma(after[x])
                    squaredError += delta * delta
                }
            }
            val mse = squaredError / (reference.width.toLong() * reference.height)
            return if (mse == 0.0) 100.0 else 10 * kotlin.math.log10(255.0 * 255.0 / mse)
        } finally {
            if (result !== candidate && result !== source && result !== reference) result.recycle()
            if (reference !== source) reference.recycle()
        }
    }

    private fun variant(id: String, file: File, kind: VariantKind, mime: String) =
        DocumentVariant(
            kind = kind,
            relativePath = "$id/${file.name}",
            mimeType = mime,
            byteSize = file.length(),
            sha256 = sha256(file),
        )

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        const val MAX_CLOUD_IMAGE_BYTES = 2L * 1024 * 1024
        const val MAX_PDF_PAGES = 10

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
