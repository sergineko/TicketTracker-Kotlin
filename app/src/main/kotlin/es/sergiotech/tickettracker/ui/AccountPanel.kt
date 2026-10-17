package es.sergiotech.tickettracker.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import es.sergiotech.tickettracker.BuildConfig
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.domain.CloudOperationException
import es.sergiotech.tickettracker.domain.GoogleChallenge
import kotlinx.coroutines.CancellationException

@Composable
fun AccountPanel(state: AccountUiState, vm: AccountViewModel) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label(R.string.account), style = MaterialTheme.typography.titleLarge)
        if (!vm.configured) Text(label(R.string.cloud_pending))
        else {
            if (state.recoveryNeeded)
                Text(label(R.string.session_recovery), color = MaterialTheme.colorScheme.error)
            val account = state.account
            if (account == null || account.expiresAt <= System.currentTimeMillis() / 1000) {
                Text(
                    label(R.string.account_merge_notice),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.challengeId == null) {
                    OutlinedTextField(
                        state.email,
                        vm::email,
                        Modifier.fillMaxWidth(),
                        label = { Text(label(R.string.email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !state.busy,
                    )
                    Button(
                        onClick = { vm.requestCode() },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label(R.string.send_code))
                    }
                    if (BuildConfig.GOOGLE_CLIENT_ID.isNotBlank())
                        OutlinedButton(
                            onClick = {
                                vm.google { challenge -> googleToken(context, challenge) }
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label(R.string.google_signin))
                        }
                    else
                        Text(
                            label(R.string.google_pending),
                            style = MaterialTheme.typography.bodySmall,
                        )
                } else {
                    Text(label(R.string.code_sent))
                    OutlinedTextField(
                        state.code,
                        vm::code,
                        Modifier.fillMaxWidth(),
                        label = { Text(label(R.string.access_code)) },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = !state.busy,
                    )
                    Button(
                        onClick = { vm.verifyCode() },
                        enabled = !state.busy && state.code.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label(R.string.verify_code))
                    }
                    TextButton(onClick = vm::resetChallenge, enabled = !state.busy) {
                        Text(label(R.string.change_email))
                    }
                }
            } else {
                Text(account.email, style = MaterialTheme.typography.titleMedium)
                if (BuildConfig.GOOGLE_CLIENT_ID.isNotBlank())
                    OutlinedButton(
                        onClick = { vm.google { challenge -> googleToken(context, challenge) } },
                        enabled = !state.busy && !state.sync.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label(R.string.link_google))
                    }
                Text(label(R.string.account_connected), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { vm.synchronize() },
                    enabled = !state.busy && !state.sync.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(label(R.string.synchronize))
                }
                TextButton(
                    onClick = { vm.logout() },
                    enabled = !state.busy && !state.sync.running,
                ) {
                    Text(label(R.string.signout))
                }
            }
            if (state.busy || state.sync.running) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.sync.lastSuccess != null && state.conflicts.isEmpty())
                Text(label(R.string.sync_success), style = MaterialTheme.typography.bodySmall)
            state.error?.let {
                Text(label(accountError(it)), color = MaterialTheme.colorScheme.error)
            }
            if (state.error == null)
                state.sync.error?.let {
                    Text(label(accountError(it)), color = MaterialTheme.colorScheme.error)
                }
            for (conflict in state.conflicts) {
                OutlinedCard {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            label(R.string.sync_conflict),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(label(R.string.conflict_body))
                        if (conflict.kind == "PURCHASE") {
                            if (conflict.canKeepLocal)
                                TextButton(
                                    onClick = { vm.resolve(conflict, true) },
                                    enabled = !state.busy,
                                ) {
                                    Text(label(R.string.keep_local))
                                }
                            TextButton(
                                onClick = { vm.resolve(conflict, false) },
                                enabled = !state.busy,
                            ) {
                                Text(label(R.string.use_remote))
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun googleToken(context: Context, challenge: GoogleChallenge): String {
    try {
        val option =
            GetSignInWithGoogleOption.Builder(challenge.clientId).setNonce(challenge.nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential =
            CredentialManager.create(context).getCredential(context, request).credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        )
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    } catch (_: NoCredentialException) {
        throw CloudOperationException("google_unavailable")
    } catch (cancelled: GetCredentialCancellationException) {
        throw CancellationException("Google sign-in cancelled", cancelled)
    }
}

private fun accountError(code: String) =
    when (code) {
        "invalid_email" -> R.string.invalid_email
        "google_unavailable" -> R.string.google_unavailable
        "quota_exceeded" -> R.string.cloud_quota
        "invalid_code" -> R.string.invalid_code
        "rate_limited" -> R.string.auth_rate_limit
        "unsynced_changes" -> R.string.unsynced_signout
        "session_expired",
        "authentication_required" -> R.string.session_expired
        "google_not_configured",
        "email_not_configured",
        "service_not_configured" -> R.string.cloud_pending
        "verify_email_before_linking" -> R.string.google_link_email
        else -> R.string.cloud_error
    }
