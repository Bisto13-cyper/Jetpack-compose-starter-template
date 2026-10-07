package com.superapp.app.features.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.superapp.app.features.clock.widget.TimerWidgetProvider

/**
 * All timer state transitions. State is persisted in [ClockStore] and completion is delivered by
 * AlarmManager, so timers keep working when the app is in the background or its process is gone.
 * End times are wall-clock based so they survive reboot (changing the device clock shifts them).
 */
object TimerController {

    const val ACTION_TIMER = "com.superapp.app.clock.ACTION_TIMER"
    const val EXTRA_TIMER_ID = "clock_timer_id"

    /** Starts a timer. Returns null when the duration is invalid or this preset is already active. */
    fun start(
        context: Context,
        name: String,
        durationMs: Long,
        soundUri: String?,
        savedId: String? = null
    ): ActiveTimer? {
        if (durationMs <= 0L) return null
        val store = ClockStore.get(context)
        if (savedId != null &&
            store.loadActive().any { it.savedId == savedId && it.status != TimerStatus.FINISHED }
        ) return null

        val timer = ActiveTimer(
            name = name.ifBlank { "Timer" },
            durationMs = durationMs,
            soundUri = soundUri,
            status = TimerStatus.RUNNING,
            endAtWallMs = System.currentTimeMillis() + durationMs,
            remainingMs = durationMs,
            savedId = savedId
        )
        store.updateActive { list ->
            list.filterNot { savedId != null && it.savedId == savedId && it.status == TimerStatus.FINISHED } + timer
        }
        schedule(context, timer)
        TimerWidgetProvider.refreshAll(context)
        return timer
    }

    fun pause(context: Context, id: String) {
        val now = System.currentTimeMillis()
        ClockStore.get(context).updateActive { list ->
            list.map {
                if (it.id == id && it.status == TimerStatus.RUNNING)
                    it.copy(status = TimerStatus.PAUSED, remainingMs = it.remaining(now))
                else it
            }
        }
        cancelAlarm(context, id)
        TimerWidgetProvider.refreshAll(context)
    }

    fun resume(context: Context, id: String) {
        val now = System.currentTimeMillis()
        var resumed: ActiveTimer? = null
        ClockStore.get(context).updateActive { list ->
            list.map {
                if (it.id == id && it.status == TimerStatus.PAUSED && it.remainingMs > 0L) {
                    it.copy(status = TimerStatus.RUNNING, endAtWallMs = now + it.remainingMs).also { t -> resumed = t }
                } else it
            }
        }
        resumed?.let { schedule(context, it) }
        TimerWidgetProvider.refreshAll(context)
    }

    /** Stops the timer and returns it to its full duration, paused and ready to start again. */
    fun reset(context: Context, id: String) {
        cancelAlarm(context, id)
        ClockStore.get(context).updateActive { list ->
            list.map {
                if (it.id == id) it.copy(status = TimerStatus.PAUSED, remainingMs = it.durationMs, endAtWallMs = 0L)
                else it
            }
        }
        AlarmRingService.stop(context)
        TimerWidgetProvider.refreshAll(context)
    }

    /** Removes the timer entirely (also dismisses a finished timer). */
    fun cancel(context: Context, id: String) {
        cancelAlarm(context, id)
        ClockStore.get(context).updateActive { list -> list.filterNot { it.id == id } }
        AlarmRingService.stop(context)
        TimerWidgetProvider.refreshAll(context)
    }

    fun onTimerFired(context: Context, id: String) {
        var fired: ActiveTimer? = null
        ClockStore.get(context).updateActive { list ->
            list.map {
                if (it.id == id && it.status == TimerStatus.RUNNING) {
                    it.copy(status = TimerStatus.FINISHED, remainingMs = 0L).also { t -> fired = t }
                } else it
            }
        }
        fired?.let {
            AlarmRingService.start(
                context,
                RingRequest(
                    kind = RingRequest.KIND_TIMER,
                    title = it.name,
                    message = "Time's up",
                    soundUri = it.soundUri,
                    volumePercent = 100,
                    vibrate = true,
                    snoozeMinutes = 0
                )
            )
        }
        TimerWidgetProvider.refreshAll(context)
    }

    /** Re-arms running timers after boot / app update. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        ClockStore.get(context).loadActive()
            .filter { it.status == TimerStatus.RUNNING }
            .forEach { if (it.endAtWallMs <= now) onTimerFired(context, it.id) else schedule(context, it) }
    }

    private fun schedule(context: Context, timer: ActiveTimer) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, timer.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val at = timer.endAtWallMs
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms() ->
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else -> am.setExact(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(context: Context, id: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingIntent(context, id, PendingIntent.FLAG_NO_CREATE)?.let {
            am.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(context: Context, id: String, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TIMER
            putExtra(EXTRA_TIMER_ID, id)
        }
        return PendingIntent.getBroadcast(context, ("timer:$id").hashCode(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }
}
