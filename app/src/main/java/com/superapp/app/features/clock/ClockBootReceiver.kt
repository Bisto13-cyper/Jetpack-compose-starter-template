package com.superapp.app.features.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.superapp.app.features.clock.widget.TimerWidgetProvider

/**
 * AlarmManager forgets everything on reboot, app update, and (for wall-clock alarms) time or
 * time-zone changes, so the persisted configuration is re-applied here.
 */
class ClockBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                AlarmScheduler.rescheduleAll(context)
                TimerController.rescheduleAll(context)
                TimerWidgetProvider.refreshAll(context)
            }
        }
    }
}
