package es.sergiotech.tickettracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import es.sergiotech.tickettracker.ui.TrackerViewModel
import java.io.File
import org.junit.Rule
import org.junit.Test

class PurchaseFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun sharedMailTextOpensAReviewDraftWithoutInventingADocument() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("add-menu").fetchSemanticsNodes().isNotEmpty()
        }
        val vm = ViewModelProvider(compose.activity)[TrackerViewModel::class.java]
        val previousDocuments = vm.state.value.documents.size
        val launchIntent = android.content.Intent(compose.activity.intent)
        try {
            compose.runOnUiThread {
                compose.activity.startActivity(
                    android.content
                        .Intent(compose.activity, MainActivity::class.java)
                        .setAction(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            "TIENDA CORREO\n18/10/2026\nPAN 1,70\nTOTAL 1,70 EUR",
                        )
                        .addFlags(
                            android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                )
            }
            val title = compose.activity.getString(R.string.share_text_import)
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithTag("merchant").assertTextContains("TIENDA CORREO")
            compose.onNodeWithTag("total").assertTextContains("1.70")
            org.junit.Assert.assertTrue(vm.editor.value.documentIds.isEmpty())
            org.junit.Assert.assertEquals(previousDocuments, vm.state.value.documents.size)
            org.junit.Assert.assertEquals("2026-10-18", vm.editor.value.date)
            org.junit.Assert.assertEquals("PAN", vm.editor.value.lines.single().name)
        } finally {
            // ActivityScenario matches lifecycle events against its launch intent.
            compose.runOnUiThread { compose.activity.intent = launchIntent }
        }
    }

    @Test
    fun manualPurchaseCanBeSavedSearchedAndReopened() {
        val merchant = "Tienda UI ${System.currentTimeMillis()}"
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("add-menu").fetchSemanticsNodes().isNotEmpty()
        }
        check(!File(compose.activity.filesDir, "session.enc").exists()) {
            "Use an isolated test device without a cloud session"
        }
        compose.onNodeWithTag("add-menu").performClick()
        compose.onNodeWithTag("add-purchase").performClick()
        compose.onNodeWithTag("merchant").performTextInput(merchant)
        compose.onNodeWithTag("total").performTextInput("39,95")
        compose
            .onNodeWithTag("purchase-date")
            .performScrollTo()
            .performTextReplacement("21/10/2026")
        compose
            .onNodeWithTag("product-0")
            .performScrollTo()
            .performTextInput("Auriculares de prueba")
        compose.onNodeWithTag("save").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("nav-purchases").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-purchases").performClick()
        compose.onNodeWithTag("search").performTextInput(merchant)
        val result = hasText(merchant) and !hasTestTag("search")
        compose.waitUntil(15_000) { compose.onAllNodes(result).fetchSemanticsNodes().size == 1 }
        compose.onNode(result).performClick()
        compose.onNodeWithText("Auriculares de prueba").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(merchant).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Auriculares de prueba").assertExists()
        val vm = ViewModelProvider(compose.activity)[TrackerViewModel::class.java]
        compose.runOnUiThread {
            vm.state.value.purchases.find { it.merchant == merchant }?.let(vm::delete)
        }
        compose.waitUntil(15_000) { vm.state.value.purchases.none { it.merchant == merchant } }
    }
}
