package es.sergiotech.tickettracker.ui

import android.accounts.Account
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.data.mail.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun GmailScreen(vm: GmailViewModel, back: () -> Unit, imported: (Uri, String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = requireNotNull(LocalActivity.current)
    val client = remember(activity) { Identity.getAuthorizationClient(activity) }
    val coroutine = rememberCoroutineScope()
    var authorizing by remember { mutableStateOf(false) }
    var authorizationVersion by rememberSaveable { mutableStateOf<Int?>(null) }
    fun accept(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank() || GmailApi.SCOPE !in result.grantedScopes)
            vm.authorizationFailed(MailError.PERMISSION)
        else
            authorizationVersion?.let { vm.connect(token, it) }
                ?: vm.authorizationFailed(MailError.CANCELLED)
    }
    val authorization =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            result ->
            authorizing = false
            if (result.resultCode != Activity.RESULT_OK || result.data == null)
                vm.authorizationFailed(MailError.CANCELLED)
            else
                try {
                    accept(client.getAuthorizationResultFromIntent(result.data!!))
                } catch (_: Exception) {
                    vm.authorizationFailed()
                }
        }
    LaunchedEffect(vm) { vm.imported.collect { imported(it.uri, it.ownerId) } }
    GmailContent(
        state,
        state.busy || authorizing,
        back,
        vm::query,
        { vm.search() },
        { vm.search(true) },
        vm::download,
        connect = {
            authorizationVersion = vm.authorizationStamp
            authorizing = true
            coroutine.launch {
                try {
                    val result =
                        client
                            .authorize(
                                AuthorizationRequest.builder()
                                    .setRequestedScopes(listOf(Scope(GmailApi.SCOPE)))
                                    .build()
                            )
                            .await()
                    if (result.hasResolution())
                        authorization.launch(
                            IntentSenderRequest.Builder(
                                    requireNotNull(result.pendingIntent).intentSender
                                )
                                .build()
                        )
                    else {
                        authorizing = false
                        accept(result)
                    }
                } catch (cancelled: CancellationException) {
                    authorizing = false
                    throw cancelled
                } catch (_: Exception) {
                    authorizing = false
                    vm.authorizationFailed()
                }
            }
        },
        disconnect = {
            val email = state.email ?: return@GmailContent
            authorizing = true
            coroutine.launch {
                try {
                    client
                        .revokeAccess(
                            RevokeAccessRequest.builder()
                                .setAccount(Account(email, "com.google"))
                                .setScopes(listOf(Scope(GmailApi.SCOPE)))
                                .build()
                        )
                        .await()
                    vm.disconnect()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    vm.authorizationFailed()
                } finally {
                    authorizing = false
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GmailContent(
    state: GmailState,
    busy: Boolean,
    back: () -> Unit,
    query: (String) -> Unit,
    search: () -> Unit,
    more: () -> Unit,
    download: (MailAttachment) -> Unit,
    connect: () -> Unit,
    disconnect: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label(R.string.mail_title)) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, label(R.string.back))
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("mail-results"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Icon(Icons.Outlined.Email, null, tint = MaterialTheme.colorScheme.primary)
                Text(label(R.string.mail_intro), style = MaterialTheme.typography.bodyMedium)
                Text(
                    label(R.string.mail_testing_notice),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Text(
                    label(R.string.mail_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.error?.let { error ->
                item {
                    Text(
                        label(mailErrorResource(error)),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("mail-error"),
                    )
                }
            }
            item {
                if (state.email == null)
                    Button(
                        onClick = connect,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("mail-connect"),
                    ) {
                        Text(label(R.string.mail_connect))
                    }
                else
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.email, style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            state.query,
                            query,
                            Modifier.fillMaxWidth().testTag("mail-query"),
                            label = { Text(label(R.string.mail_query)) },
                            placeholder = { Text(label(R.string.mail_query_hint)) },
                            enabled = !busy,
                            singleLine = true,
                        )
                        Button(
                            onClick = search,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().testTag("mail-search"),
                        ) {
                            Text(label(R.string.mail_search))
                        }
                        TextButton(onClick = disconnect, enabled = !busy) {
                            Text(label(R.string.mail_disconnect))
                        }
                    }
            }
            if (busy)
                item {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(label(R.string.mail_loading))
                }
            if (state.searched && state.receipts.isEmpty() && !busy)
                item { Text(label(R.string.mail_empty)) }
            items(state.receipts, key = { it.id }) { receipt ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            receipt.subject.ifBlank { label(R.string.mail_no_subject) },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(receipt.sender, style = MaterialTheme.typography.bodySmall)
                        Text(receipt.sentAt, style = MaterialTheme.typography.bodySmall)
                        receipt.attachments.forEach { attachment ->
                            Text(attachment.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                android.text.format.Formatter.formatFileSize(
                                    LocalContext.current,
                                    attachment.bytes,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedButton(
                                onClick = { download(attachment) },
                                enabled = !busy,
                                modifier =
                                    Modifier.testTag("mail-import-${attachment.attachmentId}"),
                            ) {
                                Text(label(R.string.mail_import))
                            }
                        }
                    }
                }
            }
            if (state.nextPage != null)
                item {
                    OutlinedButton(
                        onClick = more,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label(R.string.mail_more))
                    }
                }
        }
    }
}

private fun mailErrorResource(error: MailError) =
    when (error) {
        MailError.AUTHORIZATION -> R.string.mail_auth_error
        MailError.PERMISSION -> R.string.mail_permission_error
        MailError.API_DISABLED -> R.string.mail_disabled_error
        MailError.RATE_LIMIT -> R.string.mail_rate_error
        MailError.TOO_LARGE -> R.string.mail_large_error
        MailError.INVALID -> R.string.mail_invalid_error
        MailError.CANCELLED -> R.string.mail_cancelled
        MailError.NETWORK -> R.string.mail_network_error
    }
