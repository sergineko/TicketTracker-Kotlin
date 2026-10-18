package es.sergiotech.tickettracker.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SignInMethod {
    UNKNOWN,
    EMAIL_CODE,
    GOOGLE,
}

data class AccountInfo(
    val id: String,
    val email: String,
    val expiresAt: Long,
    val signInMethod: SignInMethod = SignInMethod.UNKNOWN,
)

data class EmailChallenge(val id: String)

data class GoogleChallenge(val id: String, val nonce: String, val clientId: String)

data class CloudSyncState(
    val running: Boolean = false,
    val lastSuccess: Long? = null,
    val error: String? = null,
    val ownerId: String? = null,
)

data class SyncConflict(val id: String, val kind: String, val canKeepLocal: Boolean = true)

data class CloudStorageUsage(
    val originalBytes: Long,
    val derivativeBytes: Long,
    val documents: Int,
    val optimizableIds: List<String>,
    val optimizedBytes: Long = 0,
)

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

    suspend fun storageUsage(): CloudStorageUsage =
        throw CloudOperationException("storage_unavailable")

    suspend fun optimizeStorage() {
        throw CloudOperationException("storage_unavailable")
    }
}
