package es.sergiotech.tickettracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.sergiotech.tickettracker.core.database.TicketDatabase
import es.sergiotech.tickettracker.data.RoomPurchaseRepository
import es.sergiotech.tickettracker.domain.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private lateinit var db: TicketDatabase
    private lateinit var repository: RoomPurchaseRepository
    private val identity =
        object : IdentityGateway {
            override val identity = flowOf(Identity("owner-a"))
        }

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    TicketDatabase::class.java,
                )
                .build()
        repository = RoomPurchaseRepository(db, identity)
    }

    @After fun close() = db.close()

    @Test
    fun saveEditAndDeletePreserveOutboxAndDocument() = runTest {
        val document =
            repository.addDocument(
                ReceiptDocument(
                    displayName = "Factura",
                    variants =
                        listOf(
                            DocumentVariant(
                                kind = VariantKind.ORIGINAL,
                                relativePath = "test/original",
                                mimeType = "application/pdf",
                                byteSize = 10,
                                sha256 = "a".repeat(64),
                            )
                        ),
                )
            )
        val purchase =
            Purchase(
                merchant = "Prueba",
                purchasedOn = LocalDate.of(2026, 10, 17),
                total = Money(2599),
                lines =
                    listOf(
                        PurchaseLine(product = Product(name = "Monitor")),
                        PurchaseLine(product = Product(name = "Cable")),
                    ),
            )
        SavePurchase(repository)(purchase, listOf(document.id))
        val saved = repository.observePurchases().first().single()
        assertEquals(2, saved.lines.size)
        assertEquals(1L, saved.revision)
        assertEquals(purchase.id, repository.observeDocuments().first().single().purchaseId)
        repository.save(saved.copy(merchant = "Editada"), listOf(document.id))
        val edited = repository.observePurchases().first().single()
        assertEquals(2L, edited.revision)
        assertEquals(saved.lines.map { it.product.id }, edited.lines.map { it.product.id })
        repository.delete(edited.id, edited.revision)
        assertTrue(repository.observePurchases().first().isEmpty())
        assertEquals(1, repository.observeDocuments().first().size)
        assertTrue(db.tickets().outbox("owner-a").any { it.entityType == "PURCHASE_TOMBSTONE" })
    }

    @Test
    fun staleEditDoesNotOverwriteNewerData() = runTest {
        val p =
            Purchase(
                merchant = "Inicial",
                purchasedOn = LocalDate.now(),
                total = Money(1),
                lines = listOf(PurchaseLine(product = Product(name = "Artículo"))),
            )
        repository.save(p)
        val old = repository.observePurchases().first().single()
        repository.save(old.copy(merchant = "Nueva"))
        try {
            repository.save(old.copy(merchant = "Antigua"))
            fail("Expected conflict")
        } catch (_: RevisionConflictException) {}
        assertEquals("Nueva", repository.observePurchases().first().single().merchant)
    }

    @Test
    fun secondIdentityCannotSeeOrOverwriteFirstIdentity() = runTest {
        val p =
            Purchase(
                merchant = "Privada",
                purchasedOn = LocalDate.now(),
                total = Money(1),
                lines = listOf(PurchaseLine(product = Product(name = "Privado"))),
            )
        repository.save(p)
        val other =
            RoomPurchaseRepository(
                db,
                object : IdentityGateway {
                    override val identity = flowOf(Identity("owner-b"))
                },
            )
        assertTrue(other.observePurchases().first().isEmpty())
        try {
            other.save(p)
            fail("Expected owner rejection")
        } catch (_: IllegalArgumentException) {}
        assertEquals("Privada", repository.observePurchases().first().single().merchant)
    }

    @Test
    fun identicalDocumentsDoNotCreateDuplicateRecords() = runTest {
        fun doc() =
            ReceiptDocument(
                displayName = "Original",
                variants =
                    listOf(
                        DocumentVariant(
                            kind = VariantKind.ORIGINAL,
                            relativePath = "test/file",
                            mimeType = "image/jpeg",
                            byteSize = 20,
                            sha256 = "b".repeat(64),
                        )
                    ),
            )
        val first = repository.addDocument(doc())
        val duplicate = repository.addDocument(doc())
        assertEquals(first.id, duplicate.id)
        assertEquals(1, repository.observeDocuments().first().size)
    }
}
