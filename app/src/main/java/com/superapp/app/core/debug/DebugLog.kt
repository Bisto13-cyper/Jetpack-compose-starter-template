package com.superapp.app.core.debug

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small local-only diagnostic ring buffer. Never pass passwords or user-entered text here. */
object DebugLog {
    private const val PREFS = "local_debug_log"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 250

    @Synchronized
    fun add(context: Context, category: String, message: String, error: Throwable? = null) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val detail = error?.let { "\n${it.javaClass.name}: ${it.message}\n${it.stackTraceToString()}" }.orEmpty()
            val old = prefs.getString(KEY, "").orEmpty().split("\n---\n").filter { it.isNotBlank() }
            val next = (old + "[$stamp][$category] $message$detail").takeLast(MAX_ENTRIES)
            prefs.edit().putString(KEY, next.joinToString("\n---\n")).apply()
        }
    }

    fun read(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "")
        .orEmpty().ifBlank { "No diagnostic events recorded yet." }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
