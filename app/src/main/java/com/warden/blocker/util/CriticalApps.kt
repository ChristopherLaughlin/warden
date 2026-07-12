package com.warden.blocker.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Apps that must never be blockable — blocking them locks the user out of their own phone
 * (a recurring, much-complained-about failure mode of other blockers). We refuse to add
 * these in the picker and never intercept them at runtime.
 */
object CriticalApps {
    private val CORE = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.android.cellbroadcastreceiver",
    )

    fun isCritical(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        if (packageName in CORE) return true
        return packageName == defaultLauncher(context)
    }

    private fun defaultLauncher(context: Context): String? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }.getOrNull()
}
