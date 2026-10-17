package es.sergiotech.tickettracker

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import es.sergiotech.tickettracker.core.database.TicketDatabase
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.data.*
import es.sergiotech.tickettracker.domain.*
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class CoverageIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: TicketDatabase
    private lateinit var repository: RoomCoverageRepository

    private fun identity(owner: String) =
        object : IdentityGateway {
            override val identity = flowOf(Identity(owner))
        }

    private fun record() =
        TrackedContract(
            kind = ContractKind.SUBSCRIPTION,
            name = "Plan",
            provider = "Provider",
            amount = Money(1299),
            period = BillingPeriod.MONTHLY,
            nextDate = LocalDate.of(2026, 11, 17),
        )

    @Before
    fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.cacheDir, "coverage-test-${newId()}").apply { mkdirs() }
        context =
            object : ContextWrapper(base) {
                override fun getFilesDir() = directory
            }
        db = Room.inMemoryDatabaseBuilder(context, TicketDatabase::class.java).build()
        repository = RoomCoverageRepository(db, identity("owner-a"))
    }

    @After
    fun close() {
        db.close()
        context.filesDir.deleteRecursively()
    }

    @Test
    fun revisionsDeletionAndOwnershipAreEnforced() = runTest {
        repository.save(record())
        val initial = repository.observe().first().contracts.single()
        assertEquals(1L, initial.revision)
        val other = RoomCoverageRepository(db, identity("owner-b"))
        assertTrue(other.observe().first().contracts.isEmpty())
        try {
            other.save(initial.copy(ownerId = "owner-b"))
            fail("Owner overwrite")
        } catch (_: IllegalArgumentException) {}
        try {
            other.archive(initial.id, initial.revision)
            fail("Owner deletion")
        } catch (_: IllegalArgumentException) {}
        repository.save(initial.copy(name = "Updated", kind = ContractKind.INSURANCE))
        try {
            repository.save(initial)
            fail("Stale overwrite")
        } catch (_: RevisionConflictException) {}
        try {
            repository.archive(initial.id, initial.revision)
            fail("Stale deletion")
        } catch (_: RevisionConflictException) {}
        val updated = repository.observe().first().contracts.single()
        assertEquals("Updated", updated.name)
        repository.archive(updated.id, updated.revision)
        assertTrue(repository.observe().first().contracts.isEmpty())
        assertNotNull(db.coverage().contract(updated.id)!!.deletedAt)
        try {
            repository.save(updated.copy(revision = updated.revision + 1))
            fail("Resurrection")
        } catch (_: RevisionConflictException) {}
        assertTrue(db.tickets().outbox("owner-a").isEmpty()) // These records are explicitly local.
    }

    @Test
    fun exportContainsLocalContractsAndByteExactOriginalsForOnlyCurrentOwner() = runTest {
        repository.save(record())
        RoomCoverageRepository(db, identity("owner-b"))
            .save(record().copy(name = "Private other owner"))
        val purchases = RoomPurchaseRepository(db, identity("owner-a"))
        val store = PrivateDocumentStore(context)
        val bytes = "synthetic original for export integrity".toByteArray()
        val hash =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        val variant =
            DocumentVariant(
                kind = VariantKind.ORIGINAL,
                relativePath = "${newId()}/original",
                mimeType = "application/pdf",
                byteSize = bytes.size.toLong(),
                sha256 = hash,
            )
        store.saveDownloaded(variant, bytes.inputStream())
        purchases.addDocument(
            ReceiptDocument(displayName = "Synthetic original", variants = listOf(variant))
        )
        val archive = File(context.filesDir, "export.zip")
        VaultExporter(context, purchases, store, repository, identity("owner-a"))
            .export(Uri.fromFile(archive))
        ZipFile(archive).use { zip ->
            val manifest =
                JSONObject(
                    zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText()
                )
            assertEquals(2, manifest.getInt("version"))
            val contracts = manifest.getJSONArray("contracts")
            assertEquals(1, contracts.length())
            assertEquals(1299, contracts.getJSONObject(0).getLong("amount_minor"))
            assertEquals("owner-a", contracts.getJSONObject(0).getString("owner_id"))
            assertEquals("2026-11-17", contracts.getJSONObject(0).getString("next_date"))
            assertArrayEquals(
                bytes,
                zip.getInputStream(zip.getEntry("documents/${variant.relativePath}")).readBytes(),
            )
        }
        assertTrue(store.verify(variant))
    }
}
