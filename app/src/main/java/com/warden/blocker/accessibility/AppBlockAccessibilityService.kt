package com.warden.blocker.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.warden.blocker.block.AccessDecision
import com.warden.blocker.block.LimitReason
import com.warden.blocker.ui.intercept.InterceptActivity
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches the foreground app and asks [com.warden.blocker.block.AccessController] what to do.
 * Depending on the decision it launches [InterceptActivity] in hard-block, limit-reached, or
 * mindful-pause mode. Website blocking is handled network-side by the VPN.
 *
 * Privacy: only the foreground package name is inspected — never screen content.
 */
class AppBlockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg == lastPackage) return
        lastPackage = pkg

        scope.launch {
            when (val decision = wardenContainer.accessController.decideForPackage(pkg)) {
                is AccessDecision.Allow -> Unit
                is AccessDecision.Pause ->
                    launchIntercept(InterceptActivity.KIND_PAUSE, decision.item.id, null)
                is AccessDecision.HardBlock ->
                    launchIntercept(InterceptActivity.KIND_BLOCK, decision.item.id, null)
                is AccessDecision.LimitReached ->
                    launchIntercept(InterceptActivity.KIND_LIMIT, decision.item.id, decision.reason)
            }
        }
    }

    private fun launchIntercept(kind: String, itemId: Long, reason: LimitReason?) {
        val intent = Intent(this, InterceptActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(InterceptActivity.EXTRA_KIND, kind)
            .putExtra(InterceptActivity.EXTRA_ITEM_ID, itemId)
            .putExtra(InterceptActivity.EXTRA_REASON, reason?.name)
        startActivity(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
