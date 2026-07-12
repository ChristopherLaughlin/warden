package com.warden.blocker.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.warden.blocker.vpn.VpnController
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-arms the DNS filter after a reboot if the user had blocking enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val enabled = context.wardenContainer.settings.masterEnabled.first()
                // VpnService.prepare must have been granted previously; if so this succeeds silently.
                if (enabled && VpnController.consentIntent(context) == null) {
                    VpnController.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
