package com.superapp.app.features.password.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Copies secrets flagged as sensitive (hides the preview on Android 13+) and clears them again
 * after [CLEAR_DELAY_MS] or when the vault locks.
 */
object VaultClipboard {
    private const val LABEL = "superapp_vault"
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE" // ClipDescription.EXTRA_IS_SENSITIVE (API 33)
    private const val CLEAR_DELAY_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun copy(context: Context, text: String, sensitive: Boolean) {
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(if (sensitive) LABEL else "vault_name", text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        }
        cm.setPrimaryClip(clip)
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        if (sensitive) {
            val r = Runnable { clearNow(app) }
            pending = r
            handler.postDelayed(r, CLEAR_DELAY_MS)
        }
    }

    /**
     * Clears the clipboard if it still holds a vault secret. If Android hides the clip description from
     * us (background clipboard restrictions) we clear anyway: leaking a password is worse than losing a copy.
     */
    fun clearNow(context: Context) {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        try {
            val cm = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val label = cm.primaryClipDescription?.label
            if (label == null || label == LABEL) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
                else cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {
        }
    }
}
