package com.warden.blocker.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Cancels notifications from blocked apps while notification blocking is enabled and blocking
 * is active — so a blocked app can't lure you back with a buzz. Opt-in; requires the user to
 * grant notification access. Reads only the posting package name.
 */
class WardenNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg == packageName) return
        val key = sbn.key
        scope.launch {
            val container = wardenContainer
            if (!container.settings.blockNotifications.first()) return@launch
            if (!container.blockEngine.isBlockingActiveNow()) return@launch
            val item = container.repository.appItem(pkg) ?: return@launch
            if (item.enabled) cancelNotification(key)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
