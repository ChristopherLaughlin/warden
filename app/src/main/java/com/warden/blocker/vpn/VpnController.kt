package com.warden.blocker.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService

/** Thin facade the UI uses to start/stop the filtering tunnel. */
object VpnController {

    /**
     * Returns an intent to show the system VPN-consent dialog, or null if already granted.
     * The caller must launch it and call [start] once the result is OK.
     */
    fun consentIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        val intent = Intent(context, WardenVpnService::class.java)
            .setAction(WardenVpnService.ACTION_START)
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, WardenVpnService::class.java)
            .setAction(WardenVpnService.ACTION_STOP)
        context.startService(intent)
    }
}
