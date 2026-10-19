package es.sergiotech.tickettracker.ui

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import es.sergiotech.tickettracker.data.mail.*
import es.sergiotech.tickettracker.domain.IdentityGateway
import es.sergiotech.tickettracker.domain.newId
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class GmailState(
    val email: String? = null,
    val query: String = "",
    val receipts: List<MailReceipt> = emptyList(),
    val nextPage: String? = null,
    val busy: Boolean = false,
    val searched: Boolean = false,
    val error: MailError? = null,
)

data class PreparedMailImport(val uri: Uri, val ownerId: String)

@HiltViewModel
class GmailViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val identity: IdentityGateway,
) : ViewModel() {
    internal var api = GmailApi()
    private val _state = MutableStateFlow(GmailState())
    val state = _state.asStateFlow()
    private val imports = Channel<PreparedMailImport>(Channel.BUFFERED)
    val imported = imports.receiveAsFlow()
    // Short lived access only; never persist, send to the Worker or include in state/logs.
    private var token: String? = null
    private var owner: String? = null
    private var generation = 0
    private var task: Job? = null
    val authorizationStamp
        get() = generation

    init {
        viewModelScope.launch {
            identity.identity.collect { user ->
                if (owner != user.id) {
                    disconnect()
                    owner = user.id
                }
            }
        }
    }

    fun query(value: String) {
        if (!_state.value.busy) _state.update { it.copy(query = value.take(200), nextPage = null) }
    }

    fun authorizationFailed(reason: MailError = MailError.AUTHORIZATION) {
        _state.update { it.copy(error = reason, busy = false) }
    }

    fun connect(accessToken: String, expectedVersion: Int? = null) {
        if (expectedVersion != null && expectedVersion != generation)
            return authorizationFailed(MailError.CANCELLED)
        if (_state.value.busy) return
        task =
            viewModelScope.launch {
                val currentOwner = identity.identity.first().id
                if (owner != currentOwner) {
                    generation++
                    owner = currentOwner
                    _state.value = GmailState()
                }
                if (expectedVersion != null && expectedVersion != generation)
                    return@launch authorizationFailed(MailError.CANCELLED)
                token = accessToken
                val version = generation
                _state.update { it.copy(busy = true, error = null) }
                try {
                    val email = api.email(accessToken)
                    checkOwner(currentOwner, version)
                    _state.update { it.copy(email = email) }
                    searchPage(accessToken, currentOwner, version, false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    report(failure, version)
                } finally {
                    if (version == generation) _state.update { it.copy(busy = false) }
                }
            }
    }

    fun search(more: Boolean = false) {
        val active = token ?: return authorizationFailed()
        val currentOwner = owner ?: return
        if (_state.value.busy) return
        val version = generation
        task =
            viewModelScope.launch {
                _state.update { it.copy(busy = true, error = null) }
                try {
                    searchPage(active, currentOwner, version, more)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    report(failure, version)
                } finally {
                    if (version == generation) _state.update { it.copy(busy = false) }
                }
            }
    }

    private suspend fun searchPage(
        active: String,
        currentOwner: String,
        version: Int,
        more: Boolean,
    ) {
        val before = _state.value
        val page = api.search(active, before.query, if (more) before.nextPage else null)
        checkOwner(currentOwner, version)
        _state.update {
            it.copy(
                receipts =
                    ((if (more) before.receipts else emptyList()) + page.receipts).distinctBy {
                        message ->
                        message.id
                    },
                nextPage = page.nextPage,
                searched = true,
            )
        }
    }

    fun download(attachment: MailAttachment) {
        val active = token ?: return authorizationFailed()
        val currentOwner = owner ?: return
        if (_state.value.busy || _state.value.receipts.none { attachment in it.attachments }) return
        val version = generation
        task =
            viewModelScope.launch {
                _state.update { it.copy(busy = true, error = null) }
                var file: File? = null
                try {
                    val bytes = api.download(active, attachment)
                    checkOwner(currentOwner, version)
                    val target =
                        File(
                            File(context.cacheDir, "captures").apply { mkdirs() },
                            "mail-${newId()}.bin",
                        )
                    file = target
                    withContext(Dispatchers.IO) { target.writeBytes(bytes) }
                    checkOwner(currentOwner, version)
                    imports.send(
                        PreparedMailImport(
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.documents",
                                target,
                            ),
                            currentOwner,
                        )
                    )
                } catch (cancelled: CancellationException) {
                    file?.delete()
                    throw cancelled
                } catch (failure: Exception) {
                    file?.delete()
                    report(failure, version)
                } finally {
                    if (version == generation) _state.update { it.copy(busy = false) }
                }
            }
    }

    fun disconnect() {
        generation++
        task?.cancel()
        task = null
        token = null
        while (imports.tryReceive().isSuccess) {
            /* Drop events from the previous vault. */
        }
        _state.value = GmailState()
    }

    private suspend fun checkOwner(expected: String, version: Int) {
        if (version != generation || identity.identity.first().id != expected)
            throw CancellationException("Mail owner changed")
    }

    private fun report(failure: Exception, version: Int) {
        if (version != generation) return
        val reason = (failure as? MailImportException)?.reason ?: MailError.NETWORK
        if (reason == MailError.AUTHORIZATION) {
            token = null
            _state.update { it.copy(email = null, receipts = emptyList(), nextPage = null) }
        }
        _state.update { it.copy(error = reason) }
    }
}
