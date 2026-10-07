package com.superapp.app.features.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules alarms through AlarmManager.setAlarmClock(), the platform API meant for user-visible
 * alarm clocks: it is exact, is exempt from Doze/App Standby, needs no exact-alarm permission,
 * and shows the next alarm in the system UI. Every alarm has its own PendingIntent.
 */
object AlarmScheduler {

    const val ACTION_ALARM = "com.superapp.app.clock.ACTION_ALARM"
    const val ACTION_SNOOZE_FIRE = "com.superapp.app.clock.ACTION_SNOOZE_FIRE"
    const val EXTRA_GROUP_ID = "clock_group_id"
    const val EXTRA_ALARM_ID = "clock_alarm_id"
    const val EXTRA_OPEN_CLOCK = "com.superapp.app.OPEN_CLOCK"

    /** Next time [item] should ring, strictly after [nowMs]. */
    fun nextTriggerMillis(item: AlarmItem, nowMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, item.hour)
            set(Calendar.MINUTE, item.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (item.days.isEmpty()) {
            if (cal.timeInMillis <= nowMs) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        repeat(8) {
            if (cal.timeInMillis > nowMs && cal.get(Calendar.DAY_OF_WEEK) in item.days) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** Earliest upcoming enabled alarm across all enabled groups, or null. */
    fun nextAlarm(groups: List<AlarmGroup>, nowMs: Long = System.currentTimeMillis()): Pair<AlarmGroup, Long>? =
        groups.filter { it.enabled }
            .flatMap { g -> g.alarms.filter { it.enabled }.map { g to nextTriggerMillis(it, nowMs) } }
            .minByOrNull { it.second }

    fun schedule(context: Context, group: AlarmGroup, item: AlarmItem) {
        if (!group.enabled || !item.enabled) {
            cancel(context, item)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = pendingIntent(context, group.id, item.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val trigger = nextTriggerMillis(item)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showIntent(context)), operation)
    }

    fun cancel(context: Context, item: AlarmItem) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingIntent(context, "", item.id, PendingIntent.FLAG_NO_CREATE)?.let {
            am.cancel(it)
            it.cancel()
        }
    }

    fun cancelGroup(context: Context, group: AlarmGroup) = group.alarms.forEach { cancel(context, it) }

    /** Re-applies the whole persisted configuration (app start, boot, time/zone change). */
    fun rescheduleAll(context: Context) {
        ClockStore.get(context).loadGroups().forEach { g ->
            g.alarms.forEach { schedule(context, g, it) }
        }
    }

    /** Re-schedules one snooze for [request] after its snoozeMinutes. */
    fun scheduleSnooze(context: Context, request: RingRequest) {
        if (request.snoozeMinutes <= 0) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_FIRE
            request.putInto(this)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ("snooze:" + request.title).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val trigger = System.currentTimeMillis() + request.snoozeMinutes * 60_000L
        am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showIntent(context)), pi)
    }

    private fun pendingIntent(context: Context, groupId: String, alarmId: String, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_GROUP_ID, groupId)
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(context, alarmId.hashCode(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showIntent(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        launch.putExtra(EXTRA_OPEN_CLOCK, "alarms")
        return PendingIntent.getActivity(
            context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
