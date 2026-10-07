package com.superapp.app.features.clock

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence for alarm groups, saved timers and active timers.
 * Uses SharedPreferences + org.json (no extra dependencies). Updates are atomic per process,
 * so receivers, services and the UI can all modify it safely.
 */
class ClockStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    fun loadGroups(): List<AlarmGroup> = read(KEY_GROUPS, ::groupFrom)
    fun loadSaved(): List<SavedTimer> = read(KEY_SAVED, ::savedFrom)
    fun loadActive(): List<ActiveTimer> = read(KEY_ACTIVE, ::activeFrom)

    fun updateGroups(transform: (List<AlarmGroup>) -> List<AlarmGroup>) = synchronized(lock) {
        write(KEY_GROUPS, transform(loadGroups()).map(::groupTo))
    }

    fun updateSaved(transform: (List<SavedTimer>) -> List<SavedTimer>) = synchronized(lock) {
        write(KEY_SAVED, transform(loadSaved()).map(::savedTo))
    }

    fun updateActive(transform: (List<ActiveTimer>) -> List<ActiveTimer>) = synchronized(lock) {
        write(KEY_ACTIVE, transform(loadActive()).map(::activeTo))
    }

    private fun <T> read(key: String, parse: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(key: String, items: List<JSONObject>) {
        prefs.edit().putString(key, JSONArray(items).toString()).apply()
    }

    private fun intSet(arr: JSONArray?): Set<Int> =
        if (arr == null) emptySet() else (0 until arr.length()).map { arr.getInt(it) }.toSet()

    private fun optString(o: JSONObject, key: String): String? =
        if (o.isNull(key) || !o.has(key)) null else o.getString(key)

    private fun alarmTo(a: AlarmItem) = JSONObject()
        .put("id", a.id).put("hour", a.hour).put("minute", a.minute)
        .put("enabled", a.enabled).put("days", JSONArray(a.days.toList()))

    private fun alarmFrom(o: JSONObject) = AlarmItem(
        id = o.getString("id"),
        hour = o.getInt("hour"),
        minute = o.getInt("minute"),
        enabled = o.optBoolean("enabled", true),
        days = intSet(o.optJSONArray("days"))
    )

    private fun groupTo(g: AlarmGroup) = JSONObject()
        .put("id", g.id).put("name", g.name).put("enabled", g.enabled)
        .put("sound", g.soundUri ?: JSONObject.NULL)
        .put("volume", g.volumePercent).put("vibrate", g.vibrate)
        .put("snooze", g.snoozeMinutes)
        .put("defaultDays", JSONArray(g.defaultDays.toList()))
        .put("alarms", JSONArray(g.alarms.map(::alarmTo)))

    private fun groupFrom(o: JSONObject): AlarmGroup {
        val arr = o.optJSONArray("alarms") ?: JSONArray()
        return AlarmGroup(
            id = o.getString("id"),
            name = o.optString("name", "Alarms"),
            enabled = o.optBoolean("enabled", true),
            soundUri = optString(o, "sound"),
            volumePercent = o.optInt("volume", 100),
            vibrate = o.optBoolean("vibrate", true),
            snoozeMinutes = o.optInt("snooze", 10),
            defaultDays = intSet(o.optJSONArray("defaultDays")),
            alarms = (0 until arr.length()).map { alarmFrom(arr.getJSONObject(it)) }
        )
    }

    private fun savedTo(s: SavedTimer) = JSONObject()
        .put("id", s.id).put("name", s.name).put("duration", s.durationMs)
        .put("sound", s.soundUri ?: JSONObject.NULL).put("enabled", s.enabled)

    private fun savedFrom(o: JSONObject) = SavedTimer(
        id = o.getString("id"),
        name = o.optString("name", "Timer"),
        durationMs = o.getLong("duration"),
        soundUri = optString(o, "sound"),
        enabled = o.optBoolean("enabled", true)
    )

    private fun activeTo(t: ActiveTimer) = JSONObject()
        .put("id", t.id).put("name", t.name).put("duration", t.durationMs)
        .put("sound", t.soundUri ?: JSONObject.NULL).put("status", t.status.name)
        .put("endAt", t.endAtWallMs).put("remaining", t.remainingMs)
        .put("savedId", t.savedId ?: JSONObject.NULL)

    private fun activeFrom(o: JSONObject) = ActiveTimer(
        id = o.getString("id"),
        name = o.optString("name", "Timer"),
        durationMs = o.getLong("duration"),
        soundUri = optString(o, "sound"),
        status = runCatching { TimerStatus.valueOf(o.getString("status")) }.getOrDefault(TimerStatus.PAUSED),
        endAtWallMs = o.optLong("endAt", 0L),
        remainingMs = o.optLong("remaining", o.getLong("duration")),
        savedId = optString(o, "savedId")
    )

    companion object {
        private const val PREFS = "clock_store"
        private const val KEY_GROUPS = "groups"
        private const val KEY_SAVED = "saved_timers"
        private const val KEY_ACTIVE = "active_timers"

        @Volatile private var instance: ClockStore? = null

        fun get(context: Context): ClockStore =
            instance ?: synchronized(this) {
                instance ?: ClockStore(context).also { instance = it }
            }
    }
}
