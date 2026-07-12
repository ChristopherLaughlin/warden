package com.warden.blocker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "warden_settings")

/**
 * User settings & protection state. The PIN is stored as a salted hash — never plaintext.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val ALWAYS_ON = booleanPreferencesKey("always_on")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
    }

    val masterEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.MASTER_ENABLED] ?: false }
    val alwaysOn: Flow<Boolean> = context.dataStore.data.map { it[Keys.ALWAYS_ON] ?: true }
    val strictMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.STRICT_MODE] ?: false }
    val hasPin: Flow<Boolean> = context.dataStore.data.map { it[Keys.PIN_HASH] != null }

    suspend fun setMasterEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.MASTER_ENABLED] = value }.let { }

    suspend fun setAlwaysOn(value: Boolean) =
        context.dataStore.edit { it[Keys.ALWAYS_ON] = value }.let { }

    suspend fun setStrictMode(value: Boolean) =
        context.dataStore.edit { it[Keys.STRICT_MODE] = value }.let { }

    suspend fun setPin(hash: String, salt: String) =
        context.dataStore.edit {
            it[Keys.PIN_HASH] = hash
            it[Keys.PIN_SALT] = salt
        }.let { }

    /** Returns (hash, salt) or null if no PIN configured. */
    suspend fun pinCredentials(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val h = prefs[Keys.PIN_HASH]
        val s = prefs[Keys.PIN_SALT]
        return if (h != null && s != null) h to s else null
    }
}
