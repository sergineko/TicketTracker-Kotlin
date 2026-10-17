package es.sergiotech.tickettracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.sergiotech.tickettracker.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.settings by preferencesDataStore("tickettracker")

data class AppPreferences(
    val theme: String = "SYSTEM",
    val dynamicColor: Boolean = false,
    val reminders: Boolean = false,
)

class LocalPreferences(private val context: Context) : IdentityGateway {
    private val lock = Mutex()
    private val identityKey = stringPreferencesKey("local_owner_uuid")
    private val themeKey = stringPreferencesKey("theme")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val remindersKey = booleanPreferencesKey("reminders")
    override val identity: Flow<Identity> = flow {
        val id =
            lock.withLock {
                context.settings.data.first()[identityKey]
                    ?: newId().also { id -> context.settings.edit { it[identityKey] = id } }
            }
        emit(Identity(id))
    }
    val preferences =
        context.settings.data.map {
            AppPreferences(
                it[themeKey] ?: "SYSTEM",
                it[dynamicKey] ?: false,
                it[remindersKey] ?: false,
            )
        }

    suspend fun theme(value: String) {
        require(value in setOf("SYSTEM", "LIGHT", "DARK"))
        context.settings.edit { it[themeKey] = value }
    }

    suspend fun dynamic(value: Boolean) {
        context.settings.edit { it[dynamicKey] = value }
    }

    suspend fun reminders(value: Boolean) {
        context.settings.edit { it[remindersKey] = value }
    }
}
