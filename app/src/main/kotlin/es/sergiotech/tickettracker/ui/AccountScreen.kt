package es.sergiotech.tickettracker.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.sergiotech.tickettracker.BuildConfig
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.data.clearGoogleCredentialState
import es.sergiotech.tickettracker.data.googleToken
import es.sergiotech.tickettracker.domain.*

internal data class AccountActions(
    val email: (String) -> Unit,
    val code: (String) -> Unit,
    val requestCode: () -> Unit,
    val verifyCode: () -> Unit,
    val resetChallenge: () -> Unit,
    val google: () -> Unit,
    val synchronize: () -> Unit,
    val logout: () -> Unit,
    val resolve: (SyncConflict, Boolean) -> Unit,
    val storageUsage: () -> Unit,
    val optimizeStorage: () -> Unit,
)

@Composable
fun AccountScreen(
    inventory: TrackerState,
    state: AccountUiState,
    vm: AccountViewModel,
    back: () -> Unit,
) {
    val context = LocalContext.current
    AccountPage(
        inventory,
        state,
        vm.configured,
        AccountActions(
            vm::email,
            vm::code,
            vm::requestCode,
            vm::verifyCode,
            vm::resetChallenge,
            { vm.google { googleToken(context, it) } },
            vm::synchronize,
            { vm.logout { clearGoogleCredentialState(context) } },
            vm::resolve,
            vm::storageUsage,
            vm::optimizeStorage,
        ),
        back,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountPage(
    inventory: TrackerState,
    state: AccountUiState,
    configured: Boolean,
    actions: AccountActions,
    back: () -> Unit,
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var accessOptions by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label(R.string.account_title)) },
                navigationIcon = {
                    IconButton(back, Modifier.testTag("account-back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, label(R.string.back))
                    }
                },
            )
        }
    ) { padding ->
        if (state.loading)
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        else
            LazyColumn(
                Modifier.padding(padding).testTag("account-list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    if (state.account != null) AccountIdentity(state)
                    else
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            BrandSymbol(Modifier.size(60.dp))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    label(R.string.account_welcome),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    label(R.string.account_welcome_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                }
                if (!configured)
                    item {
                        Text(
                            label(R.string.cloud_pending),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                else if (state.needsSignIn()) item { SignInCard(state, actions) }
                else {
                    item { AccountSyncCard(inventory, state, actions.synchronize) }
                    item { AccountStorageCard(inventory, state, actions) }
                    if (state.error != null) item { AccountErrorMessage(state.error) }
                    items(state.conflicts, key = { it.id }) { conflict ->
                        ConflictCard(conflict, state.busy, actions.resolve)
                    }
                }
                item {
                    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.CloudDone,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        label(R.string.cloud_scope_title),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        label(R.string.cloud_scope_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    Icons.Outlined.PhoneAndroid,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        label(R.string.device_scope_title),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.device_scope_body,
                                            inventory.contracts.size,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.account != null)
                    item {
                        OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                TextButton(
                                    onClick = { accessOptions = !accessOptions },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.testTag("account-access-options"),
                                ) {
                                    Icon(Icons.Outlined.ManageAccounts, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(label(R.string.access_options))
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        if (accessOptions) Icons.Outlined.ExpandLess
                                        else Icons.Outlined.ExpandMore,
                                        null,
                                    )
                                }
                                if (accessOptions) {
                                    Text(
                                        label(R.string.access_options_body),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (
                                        !state.needsSignIn() &&
                                            BuildConfig.GOOGLE_CLIENT_ID.isNotBlank() &&
                                            state.account.signInMethod != SignInMethod.GOOGLE
                                    )
                                        GoogleAccessButton(
                                            actions.google,
                                            !state.busy && !state.sync.running,
                                        )
                                }
                                HorizontalDivider()
                                TextButton(
                                    onClick = { confirmLogout = true },
                                    enabled = !state.busy && !state.sync.running,
                                    modifier = Modifier.testTag("account-signout"),
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Logout, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(label(R.string.signout))
                                }
                            }
                        }
                    }
                item {
                    Text(
                        label(R.string.local_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
    if (confirmLogout)
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(label(R.string.signout_title)) },
            text = { Text(label(R.string.signout_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        actions.logout()
                    }
                ) {
                    Text(label(R.string.signout))
                }
            },
            dismissButton = {
                TextButton({ confirmLogout = false }) { Text(label(R.string.cancel)) }
            },
        )
}

@Composable
private fun AccountStorageCard(
    inventory: TrackerState,
    state: AccountUiState,
    actions: AccountActions,
) {
    val context = LocalContext.current
    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.primary)
                Text(label(R.string.storage_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(label(R.string.storage_policy_body), style = MaterialTheme.typography.bodySmall)
            state.storage?.let { usage ->
                Text(
                    stringResource(
                        R.string.storage_originals,
                        usage.documents,
                        android.text.format.Formatter.formatFileSize(context, usage.originalBytes),
                    )
                )
                Text(
                    stringResource(
                        R.string.storage_derivatives,
                        android.text.format.Formatter.formatFileSize(context, usage.derivativeBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    label(R.string.storage_usage_scope),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (usage.optimizableIds.isNotEmpty())
                    OutlinedButton(
                        onClick = actions.optimizeStorage,
                        enabled =
                            !state.busy &&
                                !state.sync.running &&
                                inventory.pendingCount == 0 &&
                                state.conflicts.isEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("storage-optimize"),
                    ) {
                        Text(label(R.string.storage_optimize))
                    }
            }
            TextButton(
                onClick = actions.storageUsage,
                enabled = !state.busy,
                modifier = Modifier.testTag("storage-refresh"),
            ) {
                if (state.action == AccountAction.STORAGE)
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(label(R.string.storage_refresh))
            }
        }
    }
}

@Composable
private fun AccountIdentity(state: AccountUiState) {
    val account = requireNotNull(state.account)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccountAvatar(account, Modifier.size(54.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    account.email,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("account-email"),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (state.needsSignIn()) Icons.Outlined.LockClock
                        else Icons.Outlined.VerifiedUser,
                        null,
                        Modifier.size(16.dp),
                    )
                    Text(
                        label(
                            if (state.needsSignIn()) R.string.account_renew
                            else R.string.account_session_active
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    label(
                        when (account.signInMethod) {
                            SignInMethod.GOOGLE -> R.string.access_google
                            SignInMethod.EMAIL_CODE -> R.string.access_email
                            SignInMethod.UNKNOWN -> R.string.account_session_private
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun AccountAvatar(account: AccountInfo?, modifier: Modifier) {
    Surface(
        modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (account == null) Icon(Icons.Outlined.PersonOutline, null, Modifier.size(28.dp))
            else
                Text(
                    account.email.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                )
        }
    }
}

@Composable
private fun SignInCard(state: AccountUiState, actions: AccountActions) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.recoveryNeeded) AccountErrorMessage("session_recovery")
            if (state.account != null)
                Text(label(R.string.session_expired), style = MaterialTheme.typography.bodyMedium)
            if (state.challengeId == null) {
                if (BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) {
                    GoogleAccessButton(actions.google, !state.busy)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text(label(R.string.or_email), style = MaterialTheme.typography.labelMedium)
                        HorizontalDivider(Modifier.weight(1f))
                    }
                }
                OutlinedTextField(
                    state.email,
                    actions.email,
                    Modifier.fillMaxWidth().testTag("account-email-input"),
                    label = { Text(label(R.string.email)) },
                    leadingIcon = { Icon(Icons.Outlined.MailOutline, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !state.busy,
                    readOnly = state.account != null,
                )
                Button(
                    actions.requestCode,
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account-send-code"),
                    enabled = !state.busy,
                ) {
                    Text(label(R.string.send_code))
                }
                Text(
                    label(R.string.account_merge_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    label(R.string.code_step_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.code_step_body, state.email),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    state.code,
                    actions.code,
                    Modifier.fillMaxWidth().testTag("account-code-input"),
                    label = { Text(label(R.string.access_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    enabled = !state.busy,
                )
                Button(
                    actions.verifyCode,
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account-verify-code"),
                    enabled = !state.busy && state.code.length == 6,
                ) {
                    Text(label(R.string.verify_code))
                }
                Text(
                    label(R.string.code_sent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(actions.resetChallenge, enabled = !state.busy) {
                    Text(label(R.string.change_email))
                }
            }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    label(
                        if (state.action == AccountAction.GOOGLE) R.string.google_connecting
                        else R.string.account_connecting
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.error?.let { AccountErrorMessage(it) }
        }
    }
}

@Composable
private fun GoogleAccessButton(click: () -> Unit, enabled: Boolean) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    OutlinedButton(
        click,
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account-google"),
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (dark) Color(0xFF8E918F) else Color(0xFF747775)),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = if (dark) Color(0xFF131314) else Color.White,
                contentColor = if (dark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F),
            ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Surface(shape = CircleShape, color = Color.White) {
            Image(painterResource(R.drawable.google_g), null, Modifier.padding(2.dp).size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label(R.string.google_signin),
            Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AccountSyncCard(
    inventory: TrackerState,
    state: AccountUiState,
    synchronize: () -> Unit,
) {
    val syncing = state.sync.running || state.action == AccountAction.SYNC
    val error = state.sync.error
    val title =
        when {
            syncing -> R.string.sync_in_progress
            error != null -> R.string.sync_needs_attention
            state.conflicts.isNotEmpty() -> R.string.sync_conflict
            inventory.pendingCount > 0 -> R.string.sync_pending_title
            state.sync.lastSuccess != null -> R.string.sync_up_to_date
            else -> R.string.cloud_ready_title
        }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (error != null) Icons.Outlined.CloudOff
                    else if (syncing) Icons.Outlined.CloudSync else Icons.Outlined.CloudDone,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    label(title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("account-sync-status"),
                )
            }
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                if (error != null) label(accountError(error))
                else if (state.conflicts.isNotEmpty()) label(R.string.conflict_body)
                else if (inventory.pendingCount > 0)
                    pluralStringResource(
                        R.plurals.pending_cloud_changes,
                        inventory.pendingCount,
                        inventory.pendingCount,
                    )
                else
                    label(
                        if (state.sync.lastSuccess == null) R.string.cloud_ready_body
                        else R.string.sync_scope_complete
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccountStatistic(
                    inventory.purchases.size,
                    R.string.cloud_purchase_stat,
                    Modifier.weight(1f).fillMaxHeight(),
                )
                AccountStatistic(
                    inventory.documents.count { it.state == DocumentState.SYNCED_VERIFIED },
                    R.string.cloud_document_stat,
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
            state.sync.lastSuccess?.let { time ->
                Text(
                    stringResource(
                        R.string.last_connection,
                        DateUtils.formatDateTime(
                            LocalContext.current,
                            time,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                synchronize,
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account-sync"),
                enabled = !state.busy && !state.sync.running,
            ) {
                Icon(Icons.Outlined.Sync, null)
                Spacer(Modifier.width(8.dp))
                Text(label(if (error != null) R.string.retry else R.string.synchronize))
            }
        }
    }
}

@Composable
private fun AccountStatistic(count: Int, title: Int, modifier: Modifier) {
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(label(title), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: SyncConflict,
    busy: Boolean,
    resolve: (SyncConflict, Boolean) -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label(R.string.sync_conflict), fontWeight = FontWeight.Bold)
            Text(label(R.string.conflict_body), style = MaterialTheme.typography.bodyMedium)
            if (conflict.kind == "PURCHASE") {
                if (conflict.canKeepLocal)
                    TextButton({ resolve(conflict, true) }, enabled = !busy) {
                        Text(label(R.string.keep_local))
                    }
                TextButton({ resolve(conflict, false) }, enabled = !busy) {
                    Text(label(R.string.use_remote))
                }
            }
        }
    }
}

@Composable
private fun AccountErrorMessage(code: String) {
    Text(
        label(accountError(code)),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("account-error"),
    )
}

@Composable
internal fun accountSummary(state: AccountUiState, pending: Int): String =
    when {
        state.loading -> label(R.string.account_loading)
        state.account == null -> label(R.string.account_local_summary)
        state.needsSignIn() -> label(R.string.account_renew)
        state.sync.running -> label(R.string.sync_in_progress)
        state.sync.error != null || state.conflicts.isNotEmpty() ->
            label(R.string.sync_needs_attention)
        pending > 0 -> pluralStringResource(R.plurals.pending_cloud_changes, pending, pending)
        else -> label(R.string.account_session_active)
    }

@Composable
internal fun AccountBadge(state: AccountUiState, pending: Int, open: () -> Unit) {
    Surface(
        open,
        Modifier.fillMaxWidth().testTag("account-entry"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                if (state.account == null) Icons.Outlined.CloudQueue
                else Icons.Outlined.AccountCircle,
                null,
                Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                accountSummary(state, pending),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.account == null)
                Text(
                    label(R.string.account_connect),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

internal fun accountError(code: String) =
    when (code) {
        "invalid_email" -> R.string.invalid_email
        "invalid_code" -> R.string.invalid_code
        "google_unavailable" -> R.string.google_unavailable
        "google_device_setup" -> R.string.google_device_setup
        "google_response_invalid",
        "invalid_google_token",
        "invalid_google_challenge" -> R.string.google_retry
        "google_signin_failed" -> R.string.google_signin_failed
        "credential_clear_failed" -> R.string.google_clear_failed
        "identity_conflict" -> R.string.account_identity_conflict
        "quota_exceeded" -> R.string.cloud_quota
        "rate_limited" -> R.string.auth_rate_limit
        "unsynced_changes" -> R.string.unsynced_signout
        "session_expired",
        "authentication_required" -> R.string.session_expired
        "session_recovery" -> R.string.session_recovery
        "google_not_configured",
        "email_not_configured",
        "service_not_configured" -> R.string.cloud_pending
        "verify_email_before_linking" -> R.string.google_link_email
        else -> R.string.cloud_error
    }
