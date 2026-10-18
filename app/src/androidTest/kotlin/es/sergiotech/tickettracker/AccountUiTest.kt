package es.sergiotech.tickettracker

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import es.sergiotech.tickettracker.core.designsystem.TicketTheme
import es.sergiotech.tickettracker.domain.*
import es.sergiotech.tickettracker.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountUiTest {
    @get:Rule val compose = createComposeRule()
    private val account =
        AccountInfo("fixture-owner", "cuenta@example.test", 4102444800, SignInMethod.GOOGLE)

    private fun actions(sync: () -> Unit = {}, logout: () -> Unit = {}) =
        AccountActions({}, {}, {}, {}, {}, {}, sync, logout, { _, _ -> }, {}, {})

    @Test
    fun signedInGoogleShowsIdentityAndSyncWithoutAnotherLoginButton() {
        var syncCalls = 0
        var logoutCalls = 0
        compose.setContent {
            TicketTheme(mode = "DARK") {
                AccountPage(
                    TrackerState(loading = false),
                    AccountUiState(loading = false, account = account),
                    true,
                    actions({ syncCalls++ }, { logoutCalls++ }),
                    {},
                )
            }
        }
        compose.onNodeWithTag("account-email").assertTextEquals(account.email)
        compose.onNodeWithTag("account-google").assertDoesNotExist()
        compose.onNodeWithTag("account-send-code").assertDoesNotExist()
        compose.onNodeWithTag("account-sync").performClick()
        assertEquals(1, syncCalls)
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(context.cacheDir, "account-dark.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose
            .onNodeWithTag("account-list")
            .performScrollToNode(hasTestTag("account-access-options"))
        compose.onNodeWithTag("account-access-options").performClick()
        compose.onNodeWithTag("account-google").assertDoesNotExist()
        compose.onNodeWithTag("account-list").performScrollToNode(hasTestTag("account-signout"))
        compose.onNodeWithTag("account-signout").performClick()
        assertEquals(0, logoutCalls)
    }

    @Test
    fun failedAndPendingSyncNeverShowsUpToDate() {
        val state =
            mutableStateOf(
                AccountUiState(
                    loading = false,
                    account = account,
                    sync =
                        CloudSyncState(
                            lastSuccess = 1,
                            error = "network_error",
                            ownerId = account.id,
                        ),
                )
            )
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            TicketTheme {
                AccountPage(
                    TrackerState(pendingCount = 2, loading = false),
                    state.value,
                    true,
                    actions(),
                    {},
                )
            }
        }
        compose
            .onNodeWithTag("account-sync-status")
            .assertTextEquals(context.getString(R.string.sync_needs_attention))
        compose.runOnIdle {
            state.value =
                state.value.copy(sync = CloudSyncState(lastSuccess = 1, ownerId = account.id))
        }
        compose
            .onNodeWithTag("account-sync-status")
            .assertTextEquals(context.getString(R.string.sync_pending_title))
    }

    @Test
    fun signedOutAndExpiredSessionsOfferAuthenticationInsteadOfSync() {
        val state = mutableStateOf(AccountUiState(loading = false))
        compose.setContent {
            TicketTheme { AccountPage(TrackerState(), state.value, true, actions(), {}) }
        }
        compose.onNodeWithTag("account-google").assertExists()
        compose.onNodeWithTag("account-sync").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(account = account.copy(expiresAt = 1)) }
        compose.onNodeWithTag("account-google").assertExists()
        compose.onNodeWithTag("account-sync").assertDoesNotExist()
    }
}
