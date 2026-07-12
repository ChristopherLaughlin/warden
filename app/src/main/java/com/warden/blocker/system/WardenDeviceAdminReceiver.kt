package com.warden.blocker.system

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Being an *active* device admin is what blocks the app from being uninstalled — Android
 * won't let you remove an app with an enabled admin until it's deactivated. Warden uses
 * this for one thing only: uninstall protection during strict mode. It requests no
 * password/wipe/lock policies.
 */
class WardenDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off removes Warden's uninstall protection. If you're trying to quit " +
            "during a block, consider waiting out the urge instead."
}
