package es.sergiotech.tickettracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class PurchaseFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun manualPurchaseCanBeSavedSearchedAndReopened() {
        val merchant = "Tienda UI ${System.currentTimeMillis()}"
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("add-menu").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("add-menu").performClick()
        compose.onNodeWithTag("add-purchase").performClick()
        compose.onNodeWithTag("merchant").performTextInput(merchant)
        compose.onNodeWithTag("total").performTextInput("39,95")
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
    }
}
