package com.warden.blocker.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.warden.blocker.vpn.VpnController
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs when a focus session is scheduled to end. Clears the (expired) session and, if
 * blocking is no longer active for any other reason, tears the VPN back down.
 */
class FocusEndWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.wardenContainer
        val endsAt = container.settings.focusEndsAt.first()
        if (endsAt != 0L && endsAt <= System.currentTimeMillis()) {
            container.settings.clearFocus()
        }
        if (!container.blockEngine.isBlockingActiveNow()) {
            VpnController.stop(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "focus_end"

        fun schedule(context: Context, delayMillis: Long) {
            val request = OneTimeWorkRequestBuilder<FocusEndWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
