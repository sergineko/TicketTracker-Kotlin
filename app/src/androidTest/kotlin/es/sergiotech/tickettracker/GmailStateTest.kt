package es.sergiotech.tickettracker

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import es.sergiotech.tickettracker.data.mail.*
import es.sergiotech.tickettracker.domain.*
import es.sergiotech.tickettracker.ui.GmailViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GmailStateTest {
    @Test
    fun changingVaultCancelsInFlightMailAndRejectsAnOldConsentResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val identity = MutableStateFlow(Identity("owner-a"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm =
            GmailViewModel(
                context,
                object : IdentityGateway {
                    override val identity = identity
                },
            )
        val models = ViewModelStore().apply { put("mail", vm) }
        val waitForProfile = CompletableDeferred<Unit>()
        vm.api = GmailApi { _, _, _ ->
            waitForProfile.await()
            JSONObject("""{"emailAddress":"lector@example.test"}""")
        }
        try {
            runCurrent()
            val stamp = vm.authorizationStamp
            vm.connect("fixture-access", stamp)
            runCurrent()
            assertTrue(vm.state.value.busy)
            identity.value = Identity("owner-b")
            runCurrent()
            waitForProfile.complete(Unit)
            runCurrent()
            assertNull(vm.state.value.email)
            assertTrue(vm.state.value.receipts.isEmpty())
            assertFalse(vm.state.value.busy)
            vm.connect("stale-fixture-access", stamp)
            runCurrent()
            assertEquals(MailError.CANCELLED, vm.state.value.error)
            assertNull(vm.state.value.email)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedConnectionShowsTheRealReasonWithoutResults() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm =
            GmailViewModel(
                ApplicationProvider.getApplicationContext(),
                object : IdentityGateway {
                    override val identity = MutableStateFlow(Identity("owner-a"))
                },
            )
        val models = ViewModelStore().apply { put("mail", vm) }
        vm.api = GmailApi { _, _, _ -> throw MailImportException(MailError.API_DISABLED) }
        try {
            runCurrent()
            vm.connect("fixture-access", vm.authorizationStamp)
            advanceUntilIdle()
            assertEquals(MailError.API_DISABLED, vm.state.value.error)
            assertTrue(vm.state.value.receipts.isEmpty())
            assertFalse(vm.state.value.busy)
            assertNull(vm.state.value.email)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }
}
