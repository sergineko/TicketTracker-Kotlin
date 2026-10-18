package es.sergiotech.tickettracker

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import es.sergiotech.tickettracker.core.database.TicketDatabase
import es.sergiotech.tickettracker.data.LocalPreferences
import es.sergiotech.tickettracker.data.RoomCoverageRepository
import es.sergiotech.tickettracker.data.RoomPurchaseRepository
import es.sergiotech.tickettracker.domain.*
import es.sergiotech.tickettracker.ui.TrackerViewModel
import es.sergiotech.tickettracker.ui.withExtraction
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DashboardFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun printedReturnDateIsAppliedOnlyToTheChosenProduct() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("add-menu").fetchSemanticsNodes().isNotEmpty()
        }
        check(!File(compose.activity.filesDir, "session.enc").exists())
        compose.onNodeWithTag("add-menu").performClick()
        compose.onNodeWithTag("add-purchase").performClick()
        val vm = ViewModelProvider(compose.activity)[TrackerViewModel::class.java]
        compose.runOnUiThread {
            vm.edit {
                it.withExtraction(
                    ReceiptExtractor()
                        .extract(
                            "TIENDA\nMonitor 39,95\nCable USB 9,95\nTOTAL 49,90 EUR\nDevoluciones hasta 17/11/2026"
                        )
                )
            }
        }
        compose.waitForIdle()
        assertTrue(vm.editor.value.lines.all { it.returnEnd.isBlank() })
        compose.onNodeWithTag("ocr-RETURN-0").performScrollTo().performClick()
        assertEquals("2026-11-17", vm.editor.value.lines[0].returnEnd)
        assertEquals("", vm.editor.value.lines[1].returnEnd)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals("2026-11-17", vm.editor.value.lines[0].returnEnd)
    }

    @Test
    fun recordedDatesFilterProductsAndContractTabsOpenTheirRecords() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("add-menu").fetchSemanticsNodes().isNotEmpty()
        }
        val context = compose.activity.applicationContext
        check(!File(context.filesDir, "session.enc").exists()) {
            "Use an isolated test device without a cloud session"
        }
        val db =
            Room.databaseBuilder(context, TicketDatabase::class.java, "tickettracker.db")
                .addMigrations(
                    TicketDatabase.MIGRATION_1_2,
                    TicketDatabase.MIGRATION_2_3,
                    TicketDatabase.MIGRATION_3_4,
                )
                .build()
        val purchases = RoomPurchaseRepository(db, LocalPreferences(context))
        val contracts = RoomCoverageRepository(db, LocalPreferences(context))
        val today = LocalDate.now()
        val names =
            listOf(
                "Portátil · prueba",
                "Lavadora · prueba",
                "Auriculares · prueba",
                "Consola · prueba",
            )
        val purchase =
            Purchase(
                merchant = "Tienda de prueba",
                purchasedOn = today.minusMonths(1),
                total = Money(245980),
                lines =
                    names.mapIndexed { index, name ->
                        PurchaseLine(
                            product =
                                Product(
                                    name = name,
                                    warrantyEnd = today.plusDays(if (index == 1) 10 else 730),
                                    returnEnd = if (index == 2) today else null,
                                )
                        )
                    },
            )
        val subscription =
            TrackedContract(
                kind = ContractKind.SUBSCRIPTION,
                name = "Streaming · prueba",
                provider = "Proveedor de prueba",
                amount = Money(1299),
                period = BillingPeriod.MONTHLY,
                nextDate = today.plusDays(3),
            )
        val insurance =
            subscription.copy(
                id = newId(),
                kind = ContractKind.INSURANCE,
                name = "Hogar · prueba",
                amount = Money(19900),
                period = BillingPeriod.YEARLY,
                nextDate = today.plusMonths(7),
            )
        try {
            runBlocking {
                purchases.save(purchase)
                contracts.save(subscription)
                contracts.save(insurance)
            }
            val vm = ViewModelProvider(compose.activity)[TrackerViewModel::class.java]
            compose.runOnUiThread { vm.retry() }
            compose.waitUntil(15_000) {
                vm.state.value.purchases.any { it.id == purchase.id } &&
                    vm.state.value.contracts.any { it.id == subscription.id }
            }
            compose.waitForIdle()
            compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
                File(context.cacheDir, "reference-dashboard.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("metric-CRITICAL"))
            compose.onNodeWithTag("metric-CRITICAL").performClick()
            compose.onNodeWithTag("home-list").performScrollToNode(hasText(names[1]))
            compose.onNodeWithText(names[1]).assertExists()
            compose.onNodeWithText(names[0]).assertDoesNotExist()
            compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("metric-RETURNS"))
            compose.onNodeWithTag("metric-RETURNS").performClick()
            compose.onNodeWithTag("home-list").performScrollToNode(hasText(names[2]))
            compose.onNodeWithText(names[2]).assertExists()
            compose.onNodeWithText(names[1]).assertDoesNotExist()
            compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("home-tab-1"))
            compose.onNodeWithTag("home-tab-1").performClick()
            compose.onNodeWithTag("home-list").performScrollToNode(hasText(subscription.name))
            compose.onNodeWithText(subscription.name).assertExists()
            compose.onNodeWithText(names[2]).assertDoesNotExist()
            compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("home-tab-2"))
            compose.onNodeWithTag("home-tab-2").performClick()
            compose.onNodeWithTag("home-list").performScrollToNode(hasText(insurance.name))
            compose.onNodeWithText(insurance.name).assertExists()
        } finally {
            runBlocking {
                purchases
                    .observePurchases()
                    .first()
                    .find { it.id == purchase.id }
                    ?.let { purchases.delete(it.id, it.revision) }
                contracts
                    .observe()
                    .first()
                    .contracts
                    .filter { it.id in setOf(subscription.id, insurance.id) }
                    .forEach { contracts.archive(it.id, it.revision) }
            }
            db.close()
        }
    }

    @Test
    fun subscriptionCanBeCreatedReopenedAndArchived() {
        val name = "Plan UI ${System.currentTimeMillis()}"
        compose.waitUntil(30_000) {
            compose
                .onAllNodesWithTag("contract-card-SUBSCRIPTION")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("contract-card-SUBSCRIPTION").performClick()
        compose.onNodeWithTag("add-contract").performClick()
        compose.onNodeWithTag("contract-name").performTextInput(name)
        compose.onNodeWithTag("contract-amount").performTextInput("12,99")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("save-contract").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("contract-amount").assertTextContains("12,99")
        compose.onNodeWithTag("contract-name").assertTextContains(name)
        compose.onNodeWithTag("save-contract").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("add-contract").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("contract-error").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            compose.onRoot().printToString(),
            compose.onAllNodesWithTag("contract-error").fetchSemanticsNodes().isEmpty(),
        )
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(name).performClick()
        compose.onNodeWithTag("contract-amount").assertTextContains("12.99")
        compose
            .onNodeWithContentDescription(compose.activity.getString(R.string.delete))
            .performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.delete)).performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("add-contract").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(name).assertDoesNotExist()
    }
}
