package com.superapp.app.features.keyboard

import android.content.Context
import android.content.Intent

/**
 * Translation helper.
 *
 * LIMITATION (documented, not faked):
 * Android does not expose a public system-wide translation service that a
 * third-party IME can call silently to translate arbitrary text.
 * The only supported mechanism is to hand the text to any installed app that
 * declares ACTION_PROCESS_TEXT for "text/plain" (e.g. Google Translate,
 * DeepL, Microsoft Translator). That app shows its own UI, performs the
 * translation, and the user can paste the result back into the editor.
 *
 * This helper performs that hand-off, and reports clearly when no handler is
 * available. It does not implement a fake offline translation engine.
 */
object TranslationHelper {

    fun hasExternalTranslator(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply { type = "text/plain" }
        return context.packageManager
            .queryIntentActivities(intent, 0)
            .isNotEmpty()
    }

    /**
     * Opens an installed ACTION_PROCESS_TEXT handler with [text] pre-filled.
     * Returns Result.failure if no handler exists or the launch fails.
     */
    fun translateViaExternalApp(context: Context, text: String): Result<Unit> {
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("Nothing to translate"))
        }
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }
    }
}
