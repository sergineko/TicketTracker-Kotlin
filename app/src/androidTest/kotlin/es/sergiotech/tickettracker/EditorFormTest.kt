package es.sergiotech.tickettracker

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import es.sergiotech.tickettracker.core.designsystem.TicketTheme
import es.sergiotech.tickettracker.domain.*
import es.sergiotech.tickettracker.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditorFormTest {
    @get:Rule val compose = createComposeRule()
    private var draft by mutableStateOf(EditorState())
    private val actions =
        EditorActions(
            edit = { transform -> draft = transform(draft).copy(error = null) },
            line = { index, transform ->
                draft =
                    draft.copy(
                        lines =
                            draft.lines.mapIndexed { i, line ->
                                if (i == index) transform(line) else line
                            },
                        error = null,
                    )
            },
            add = { draft = draft.copy(lines = draft.lines + LineDraft()) },
            remove = { index ->
                draft = draft.copy(lines = draft.lines.filterIndexed { i, _ -> i != index })
            },
            attach = {},
            deadline = { _, _ -> },
            save = { draft = draft.copy(error = draft.problems().firstOrNull()?.error) },
        )

    private fun show(mode: String = "LIGHT", largeFont: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides
                    Density(density.density, if (largeFont) 1.3f else density.fontScale)
            ) {
                TicketTheme(mode = mode) { PurchaseForm(draft, emptyList(), actions, {}, {}, {}) }
            }
        }
    }

    @Test
    fun importedItemsAreCompactAndOptionalDatesStayHiddenUntilRequested() {
        draft =
            EditorState(
                merchant = "FERRETERIA EJEMPLO",
                date = "2026-10-21",
                total = "10.00",
                extracted = true,
                lines =
                    listOf(
                        LineDraft(name = "Tornillos de acero", quantity = "4", price = "2.50"),
                        LineDraft(name = "Brocha", price = "0.00"),
                    ),
            )
        show()
        compose.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, "editor-light-preview.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("product-0").assertDoesNotExist()
        compose.onNodeWithTag("expand-item-0").performScrollTo().performClick()
        compose.onNodeWithTag("product-0").assertTextContains("Tornillos de acero")
        compose.onNodeWithTag("warranty-0").assertDoesNotExist()
        compose.onNodeWithTag("coverage-0").performScrollTo().performClick()
        compose.onNodeWithTag("warranty-0").assertExists()
        compose.onNodeWithTag("save").assertIsDisplayed()
        compose.onNodeWithTag("coverage-0").performScrollTo().performClick()
        compose.onNodeWithTag("warranty-0").assertDoesNotExist()
    }

    @Test
    fun hiddenInvalidFieldsExpandAndReportedErrorsDisappearAfterCorrection() {
        draft =
            EditorState(
                merchant = "TALLER EJEMPLO",
                date = "21/10/2026",
                total = "40.00",
                lines =
                    listOf(
                        LineDraft(
                            name = "Revisión",
                            quantity = "1000",
                            price = "40.00",
                            warrantyEnd = "2026-02-31",
                        )
                    ),
            )
        show()
        compose.onNodeWithTag("save").performClick()
        compose.onNodeWithTag("quantity-0").assertExists()
        compose.onNodeWithTag("quantity-0").performScrollTo().performTextReplacement("1")
        compose.onNodeWithTag("save").performClick()
        compose.onNodeWithTag("warranty-0").assertExists()
        compose.onNodeWithTag("warranty-0").performScrollTo().performTextReplacement("2027-10-21")
        compose.onNodeWithTag("save").performClick()
        compose.runOnIdle {
            assertTrue(draft.problems().isEmpty())
            assertNull(draft.error)
        }
    }

    @Test
    fun usingTheSumIsAnExplicitChoiceAndRemovingARowDoesNotChangeOtherIds() {
        val first = LineDraft(name = "Servicio", quantity = "2", price = "15.00")
        val second = LineDraft(name = "Material", price = "10.00")
        draft =
            EditorState(
                merchant = "SERVICIO EJEMPLO",
                date = "2026-10-21",
                total = "45.00",
                lines = listOf(first, second),
                extracted = true,
                unreadableRows = true,
            )
        show("DARK", largeFont = true)
        compose.runOnIdle { assertEquals("45.00", draft.total) }
        compose.onNodeWithTag("use-line-total").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("40.00", draft.total) }
        compose.onNodeWithTag("remove-item-0").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(second.id, draft.lines.single().id)
            assertEquals("40.00", draft.total)
        }
        compose.onNodeWithTag("save").assertIsDisplayed()
        // Synthetic preview only; it is never an app inventory or a private document.
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, "editor-dark-preview.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
