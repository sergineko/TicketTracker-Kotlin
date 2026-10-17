package es.sergiotech.tickettracker.data

import android.content.Context
import android.net.Uri
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.domain.CoverageRepository
import es.sergiotech.tickettracker.domain.IdentityGateway
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
    private val coverage: CoverageRepository,
    private val identity: IdentityGateway,
) {
    suspend fun export(uri: Uri) =
        withContext(Dispatchers.IO) {
            val owner = identity.identity.first().id
            val purchases = repository.observePurchases().first()
            val documents = repository.observeDocuments().first()
            val protection = coverage.observe().first()
            check(
                purchases.all { it.ownerId == owner } &&
                    documents.all { it.ownerId == owner } &&
                    protection.ownerId == owner
            )
            val manifest =
                JSONObject().apply {
                    put("format", "tickettracker-export")
                    put("version", 2)
                    put("exported_at", java.time.Instant.now())
                    put("purchases", JSONArray().apply { purchases.forEach { put(it.json()) } })
                    put("documents", JSONArray().apply { documents.forEach { put(it.json()) } })
                    put(
                        "contracts",
                        JSONArray().apply {
                            protection.contracts.forEach { c ->
                                put(
                                    JSONObject().apply {
                                        put("id", c.id)
                                        put("owner_id", c.ownerId)
                                        put("kind", c.kind.name)
                                        put("name", c.name)
                                        put("provider", c.provider)
                                        put("amount_minor", c.amount.minorUnits)
                                        put("currency", c.amount.currency)
                                        put("period", c.period.name)
                                        put("next_date", c.nextDate.toString())
                                        put("active", c.active)
                                        put("revision", c.revision)
                                    }
                                )
                            }
                        },
                    )
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
