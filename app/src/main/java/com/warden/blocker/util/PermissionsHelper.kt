package com.warden.blocker.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.warden.blocker.accessibility.AppBlockAccessibilityService
import com.warden.blocker.usage.UsageStatsHelper

/** Central checks for the special-access permissions Warden relies on. */
object PermissionsHelper {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val id = "${context.packageName}/${AppBlockAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(id, ignoreCase = true) }
    }

    fun hasUsageAccess(context: Context): Boolean = UsageStatsHelper.hasPermission(context)

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Accessibility is the one truly essential grant (app blocking + in-app feeds). */
    fun essentialsGranted(context: Context): Boolean = isAccessibilityEnabled(context)
}
