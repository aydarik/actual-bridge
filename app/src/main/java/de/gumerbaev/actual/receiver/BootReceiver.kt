package de.gumerbaev.actual.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import de.gumerbaev.actual.service.ActualNotificationListenerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            if (isNotificationServiceEnabled(context)) {
                toggleNotificationListenerService(context)
                Toast.makeText(
                    context,
                    "Actual Bridge Service activated",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Checks if the user has granted Notification Access permission in Settings.
     */
    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat != null && flat.contains(packageName)
    }

    /**
     * Toggles the component state to force Android's NotificationManagerService
     * to re-evaluate and re-bind the NotificationListenerService after reboot.
     */
    private fun toggleNotificationListenerService(context: Context) {
        val componentName = ComponentName(context, ActualNotificationListenerService::class.java)
        val packageManager = context.packageManager

        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}