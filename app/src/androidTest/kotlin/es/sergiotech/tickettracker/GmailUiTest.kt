package es.sergiotech.tickettracker

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import es.sergiotech.tickettracker.core.designsystem.TicketTheme
import es.sergiotech.tickettracker.data.mail.*
import es.sergiotech.tickettracker.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GmailUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun permissionIsOptionalAndErrorsHaveNoFakeResults() {
        val state = mutableStateOf(GmailState())
        var connections = 0
        compose.setContent {
            TicketTheme(mode = "DARK") {
                GmailContent(state.value, false, {}, {}, {}, {}, {}, { connections++ }, {})
            }
        }
        compose.onNodeWithTag("mail-results").performScrollToNode(hasTestTag("mail-connect"))
        compose.onNodeWithTag("mail-connect").performClick()
        assertEquals(1, connections)
        compose.runOnIdle { state.value = GmailState(error = MailError.API_DISABLED) }
        compose.onNodeWithTag("mail-results").performScrollToNode(hasTestTag("mail-error"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose
            .onNodeWithTag("mail-error")
            .assertTextEquals(context.getString(R.string.mail_disabled_error))
        compose.onNodeWithTag("mail-query").assertDoesNotExist()
    }

    @Test
    fun signedInMailboxOnlyImportsTheChosenAttachment() {
        val first = MailAttachment("msg1", "pdf1", "ticket.pdf", "application/pdf", 100)
        val second = MailAttachment("msg1", "img1", "foto.png", "image/png", 200)
        var chosen: MailAttachment? = null
        val state =
            GmailState(
                email = "lector@example.test",
                receipts =
                    listOf(
                        MailReceipt(
                            "msg1",
                            "Compra",
                            "tienda@example.test",
                            "18/10/2026",
                            listOf(first, second),
                        )
                    ),
                searched = true,
            )
        compose.setContent {
            TicketTheme { GmailContent(state, false, {}, {}, {}, {}, { chosen = it }, {}, {}) }
        }
        compose.onNodeWithTag("mail-connect").assertDoesNotExist()
        compose.onNodeWithTag("mail-results").performScrollToNode(hasTestTag("mail-import-img1"))
        compose.onNodeWithTag("mail-import-img1").performClick()
        assertEquals(second, chosen)
    }
}
