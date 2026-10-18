package es.sergiotech.tickettracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.sergiotech.tickettracker.domain.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AccountUiState(
    val loading: Boolean = true,
    val account: AccountInfo? = null,
    val email: String = "",
    val code: String = "",
    val challengeId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val sync: CloudSyncState = CloudSyncState(),
    val recoveryNeeded: Boolean = false,
    val conflicts: List<SyncConflict> = emptyList(),
    val action: AccountAction? = null,
    val storage: CloudStorageUsage? = null,
)

enum class AccountAction {
    SEND_CODE,
    VERIFY_CODE,
    GOOGLE,
    SYNC,
    SIGN_OUT,
    RESOLVE,
    STORAGE,
}

fun AccountUiState.needsSignIn(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
    account == null ||
        account.expiresAt <= nowSeconds ||
        sync.error in setOf("session_expired", "authentication_required")

@HiltViewModel
class AccountViewModel
@Inject
constructor(
    private val accounts: AccountGateway,
    private val sync: SyncController,
    private val repository: PurchaseRepository,
) : ViewModel() {
    val configured
        get() = accounts.configured

    private val _state = MutableStateFlow(AccountUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(accounts.accounts, sync.status, accounts.recoveryNeeded, sync.conflicts()) {
                    account,
                    status,
                    recovery,
                    conflicts ->
                    AccountUiState(
                        account = account,
                        sync =
                            if (account != null && account.id == status.ownerId) status
                            else CloudSyncState(),
                        recoveryNeeded = recovery,
                        conflicts = conflicts,
                    )
                }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _state.update { it.copy(loading = false, error = "account_load_failed") }
                }
                .collect { value ->
                    _state.update {
                        it.copy(
                            loading = false,
                            account = value.account,
                            sync = value.sync,
                            recoveryNeeded = value.recoveryNeeded,
                            conflicts = value.conflicts,
                            email =
                                if (it.account?.id != value.account?.id)
                                    value.account?.email.orEmpty()
                                else it.email,
                            challengeId =
                                if (it.account?.id != value.account?.id) null else it.challengeId,
                            code = if (it.account?.id != value.account?.id) "" else it.code,
                            storage = if (it.account?.id != value.account?.id) null else it.storage,
                        )
                    }
                }
        }
    }

    fun email(value: String) {
        if (state.value.busy || state.value.account != null || state.value.challengeId != null)
            return
        _state.update { it.copy(email = value.take(254), error = null) }
    }

    fun code(value: String) {
        if (state.value.busy) return
        _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }
    }

    fun resetChallenge() {
        if (state.value.busy) return
        _state.update { it.copy(challengeId = null, code = "", error = null) }
    }

    fun requestCode() =
        operation(AccountAction.SEND_CODE) {
            val email =
                (state.value.account?.email ?: state.value.email)
                    .trim()
                    .lowercase(java.util.Locale.ROOT)
            if (!email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
                throw CloudOperationException("invalid_email")
            val challenge = accounts.requestCode(email)
            _state.update { it.copy(email = email, challengeId = challenge.id, code = "") }
        }

    fun verifyCode() =
        operation(AccountAction.VERIFY_CODE) {
            val challenge = state.value.challengeId ?: return@operation
            if (state.value.code.length != 6) throw CloudOperationException("invalid_code")
            accounts.verifyCode(challenge, state.value.code)
            _state.update { it.copy(challengeId = null, code = "") }
            sync.synchronize()
        }

    fun google(getToken: suspend (GoogleChallenge) -> String) =
        operation(AccountAction.GOOGLE) {
            val challenge = accounts.googleChallenge()
            accounts.verifyGoogle(challenge.id, getToken(challenge))
            sync.synchronize()
        }

    fun synchronize() = operation(AccountAction.SYNC) { sync.synchronize() }

    fun storageUsage() =
        operation(AccountAction.STORAGE) {
            _state.update { it.copy(storage = null) }
            val usage = sync.storageUsage()
            _state.update { it.copy(storage = usage) }
        }

    fun optimizeStorage() =
        operation(AccountAction.STORAGE) {
            if (
                repository.observePendingCount().first() > 0 ||
                    sync.status.value.running ||
                    state.value.conflicts.isNotEmpty()
            )
                throw CloudOperationException("unsynced_changes")
            _state.update { it.copy(storage = null) }
            sync.optimizeStorage()
            sync.synchronize()
            val usage = sync.storageUsage()
            _state.update { it.copy(storage = usage) }
        }

    fun resolve(conflict: SyncConflict, keepLocal: Boolean) =
        operation(AccountAction.RESOLVE) {
            sync.resolveConflict(conflict.id, keepLocal)
            sync.synchronize()
        }

    fun logout(clearCredentialState: suspend () -> Unit = {}) =
        operation(AccountAction.SIGN_OUT) {
            if (
                sync.status.value.running ||
                    repository.observePendingCount().first() > 0 ||
                    state.value.conflicts.isNotEmpty()
            )
                throw CloudOperationException("unsynced_changes")
            accounts.logout()
            _state.update { it.copy(email = "", code = "", challengeId = null, error = null) }
            clearCredentialState()
        }

    private fun operation(action: AccountAction, block: suspend () -> Unit) {
        if (state.value.busy) return
        _state.update { it.copy(busy = true, action = action, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        error = (failure as? CloudOperationException)?.errorCode ?: "network_error"
                    )
                }
            } finally {
                _state.update { it.copy(busy = false, action = null) }
            }
        }
    }
}
