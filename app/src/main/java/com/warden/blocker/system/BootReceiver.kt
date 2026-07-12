package com.warden.blocker.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.warden.blocker.vpn.VpnController
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-arms the DNS filter after a reboot if the user had blocking enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Re-arm if blocking is active for any reason — master, schedule, or an
                // in-progress focus session. Consent must have been granted previously.
                val active = context.wardenContainer.blockEngine.isBlockingActiveNow()
                if (active && VpnController.consentIntent(context) == null) {
                    VpnController.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
