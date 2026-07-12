package com.warden.blocker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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
        val CURRENT_STREAK = intPreferencesKey("current_streak")
        val LONGEST_STREAK = intPreferencesKey("longest_streak")
        val LAST_ACTIVE_DAY = longPreferencesKey("last_active_day")
        val ENABLED_FEATURES = stringSetPreferencesKey("enabled_features")
        val FOCUS_ENDS_AT = longPreferencesKey("focus_ends_at")
        val FOCUS_STRICT = booleanPreferencesKey("focus_strict")
        val BLOCK_DOH = booleanPreferencesKey("block_doh")
    }

    val masterEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.MASTER_ENABLED] ?: false }
    val alwaysOn: Flow<Boolean> = context.dataStore.data.map { it[Keys.ALWAYS_ON] ?: true }
    val strictMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.STRICT_MODE] ?: false }
    val blockDoh: Flow<Boolean> = context.dataStore.data.map { it[Keys.BLOCK_DOH] ?: false }

    suspend fun setBlockDoh(value: Boolean) =
        context.dataStore.edit { it[Keys.BLOCK_DOH] = value }.let { }
    val hasPin: Flow<Boolean> = context.dataStore.data.map { it[Keys.PIN_HASH] != null }
    val currentStreak: Flow<Int> = context.dataStore.data.map { it[Keys.CURRENT_STREAK] ?: 0 }
    val longestStreak: Flow<Int> = context.dataStore.data.map { it[Keys.LONGEST_STREAK] ?: 0 }
    val enabledFeatureKeys: Flow<Set<String>> = context.dataStore.data.map { it[Keys.ENABLED_FEATURES] ?: emptySet() }

    /** Epoch millis a focus session ends (0 = none), and whether it can be cancelled early. */
    val focusEndsAt: Flow<Long> = context.dataStore.data.map { it[Keys.FOCUS_ENDS_AT] ?: 0L }
    val focusStrict: Flow<Boolean> = context.dataStore.data.map { it[Keys.FOCUS_STRICT] ?: false }

    suspend fun startFocus(endsAt: Long, strict: Boolean) =
        context.dataStore.edit { it[Keys.FOCUS_ENDS_AT] = endsAt; it[Keys.FOCUS_STRICT] = strict }.let { }

    suspend fun clearFocus() =
        context.dataStore.edit { it.remove(Keys.FOCUS_ENDS_AT); it.remove(Keys.FOCUS_STRICT) }.let { }

    suspend fun setFeatureEnabled(key: String, enabled: Boolean) =
        context.dataStore.edit { p ->
            val current = (p[Keys.ENABLED_FEATURES] ?: emptySet()).toMutableSet()
            if (enabled) current.add(key) else current.remove(key)
            p[Keys.ENABLED_FEATURES] = current
        }.let { }

    /**
     * Mark today as a protected day and update the focus streak. Called once per app open
     * while blocking is enabled. A gap of more than a day resets the streak to 1.
     */
    suspend fun recordActiveDay() {
        val today = LocalDate.now().toEpochDay()
        context.dataStore.edit { p ->
            val last = p[Keys.LAST_ACTIVE_DAY] ?: -1L
            if (last == today) return@edit
            val current = p[Keys.CURRENT_STREAK] ?: 0
            val next = if (last == today - 1) current + 1 else 1
            p[Keys.CURRENT_STREAK] = next
            p[Keys.LONGEST_STREAK] = maxOf(p[Keys.LONGEST_STREAK] ?: 0, next)
            p[Keys.LAST_ACTIVE_DAY] = today
        }
    }

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

    suspend fun clearPin() =
        context.dataStore.edit {
            it.remove(Keys.PIN_HASH)
            it.remove(Keys.PIN_SALT)
        }.let { }

    /** Returns (hash, salt) or null if no PIN configured. */
    suspend fun pinCredentials(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val h = prefs[Keys.PIN_HASH]
        val s = prefs[Keys.PIN_SALT]
        return if (h != null && s != null) h to s else null
    }
}
