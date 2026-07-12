package com.warden.blocker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.warden.blocker.block.BlockEngine
import com.warden.blocker.data.BlockRepository
import com.warden.blocker.data.SettingsStore
import com.warden.blocker.data.WardenDatabase

/**
 * Poor-man's DI: a single container of app-scoped singletons reachable from
 * services and activities via [WardenApp.container]. Keeps v1 dependency-free;
 * swap for Hilt later if the graph grows.
 */
class WardenApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            VPN_CHANNEL_ID,
            getString(R.string.vpn_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val VPN_CHANNEL_ID = "warden_blocking"
    }
}

class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val repository = BlockRepository(WardenDatabase.get(app).blockDao())
    val blockEngine = BlockEngine(repository, settings)
}

/** Convenience accessor from any Context. */
val android.content.Context.wardenContainer: AppContainer
    get() = (applicationContext as WardenApp).container
