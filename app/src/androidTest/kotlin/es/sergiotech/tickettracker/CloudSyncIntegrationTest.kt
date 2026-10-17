package es.sergiotech.tickettracker

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.sergiotech.tickettracker.core.database.TicketDatabase
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.data.LocalPreferences
import es.sergiotech.tickettracker.data.RoomPurchaseRepository
import es.sergiotech.tickettracker.data.cloud.*
import es.sergiotech.tickettracker.domain.*
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSyncIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: TicketDatabase
    private lateinit var sessions: CloudSessionStore
    private lateinit var repository: RoomPurchaseRepository
    private lateinit var store: PrivateDocumentStore
    private lateinit var api: FakeRemote
    private lateinit var sync: CloudflareSyncController
    private val owner = newId()
    private val session
        get() = CloudSession(owner, "sync@example.test", "test-session-opaque", 4102444800)

    @Before
    fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.cacheDir, "cloud-test-${newId()}").apply { mkdirs() }
        context =
            object : ContextWrapper(base) {
                override fun getFilesDir() = directory
            }
        db = Room.inMemoryDatabaseBuilder(context, TicketDatabase::class.java).build()
        sessions = CloudSessionStore(context, db, LocalPreferences(base))
        repository = RoomPurchaseRepository(db, sessions)
        store = PrivateDocumentStore(context)
        api = FakeRemote()
        sync = CloudflareSyncController(db, api, sessions, store)
    }

    @After
    fun close() {
        db.close()
        context.filesDir.deleteRecursively()
    }

    private fun purchase() =
        Purchase(
            merchant = "Local",
            purchasedOn = LocalDate.of(2026, 10, 17),
            total = Money(3995),
            lines = listOf(PurchaseLine(product = Product(name = "Monitor"))),
        )

    private fun emptyChanges() =
        JSONObject().put("changes", JSONArray()).put("cursor", 0).put("has_more", false)

    private suspend fun remotePurchase(): JSONObject {
        repository.save(purchase())
        val payload = JSONObject(db.tickets().outbox(sessions.identity.first().id).single().payload)
        payload.put("owner_id", owner)
        return payload
    }

    @Test
    fun sessionEncryptionAndAdoptionKeepAccountsSeparate() = runTest {
        repository.save(purchase())
        val anonymous = sessions.identity.first().id
        sessions.activate(session)
        assertTrue(db.tickets().outbox(anonymous).isEmpty())
        assertEquals(owner, repository.observePurchases().first().single().ownerId)
        assertFalse(
            String(File(context.filesDir, "session.enc").readBytes()).contains(session.token)
        )
        val reopened =
            CloudSessionStore(
                context,
                db,
                LocalPreferences(ApplicationProvider.getApplicationContext()),
            )
        assertEquals(session, reopened.current())
        sessions.logoutLocally()
        assertTrue(repository.observePurchases().first().isEmpty())
        sessions.activate(session.copy(userId = newId()))
        assertTrue(repository.observePurchases().first().isEmpty())
        assertEquals(1, db.tickets().outbox(owner).size)
    }

    @Test
    fun matchingHashesFromSeparateVaultsSurviveAccountAdoption() = runTest {
        val bytes = "same original imported twice".toByteArray()
        val hash =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        suspend fun importOriginal(): ReceiptDocument {
            val id = newId()
            val variant =
                DocumentVariant(
                    kind = VariantKind.ORIGINAL,
                    relativePath = "$id/original",
                    mimeType = "application/pdf",
                    byteSize = bytes.size.toLong(),
                    sha256 = hash,
                )
            store.saveDownloaded(variant, bytes.inputStream())
            return repository.addDocument(
                ReceiptDocument(id = id, displayName = "Original", variants = listOf(variant))
            )
        }
        sessions.activate(session)
        val accountDocument = importOriginal()
        sessions.logoutLocally()
        val anonymousDocument = importOriginal()
        sessions.activate(session)
        assertEquals(
            setOf(accountDocument.id, anonymousDocument.id),
            repository.observeDocuments().first().map { it.id }.toSet(),
        )
        assertEquals(2, db.tickets().outbox(owner).size)
        assertTrue(store.verify(accountDocument.variants.single()))
        assertTrue(store.verify(anonymousDocument.variants.single()))
    }

    @Test
    fun anEditDuringAcknowledgementStaysQueuedOnTheNewBase() = runTest {
        sessions.activate(session)
        repository.save(purchase())
        api.requestBlock = { path, _, _, _ ->
            if (path == "/v1/sync/mutate") {
                repository.save(
                    repository
                        .observePurchases()
                        .first()
                        .single()
                        .copy(merchant = "Edited during upload")
                )
                JSONObject().put("revision", 1)
            } else emptyChanges()
        }
        sync.synchronize()
        val pending = db.tickets().outbox(owner).single()
        assertEquals(1, pending.expectedRemoteRevision)
        assertEquals("Edited during upload", JSONObject(pending.payload).getString("merchant"))
        assertEquals(
            "Edited during upload",
            repository.observePurchases().first().single().merchant,
        )
    }

    @Test
    fun remoteConflictDoesNotOverwriteUntilTheUserChooses() = runTest {
        sessions.activate(session)
        val body = remotePurchase().put("merchant", "Other device")
        api.requestBlock = { path, _, _, _ ->
            when {
                path == "/v1/sync/mutate" -> throw CloudApiException(409, "revision_conflict")
                path.startsWith("/v1/records/") ->
                    JSONObject().put("kind", "PURCHASE").put("revision", 3).put("body", body)
                else -> emptyChanges()
            }
        }
        sync.synchronize()
        assertEquals("Local", repository.observePurchases().first().single().merchant)
        val conflict = sync.conflicts().first().single()
        sync.resolveConflict(conflict.id, false)
        assertEquals("Other device", repository.observePurchases().first().single().merchant)
        assertEquals(0, repository.observePendingCount().first())
        assertTrue(sync.conflicts().first().isEmpty())
    }

    @Test
    fun aFeedForAnotherOwnerRollsBackItsCursorAndRecords() = runTest {
        sessions.activate(session)
        val body = remotePurchase().put("owner_id", newId())
        val operation = db.tickets().outbox(owner).single()
        db.tickets().discardOperation(operation.entityId, owner)
        api.requestBlock = { _, _, _, _ ->
            JSONObject()
                .put(
                    "changes",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("kind", "PURCHASE")
                                .put("revision", 8)
                                .put("body", body)
                        ),
                )
                .put("cursor", 10)
                .put("has_more", false)
        }
        try {
            sync.synchronize()
            fail("Expected owner rejection")
        } catch (_: IllegalArgumentException) {}
        assertNull(db.tickets().cursor(owner))
        assertEquals("Local", repository.observePurchases().first().single().merchant)
        assertNotNull(sync.status.value.error)
    }

    @Test
    fun freshVaultPullsMetadataThenVerifiesDocumentOnDemand() = runTest {
        sessions.activate(session)
        val bytes = "%PDF-1.4 fixture retained in memory".toByteArray()
        val id = newId()
        val variantId = newId()
        val hash =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        val body =
            JSONObject()
                .put("schema_version", 1)
                .put("id", id)
                .put("owner_id", owner)
                .put("display_name", "Receipt")
                .put("state", "SYNCED_VERIFIED")
                .put("created_at", 1)
                .put(
                    "variants",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", variantId)
                                .put("kind", "ORIGINAL")
                                .put("mime_type", "application/pdf")
                                .put("byte_size", bytes.size)
                                .put("sha256", hash)
                        ),
                )
        api.requestBlock = { _, _, _, _ ->
            JSONObject()
                .put(
                    "changes",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("kind", "DOCUMENT")
                                .put("revision", 2)
                                .put("body", body)
                        ),
                )
                .put("cursor", 2)
                .put("has_more", false)
        }
        api.downloadBytes = bytes
        sync.synchronize()
        val document = repository.observeDocuments().first().single()
        assertFalse(store.verify(document.variants.single()))
        sync.ensureDocument(document)
        assertArrayEquals(bytes, store.file(document.variants.single()).readBytes())
        assertTrue(store.verify(document.variants.single()))
        // A failed replacement must leave the previously verified original intact.
        try {
            store.saveDownloaded(document.variants.single(), "corrupt".byteInputStream())
            fail("Expected integrity failure")
        } catch (_: IllegalStateException) {}
        assertTrue(store.verify(document.variants.single()))
    }

    @Test
    fun failedUploadKeepsTheOutboxAndOriginalUntilVerified() = runTest {
        sessions.activate(session)
        val bytes = "fixture-original".toByteArray()
        val id = newId()
        val hash =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        val variant =
            DocumentVariant(
                kind = VariantKind.ORIGINAL,
                relativePath = "$id/original",
                mimeType = "application/pdf",
                byteSize = bytes.size.toLong(),
                sha256 = hash,
            )
        store.saveDownloaded(variant, bytes.inputStream())
        repository.addDocument(
            ReceiptDocument(id = id, displayName = "Receipt", variants = listOf(variant))
        )
        api.requestBlock = { path, _, _, _ ->
            if (path.endsWith("/finalize")) JSONObject().put("revision", 2)
            else if (path.contains("mutate")) JSONObject().put("revision", 1) else emptyChanges()
        }
        api.failUpload = true
        try {
            sync.synchronize()
            fail("Expected upload failure")
        } catch (_: java.io.IOException) {}
        assertEquals(1, repository.observePendingCount().first())
        assertTrue(store.verify(variant))
        assertEquals(DocumentState.LOCAL_ONLY, repository.observeDocuments().first().single().state)
        api.failUpload = false
        sync.synchronize()
        assertEquals(0, repository.observePendingCount().first())
        assertEquals(
            DocumentState.SYNCED_VERIFIED,
            repository.observeDocuments().first().single().state,
        )
        assertTrue(store.verify(variant))
    }
}

private class FakeRemote : RemoteApi {
    override val configured = true
    var failUpload = false
    var downloadBytes = byteArrayOf()
    var requestBlock: suspend (String, String?, String, JSONObject?) -> JSONObject = { _, _, _, _ ->
        error("Unexpected request")
    }

    override suspend fun request(path: String, token: String?, method: String, body: JSONObject?) =
        requestBlock(path, token, method, body)

    override suspend fun upload(
        path: String,
        token: String,
        variant: DocumentVariant,
        store: PrivateDocumentStore,
    ) {
        if (failUpload) throw java.io.IOException("Simulated disconnect")
        check(store.verify(variant))
    }

    override suspend fun download(
        path: String,
        token: String,
        variant: DocumentVariant,
        store: PrivateDocumentStore,
    ) {
        store.saveDownloaded(variant, downloadBytes.inputStream())
    }
}
