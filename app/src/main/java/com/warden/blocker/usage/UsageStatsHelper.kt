package com.warden.blocker.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

data class AppUsage(val packageName: String, val totalMillis: Long)

/** Reads screen-time from UsageStatsManager. Requires the PACKAGE_USAGE_STATS special access. */
object UsageStatsHelper {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground minutes for a single package since midnight today (0 if none / no permission). */
    fun todayUsageMinutesFor(context: Context, packageName: String): Long {
        if (!hasPermission(context)) return 0
        return todayUsage(context).firstOrNull { it.packageName == packageName }
            ?.let { it.totalMillis / 60_000L } ?: 0
    }

    /** Per-app foreground time since midnight today, most-used first. */
    fun todayUsage(context: Context): List<AppUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = System.currentTimeMillis()
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .map { (pkg, stats) -> AppUsage(pkg, stats.sumOf { it.totalTimeInForeground }) }
            .sortedByDescending { it.totalMillis }
    }
}
