package com.superapp.app.features.editor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class EditorThemeMode { DARK, LIGHT, CUSTOM }

data class EditorSettings(
    val themeMode: EditorThemeMode = EditorThemeMode.DARK,
    val backgroundColor: Long = 0xFF1E1E1E,
    val textColor: Long = 0xFFD4D4D4,
    val keywordColor: Long = 0xFF569CD6,
    val typeColor: Long = 0xFF4EC9B0,
    val stringColor: Long = 0xFFCE9178,
    val numberColor: Long = 0xFFB5CEA8,
    val commentColor: Long = 0xFF6A9955,
    val operatorColor: Long = 0xFFD4D4D4,
    val functionColor: Long = 0xFFDCDCAA,
    val tagColor: Long = 0xFF569CD6,
    val attrColor: Long = 0xFF9CDCFE,
    val propertyColor: Long = 0xFF9CDCFE,
    val cursorColor: Long = 0xFFAEAFAD,
    val selectionColor: Long = 0xFF264F78,
    val lineNumberColor: Long = 0xFF858585,
    val currentLineColor: Long = 0xFF282828,
    val accentColor: Long = 0xFF4C8BF5,
    val fontSizeSp: Int = 14,
    val lineHeightMultiplier: Float = 1.35f,
    val tabWidth: Int = 4,
    val useTabs: Boolean = false,
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val autoCloseBrackets: Boolean = true,
    val autoCloseQuotes: Boolean = true,
    val autoIndent: Boolean = true,
    val highlightCurrentLine: Boolean = true,
    val largeFileLimitBytes: Int = 1_500_000
)

object EditorSettingsStore {
    private const val PREFS = "editor_settings"
    private var prefs: SharedPreferences? = null

    var settings by mutableStateOf(EditorSettings())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        settings = read(prefs!!)
    }

    fun update(transform: (EditorSettings) -> EditorSettings) {
        val next = transform(settings)
        settings = next
        write(next)
    }

    private fun read(p: SharedPreferences): EditorSettings {
        val d = EditorSettings()
        return EditorSettings(
            themeMode = runCatching { EditorThemeMode.valueOf(p.getString("theme", d.themeMode.name)!!) }
                .getOrDefault(d.themeMode),
            backgroundColor = p.getLong("bg", d.backgroundColor),
            textColor = p.getLong("text", d.textColor),
            keywordColor = p.getLong("kw", d.keywordColor),
            typeColor = p.getLong("ty", d.typeColor),
            stringColor = p.getLong("str", d.stringColor),
            numberColor = p.getLong("num", d.numberColor),
            commentColor = p.getLong("cm", d.commentColor),
            operatorColor = p.getLong("op", d.operatorColor),
            functionColor = p.getLong("fn", d.functionColor),
            tagColor = p.getLong("tag", d.tagColor),
            attrColor = p.getLong("atr", d.attrColor),
            propertyColor = p.getLong("prop", d.propertyColor),
            cursorColor = p.getLong("cursor", d.cursorColor),
            selectionColor = p.getLong("sel", d.selectionColor),
            lineNumberColor = p.getLong("ln", d.lineNumberColor),
            currentLineColor = p.getLong("cl", d.currentLineColor),
            accentColor = p.getLong("accent", d.accentColor),
            fontSizeSp = p.getInt("fs", d.fontSizeSp),
            lineHeightMultiplier = p.getFloat("lh", d.lineHeightMultiplier),
            tabWidth = p.getInt("tw", d.tabWidth),
            useTabs = p.getBoolean("ut", d.useTabs),
            wordWrap = p.getBoolean("ww", d.wordWrap),
            showLineNumbers = p.getBoolean("sln", d.showLineNumbers),
            autoCloseBrackets = p.getBoolean("acb", d.autoCloseBrackets),
            autoCloseQuotes = p.getBoolean("acq", d.autoCloseQuotes),
            autoIndent = p.getBoolean("ai", d.autoIndent),
            highlightCurrentLine = p.getBoolean("hcl", d.highlightCurrentLine),
            largeFileLimitBytes = p.getInt("lfl", d.largeFileLimitBytes)
        )
    }

    private fun write(s: EditorSettings) {
        prefs?.edit()?.apply {
            putString("theme", s.themeMode.name)
            putLong("bg", s.backgroundColor)
            putLong("text", s.textColor)
            putLong("kw", s.keywordColor)
            putLong("ty", s.typeColor)
            putLong("str", s.stringColor)
            putLong("num", s.numberColor)
            putLong("cm", s.commentColor)
            putLong("op", s.operatorColor)
            putLong("fn", s.functionColor)
            putLong("tag", s.tagColor)
            putLong("atr", s.attrColor)
            putLong("prop", s.propertyColor)
            putLong("cursor", s.cursorColor)
            putLong("sel", s.selectionColor)
            putLong("ln", s.lineNumberColor)
            putLong("cl", s.currentLineColor)
            putLong("accent", s.accentColor)
            putInt("fs", s.fontSizeSp)
            putFloat("lh", s.lineHeightMultiplier)
            putInt("tw", s.tabWidth)
            putBoolean("ut", s.useTabs)
            putBoolean("ww", s.wordWrap)
            putBoolean("sln", s.showLineNumbers)
            putBoolean("acb", s.autoCloseBrackets)
            putBoolean("acq", s.autoCloseQuotes)
            putBoolean("ai", s.autoIndent)
            putBoolean("hcl", s.highlightCurrentLine)
            putInt("lfl", s.largeFileLimitBytes)
            apply()
        }
    }
}
