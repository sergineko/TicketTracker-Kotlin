package es.sergiotech.tickettracker.core.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
)

class DocumentImportException(val reason: Reason) : IOException(reason.name) {
    enum class Reason {
        TOO_LARGE,
        UNSUPPORTED,
        CORRUPT,
        LOW_SPACE,
    }
}

class PrivateDocumentStore(private val context: Context) : DocumentStore {
    private val root = File(context.filesDir, "vault").apply { mkdirs() }

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

    /** Regenerable, private device cache. It is never part of cloud metadata or the outbox. */
    suspend fun preview(document: ReceiptDocument): File? =
        withContext(Dispatchers.IO) {
            document.variants
                .firstOrNull { it.kind == VariantKind.THUMBNAIL }
                ?.let { if (verify(it)) return@withContext file(it) }
            val original = document.variants.first { it.kind == VariantKind.ORIGINAL }
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
                val text: String
                try {
                    text =
                        try {
                            val recognizer =
                                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            try {
                                recognizer
                                    .process(InputImage.fromBitmap(bitmap, 0))
                                    .await()
                                    .text
                                    .take(100_000)
                            } finally {
                                recognizer.close()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            ocrFailed = true
                            ""
                        }

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

                    // Keep one untouched original and a small device-only preview. No second
                    // full-size JPEG: it was unused and consumed both local and remote space.
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

    private fun decodeImage(file: File, bounds: BitmapFactory.Options): Bitmap {
        if (
            bounds.outWidth <= 0 ||
                bounds.outHeight <= 0 ||
                bounds.outWidth.toLong() * bounds.outHeight > 400_000_000
        ) {
            throw DocumentImportException(DocumentImportException.Reason.CORRUPT)
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2600) sample *= 2
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
