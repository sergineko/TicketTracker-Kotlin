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
    val account: AccountInfo? = null,
    val email: String = "",
    val code: String = "",
    val challengeId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val sync: CloudSyncState = CloudSyncState(),
    val recoveryNeeded: Boolean = false,
    val conflicts: List<SyncConflict> = emptyList(),
)

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
                        sync = status,
                        recoveryNeeded = recovery,
                        conflicts = conflicts,
                    )
                }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _state.update { it.copy(error = "account_load_failed") }
                }
                .collect { value ->
                    _state.update {
                        it.copy(
                            account = value.account,
                            sync = value.sync,
                            recoveryNeeded = value.recoveryNeeded,
                            conflicts = value.conflicts,
                        )
                    }
                }
        }
    }

    fun email(value: String) {
        _state.update { it.copy(email = value.take(254), error = null) }
    }

    fun code(value: String) {
        _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }
    }

    fun resetChallenge() {
        _state.update { it.copy(challengeId = null, code = "", error = null) }
    }

    fun requestCode() = operation {
        val email = state.value.email.trim()
        if (!email.contains('@')) throw CloudOperationException("invalid_email")
        val challenge = accounts.requestCode(email)
        _state.update { it.copy(challengeId = challenge.id, code = "") }
    }

    fun verifyCode() = operation {
        val challenge = state.value.challengeId ?: return@operation
        accounts.verifyCode(challenge, state.value.code)
        _state.update { it.copy(challengeId = null, code = "") }
        sync.synchronize()
    }

    fun google(getToken: suspend (GoogleChallenge) -> String) = operation {
        val challenge = accounts.googleChallenge()
        accounts.verifyGoogle(challenge.id, getToken(challenge))
        sync.synchronize()
    }

    fun synchronize() = operation { sync.synchronize() }

    fun resolve(conflict: SyncConflict, keepLocal: Boolean) = operation {
        sync.resolveConflict(conflict.id, keepLocal)
        sync.synchronize()
    }

    fun logout() = operation {
        if (
            sync.status.value.running ||
                repository.observePendingCount().first() > 0 ||
                state.value.conflicts.isNotEmpty()
        )
            throw CloudOperationException("unsynced_changes")
        accounts.logout()
    }

    private fun operation(block: suspend () -> Unit) {
        if (state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
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
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
