package com.warden.blocker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.warden.blocker.data.BlockType
import com.warden.blocker.data.BlockedItem
import com.warden.blocker.data.Schedule
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun addApp(packageName: String, label: String) = viewModelScope.launch {
        container.repository.addItem(
            BlockedItem(type = BlockType.APP, value = packageName, label = label, createdAt = System.currentTimeMillis()),
        )
    }

    fun toggleItem(item: BlockedItem) = viewModelScope.launch {
        container.repository.updateItem(item.copy(enabled = !item.enabled))
    }

    fun removeItem(item: BlockedItem) = viewModelScope.launch { container.repository.removeItem(item) }

    fun addSchedule(schedule: Schedule) = viewModelScope.launch { container.repository.addSchedule(schedule) }
    fun removeSchedule(schedule: Schedule) = viewModelScope.launch { container.repository.removeSchedule(schedule) }

    fun setMasterEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setMasterEnabled(enabled) }
    fun setStrictMode(enabled: Boolean) = viewModelScope.launch { container.settings.setStrictMode(enabled) }
}
