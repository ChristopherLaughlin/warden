package com.warden.blocker.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.warden.blocker.block.AccessDecision
import com.warden.blocker.block.LimitReason
import com.warden.blocker.feature.AppFeature
import com.warden.blocker.feature.FeatureCatalog
import com.warden.blocker.feature.FeatureDetector
import com.warden.blocker.ui.intercept.InterceptActivity
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Watches the foreground app and asks [com.warden.blocker.block.AccessController] what to do,
 * launching [InterceptActivity] for a hard block, limit notice, or mindful pause.
 *
 * It also performs *in-app feature* blocking: while a supported app is foreground, it scans
 * the visible node tree (throttled) for signals of a blocked feature — e.g. Instagram Reels
 * or YouTube Shorts — and intercepts when one is on screen.
 *
 * Privacy: it inspects the foreground package name and, for feature detection, view ids /
 * visible labels of on-screen nodes. Nothing is stored or transmitted.
 */
class AppBlockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastPackage: String? = null

    private var lastFeatureScan = 0L
    private var interceptedFeatureKey: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleAppBlock(pkg)
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            maybeScanFeatures(pkg)
        }
    }

    // --- Whole-app blocking ---
    private fun handleAppBlock(pkg: String) {
        if (pkg == lastPackage) return
        lastPackage = pkg
        interceptedFeatureKey = null // new app in front; allow feature re-detection
        scope.launch {
            when (val decision = wardenContainer.accessController.decideForPackage(pkg)) {
                is AccessDecision.Allow -> Unit
                is AccessDecision.Pause -> launchIntercept(InterceptActivity.KIND_PAUSE, decision.item.id, null, null)
                is AccessDecision.HardBlock -> launchIntercept(InterceptActivity.KIND_BLOCK, decision.item.id, null, null)
                is AccessDecision.LimitReached -> launchIntercept(InterceptActivity.KIND_LIMIT, decision.item.id, decision.reason, null)
            }
        }
    }

    // --- In-app feature blocking ---
    private fun maybeScanFeatures(pkg: String) {
        val features = FeatureCatalog.byPackage(pkg)
        if (features.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastFeatureScan < SCAN_THROTTLE_MS) return
        lastFeatureScan = now

        scope.launch {
            val enabledKeys = wardenContainer.settings.enabledFeatureKeys.first()
            val active = features.filter { it.key in enabledKeys }
            if (active.isEmpty()) return@launch
            if (!wardenContainer.blockEngine.isBlockingActiveNow()) return@launch

            val hit = FeatureDetector.detect(active, A11yNode(rootInActiveWindow))
            if (hit == null) {
                interceptedFeatureKey = null
                return@launch
            }
            if (hit.key == interceptedFeatureKey) return@launch // already handled; avoid re-launch loop
            interceptedFeatureKey = hit.key
            launchIntercept(InterceptActivity.KIND_FEATURE, -1L, null, hit)
        }
    }

    private fun launchIntercept(kind: String, itemId: Long, reason: LimitReason?, feature: AppFeature?) {
        val intent = Intent(this, InterceptActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(InterceptActivity.EXTRA_KIND, kind)
            .putExtra(InterceptActivity.EXTRA_ITEM_ID, itemId)
            .putExtra(InterceptActivity.EXTRA_REASON, reason?.name)
            .putExtra(InterceptActivity.EXTRA_FEATURE_LABEL, feature?.label)
        startActivity(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SCAN_THROTTLE_MS = 400L
    }
}
