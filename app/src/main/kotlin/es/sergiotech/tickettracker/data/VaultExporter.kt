package es.sergiotech.tickettracker.data

import android.content.Context
import android.net.Uri
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.domain.PurchaseRepository
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The manifest and byte hashes make exports portable; they are sensitive and intentionally
 * unencrypted.
 */
class VaultExporter(
    private val context: Context,
    private val repository: PurchaseRepository,
    private val store: PrivateDocumentStore,
) {
    suspend fun export(uri: Uri) =
        withContext(Dispatchers.IO) {
            val purchases = repository.observePurchases().first()
            val documents = repository.observeDocuments().first()
            val manifest =
                JSONObject().apply {
                    put("format", "tickettracker-export")
                    put("version", 1)
                    put("exported_at", java.time.Instant.now())
                    put("purchases", JSONArray().apply { purchases.forEach { put(it.json()) } })
                    put("documents", JSONArray().apply { documents.forEach { put(it.json()) } })
                }
            ZipOutputStream(requireNotNull(context.contentResolver.openOutputStream(uri, "wt")))
                .use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(manifest.toString(2).toByteArray())
                    zip.closeEntry()
                    documents
                        .flatMap { it.variants }
                        .forEach { variant ->
                            currentCoroutineContext().ensureActive()
                            check(store.verify(variant)) { "Document integrity check failed" }
                            zip.putNextEntry(ZipEntry("documents/${variant.relativePath}"))
                            store.file(variant).inputStream().use { input ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    zip.write(buffer, 0, n)
                                }
                            }
                            zip.closeEntry()
                        }
                }
        }
}
