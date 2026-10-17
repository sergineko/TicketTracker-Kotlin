package es.sergiotech.tickettracker.data.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.room.withTransaction
import es.sergiotech.tickettracker.core.database.TicketDatabase
import es.sergiotech.tickettracker.data.LocalPreferences
import es.sergiotech.tickettracker.domain.Identity
import es.sergiotech.tickettracker.domain.IdentityGateway
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class CloudSession(
    val userId: String,
    val email: String,
    val token: String,
    val expiresAt: Long,
)

class CloudSessionStore(
    context: Context,
    private val db: TicketDatabase,
    private val local: LocalPreferences,
) : IdentityGateway {
    private val file = AtomicFile(File(context.filesDir, "session.enc"))
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow<CloudSession?>(null)
    val sessions = _session.asStateFlow()
    private val _recoveryNeeded = MutableStateFlow(false)
    val recoveryNeeded = _recoveryNeeded.asStateFlow()
    private val initialized =
        scope.async {
            if (file.baseFile.exists()) {
                try {
                    val bytes = file.readFully()
                    require(bytes.size > 28)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        key(),
                        GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
                    )
                    val json =
                        JSONObject(
                            String(
                                cipher.doFinal(bytes.copyOfRange(12, bytes.size)),
                                Charsets.UTF_8,
                            )
                        )
                    val session =
                        CloudSession(
                            json.getString("user_id"),
                            json.getString("email"),
                            json.getString("token"),
                            json.getLong("expires_at"),
                        )
                    adopt(json.optString("adopt_from"), session.userId)
                    _session.value = session
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _recoveryNeeded.value = true
                }
            }
        }
    override val identity: Flow<Identity> = flow {
        initialized.await()
        val anonymous = local.identity.first()
        emitAll(
            sessions
                .map { if (it == null) anonymous else Identity(it.userId, false) }
                .distinctUntilChanged()
        )
    }

    suspend fun current(): CloudSession? {
        initialized.await()
        return _session.value
    }

    suspend fun activate(session: CloudSession) =
        withContext(Dispatchers.IO) {
            initialized.await()
            lock.withLock {
                require(_session.value == null || _session.value?.userId == session.userId) {
                    "Sign out before changing accounts"
                }
                val anonymous = local.identity.first().id
                val json =
                    JSONObject().apply {
                        put("user_id", session.userId)
                        put("email", session.email)
                        put("token", session.token)
                        put("expires_at", session.expiresAt)
                        put("adopt_from", anonymous)
                    }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val encrypted =
                    cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
                val output = file.startWrite()
                try {
                    output.write(encrypted)
                    file.finishWrite(output)
                } catch (failure: Exception) {
                    file.failWrite(output)
                    throw failure
                }
                // The encrypted adoption marker makes a crash before Room commit recoverable at
                // startup.
                adopt(anonymous, session.userId)
                _session.value = session
                _recoveryNeeded.value = false
            }
        }

    suspend fun logoutLocally() =
        withContext(Dispatchers.IO) {
            initialized.await()
            lock.withLock {
                file.delete()
                _session.value = null
            }
        }

    private suspend fun adopt(fromOwner: String, toOwner: String) {
        if (fromOwner.isBlank() || fromOwner == toOwner) return
        db.withTransaction {
            db.tickets().adoptPurchases(fromOwner, toOwner)
            db.tickets().adoptDocuments(fromOwner, toOwner)
            db.tickets().adoptOutbox(fromOwner, toOwner)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("tickettracker-session-v1", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "tickettracker-session-v1",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setUserAuthenticationRequired(false)
                        .build()
                )
            }
            .generateKey()
    }
}
