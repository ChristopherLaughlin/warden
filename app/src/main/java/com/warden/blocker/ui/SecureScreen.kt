package com.warden.blocker.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.warden.blocker.BuildConfig

/**
 * Marks the current screen as security-sensitive while it's composed:
 * - FLAG_SECURE (release builds only, so debug can still be screenshotted) blocks
 *   screenshots / screen recording of the PIN.
 * - filterTouchesWhenObscured + setHideOverlayWindows defeat tapjacking overlays that could
 *   trick the user into entering or revealing the PIN.
 */
@Composable
fun SecureScreen() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val applySecure = !BuildConfig.DEBUG
        if (applySecure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window?.decorView?.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window?.setHideOverlayWindows(true)
        onDispose {
            if (applySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            window?.decorView?.filterTouchesWhenObscured = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window?.setHideOverlayWindows(false)
        }
    }
}
