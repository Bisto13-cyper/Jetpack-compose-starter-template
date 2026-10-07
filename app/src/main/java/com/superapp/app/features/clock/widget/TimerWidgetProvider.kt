package com.superapp.app.features.clock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.superapp.app.R
import com.superapp.app.features.clock.AlarmScheduler
import com.superapp.app.features.clock.ClockStore
import com.superapp.app.features.clock.ClockText
import com.superapp.app.features.clock.TimerController
import com.superapp.app.features.clock.TimerStatus

/**
 * Home-screen widget: header with the current timer (a live countdown via Chronometer, so it
 * needs no periodic updates) and a list of enabled saved timers; tapping a row starts it.
 * Updated only on state changes (updatePeriodMillis = 0) to respect widget update limits.
 */
class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, buildViews(context, it)) }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.clock_widget_list)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_START_SAVED) {
            val savedId = intent.getStringExtra(EXTRA_SAVED_ID) ?: return
            ClockStore.get(context).loadSaved()
                .firstOrNull { it.id == savedId && it.enabled }
                ?.let { TimerController.start(context, it.name, it.durationMs, it.soundUri, it.id) }
        }
    }

    companion object {
        const val ACTION_START_SAVED = "com.superapp.app.clock.widget.ACTION_START_SAVED"
        const val EXTRA_SAVED_ID = "clock_widget_saved_id"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { manager.updateAppWidget(it, buildViews(context, it)) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.clock_widget_list)
        }

        private fun buildViews(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.clock_widget_timer)

            // Collection of saved timers.
            val adapter = Intent(context, TimerWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.clock_widget_list, adapter)
            views.setEmptyView(R.id.clock_widget_list, R.id.clock_widget_empty)

            // Row tap -> broadcast back to this provider with the saved timer id filled in.
            val template = Intent(context, TimerWidgetProvider::class.java).setAction(ACTION_START_SAVED)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            views.setPendingIntentTemplate(
                R.id.clock_widget_list,
                PendingIntent.getBroadcast(context, widgetId, template, flags)
            )

            // Header: open the app on the timers screen.
            val open = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
                .putExtra(AlarmScheduler.EXTRA_OPEN_CLOCK, "timers")
            views.setOnClickPendingIntent(
                R.id.clock_widget_header,
                PendingIntent.getActivity(
                    context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Current timer state.
            val now = System.currentTimeMillis()
            val active = ClockStore.get(context).loadActive()
            val running = active.filter { it.status == TimerStatus.RUNNING }.minByOrNull { it.remaining(now) }
            val shown = running ?: active.firstOrNull { it.status == TimerStatus.PAUSED }
                ?: active.firstOrNull()

            views.setViewVisibility(R.id.clock_widget_chrono, View.GONE)
            when {
                running != null -> {
                    views.setTextViewText(R.id.clock_widget_status, running.name)
                    views.setViewVisibility(R.id.clock_widget_chrono, View.VISIBLE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        views.setChronometerCountDown(R.id.clock_widget_chrono, true)
                    }
                    views.setChronometer(
                        R.id.clock_widget_chrono,
                        SystemClock.elapsedRealtime() + running.remaining(now),
                        null,
                        true
                    )
                }
                shown != null && shown.status == TimerStatus.PAUSED ->
                    views.setTextViewText(
                        R.id.clock_widget_status,
                        "${shown.name} - paused ${ClockText.duration(shown.remainingMs)}"
                    )
                shown != null ->
                    views.setTextViewText(R.id.clock_widget_status, "${shown.name} - finished")
                else -> views.setTextViewText(R.id.clock_widget_status, "No timer running")
            }
            return views
        }
    }
}
