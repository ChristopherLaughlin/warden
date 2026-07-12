package com.warden.blocker.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.warden.blocker.ui.block.BlockedActivity
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches the foreground app. When a blocked app comes to the front (and blocking is
 * active per the [com.warden.blocker.block.BlockEngine]), it launches the full-screen
 * [BlockedActivity]. Website blocking is handled network-side by the VPN; this service
 * is the per-app layer.
 *
 * Privacy: only the foreground package name is inspected. No screen content is read,
 * stored, or transmitted.
 */
class AppBlockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastHandledPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg == lastHandledPackage) return

        scope.launch {
            if (wardenContainer.blockEngine.isPackageBlockedNow(pkg)) {
                lastHandledPackage = pkg
                launchBlockScreen(pkg)
            } else if (pkg != lastHandledPackage) {
                lastHandledPackage = null
            }
        }
    }

    private fun launchBlockScreen(blockedPackage: String) {
        val intent = Intent(this, BlockedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(BlockedActivity.EXTRA_PACKAGE, blockedPackage)
        startActivity(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
