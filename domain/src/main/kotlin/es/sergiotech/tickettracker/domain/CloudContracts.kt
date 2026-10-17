package es.sergiotech.tickettracker.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class AccountInfo(val id: String, val email: String, val expiresAt: Long)

data class EmailChallenge(val id: String)

data class GoogleChallenge(val id: String, val nonce: String, val clientId: String)

data class CloudSyncState(
    val running: Boolean = false,
    val lastSuccess: Long? = null,
    val error: String? = null,
)

data class SyncConflict(val id: String, val kind: String, val canKeepLocal: Boolean = true)

open class CloudOperationException(val errorCode: String) : Exception(errorCode)

interface AccountGateway {
    val configured: Boolean
    val accounts: Flow<AccountInfo?>
    val recoveryNeeded: Flow<Boolean>

    suspend fun requestCode(email: String): EmailChallenge

    suspend fun verifyCode(challengeId: String, code: String)

    suspend fun googleChallenge(): GoogleChallenge

    suspend fun verifyGoogle(challengeId: String, idToken: String)

    suspend fun logout()
}

interface SyncController {
    val status: StateFlow<CloudSyncState>

    fun conflicts(): Flow<List<SyncConflict>>

    suspend fun synchronize()

    suspend fun ensureDocument(document: ReceiptDocument)

    suspend fun resolveConflict(id: String, keepLocal: Boolean)
}
