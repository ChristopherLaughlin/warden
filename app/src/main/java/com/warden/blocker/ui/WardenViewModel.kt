package com.warden.blocker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.warden.blocker.data.BlockType
import com.warden.blocker.data.BlockedItem
import com.warden.blocker.data.InterceptMode
import com.warden.blocker.data.Schedule
import com.warden.blocker.security.PinHasher
import com.warden.blocker.system.FocusEndWorker
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WardenViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.wardenContainer

    val items: StateFlow<List<BlockedItem>> =
        container.repository.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val schedules: StateFlow<List<Schedule>> =
        container.repository.schedules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val masterEnabled: StateFlow<Boolean> =
        container.settings.masterEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val strictMode: StateFlow<Boolean> =
        container.settings.strictMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val alwaysOn: StateFlow<Boolean> =
        container.settings.alwaysOn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAlwaysOn(value: Boolean) = viewModelScope.launch { container.settings.setAlwaysOn(value) }

    val blockDoh: StateFlow<Boolean> =
        container.settings.blockDoh.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBlockDoh(value: Boolean) = viewModelScope.launch { container.settings.setBlockDoh(value) }

    val blockNotifications: StateFlow<Boolean> =
        container.settings.blockNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBlockNotifications(value: Boolean) = viewModelScope.launch { container.settings.setBlockNotifications(value) }
    val hasPin: StateFlow<Boolean> =
        container.settings.hasPin.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val currentStreak: StateFlow<Int> =
        container.settings.currentStreak.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val longestStreak: StateFlow<Int> =
        container.settings.longestStreak.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val enabledFeatureKeys: StateFlow<Set<String>> =
        container.settings.enabledFeatureKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val focusEndsAt: StateFlow<Long> =
        container.settings.focusEndsAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val focusStrict: StateFlow<Boolean> =
        container.settings.focusStrict.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Begin a focus session that forces blocking on for [minutes]. */
    fun startFocus(minutes: Int, strict: Boolean) = viewModelScope.launch {
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        container.settings.startFocus(endsAt, strict)
        FocusEndWorker.schedule(getApplication(), minutes * 60_000L)
    }

    /** Cancel early; refused while a strict session is still running. */
    fun cancelFocus() = viewModelScope.launch {
        val strict = container.settings.focusStrict.first()
        val endsAt = container.settings.focusEndsAt.first()
        if (strict && endsAt > System.currentTimeMillis()) return@launch
        container.settings.clearFocus()
        FocusEndWorker.cancel(getApplication())
    }

    fun setFeatureEnabled(key: String, enabled: Boolean) = viewModelScope.launch {
        container.settings.setFeatureEnabled(key, enabled)
    }

    /** Record a protected day for the focus streak, once per open while blocking is on. */
    fun onAppOpened() = viewModelScope.launch {
        if (container.settings.masterEnabled.first()) container.settings.recordActiveDay()
    }

    fun addWebsite(domain: String) {
        val clean = domain.trim().lowercase()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").substringBefore("/").ifBlank { return }
        viewModelScope.launch {
            container.repository.addItem(
                BlockedItem(type = BlockType.WEBSITE, value = clean, label = clean, createdAt = System.currentTimeMillis()),
            )
        }
    }

    /** New apps default to a mindful PAUSE (ScreenZen-style), not a hard block. */
    fun addApp(packageName: String, label: String) = viewModelScope.launch {
        if (container.repository.appItem(packageName) != null) return@launch
        container.repository.addItem(
            BlockedItem(
                type = BlockType.APP,
                value = packageName,
                label = label,
                createdAt = System.currentTimeMillis(),
                interceptMode = InterceptMode.PAUSE,
            ),
        )
    }

    fun toggleItem(item: BlockedItem) = viewModelScope.launch {
        container.repository.updateItem(item.copy(enabled = !item.enabled))
    }

    /** Persist edits from the per-item config editor. */
    fun saveItem(item: BlockedItem) = viewModelScope.launch { container.repository.updateItem(item) }

    fun removeItem(item: BlockedItem) = viewModelScope.launch { container.repository.removeItem(item) }

    fun addSchedule(schedule: Schedule) = viewModelScope.launch { container.repository.addSchedule(schedule) }
    fun removeSchedule(schedule: Schedule) = viewModelScope.launch { container.repository.removeSchedule(schedule) }

    fun setMasterEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setMasterEnabled(enabled) }
    fun setStrictMode(enabled: Boolean) = viewModelScope.launch { container.settings.setStrictMode(enabled) }

    // --- PIN ---
    fun setPin(pin: String) = viewModelScope.launch {
        val salt = PinHasher.newSalt()
        container.settings.setPin(PinHasher.hash(pin, salt), salt)
    }

    fun clearPin() = viewModelScope.launch { container.settings.clearPin() }

    /** Suspends on the DataStore read; returns true when [pin] matches the stored hash. */
    suspend fun verifyPin(pin: String): Boolean {
        val (hash, salt) = container.settings.pinCredentials() ?: return false
        return PinHasher.verify(pin, salt, hash)
    }
}
