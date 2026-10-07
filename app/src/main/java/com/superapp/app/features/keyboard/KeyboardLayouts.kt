package com.superapp.app.features.keyboard

sealed interface KeyAction {
    data class Text(val value: String) : KeyAction
    object Shift : KeyAction
    object Backspace : KeyAction
    object Enter : KeyAction
    object Space : KeyAction
    object LanguageSwitch : KeyAction
    object Symbols : KeyAction
    object Letters : KeyAction
    data class Custom(val kind: CustomKeyKind) : KeyAction
}

data class KeyDef(
    val id: String,
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val isSpecial: Boolean = false
)

/**
 * Pure functions that produce the key rows for the keyboard.
 * No UI code lives here so layouts can be reused and unit tested.
 */
object KeyboardLayouts {

    fun rows(kind: KeyboardLayoutKind, symbols: Boolean): List<List<KeyDef>> =
        if (symbols) symbolRows() else letterRows(kind)

    private fun letterRows(kind: KeyboardLayoutKind): List<List<KeyDef>> {
        val top = when (kind) {
            KeyboardLayoutKind.QWERTY -> "qwertyuiop"
            KeyboardLayoutKind.QWERTZ -> "qwertzuiop"
            KeyboardLayoutKind.AZERTY -> "azertyuiop"
        }
        val mid = when (kind) {
            KeyboardLayoutKind.QWERTY -> "asdfghjkl"
            KeyboardLayoutKind.QWERTZ -> "asdfghjkl"
            KeyboardLayoutKind.AZERTY -> "qsdfghjklm"
        }
        val bot = when (kind) {
            KeyboardLayoutKind.QWERTY -> "zxcvbnm"
            KeyboardLayoutKind.QWERTZ -> "zxcvbnm"
            KeyboardLayoutKind.AZERTY -> "wxcvbn"
        }

        val rows = mutableListOf<List<KeyDef>>()

        rows.add(top.mapIndexed { i, c ->
            KeyDef("l_top_$i", c.toString(), KeyAction.Text(c.toString()))
        })
        rows.add(mid.mapIndexed { i, c ->
            KeyDef("l_mid_$i", c.toString(), KeyAction.Text(c.toString()))
        })

        val bottom = mutableListOf<KeyDef>()
        bottom.add(KeyDef("l_shift", "⇧", KeyAction.Shift, weight = 1.5f, isSpecial = true))
        bot.forEachIndexed { i, c ->
            bottom.add(KeyDef("l_bot_$i", c.toString(), KeyAction.Text(c.toString())))
        }
        bottom.add(KeyDef("l_backspace", "⌫", KeyAction.Backspace, weight = 1.5f, isSpecial = true))
        rows.add(bottom)

        rows.add(
            listOf(
                KeyDef("l_symbols", "?123", KeyAction.Symbols, weight = 1.5f, isSpecial = true),
                KeyDef("l_lang", "🌐", KeyAction.LanguageSwitch, weight = 1f, isSpecial = true),
                KeyDef("l_space", "space", KeyAction.Space, weight = 5f, isSpecial = true),
                KeyDef("l_enter", "⏎", KeyAction.Enter, weight = 1.5f, isSpecial = true)
            )
        )
        return rows
    }

    private fun symbolRows(): List<List<KeyDef>> {
        val r1 = "1234567890".mapIndexed { i, c ->
            KeyDef("s_r1_$i", c.toString(), KeyAction.Text(c.toString()))
        }
        val r2 = listOf("!", "@", "#", "$", "%", "&", "-", "_", "=", "+").mapIndexed { i, s ->
            KeyDef("s_r2_$i", s, KeyAction.Text(s))
        }
        val r3 = listOf("(", ")", "[", "]", "{", "}", ";", ":", "\"", "'").mapIndexed { i, s ->
            KeyDef("s_r3_$i", s, KeyAction.Text(s))
        }

        val r4 = mutableListOf<KeyDef>()
        r4.add(KeyDef("s_letters", "ABC", KeyAction.Letters, weight = 1.5f, isSpecial = true))
        listOf(",", ".", "?", "/", "\\", "|", "<", ">", "~", "*").forEachIndexed { i, s ->
            r4.add(KeyDef("s_r4_$i", s, KeyAction.Text(s)))
        }
        r4.add(KeyDef("s_backspace", "⌫", KeyAction.Backspace, weight = 1.5f, isSpecial = true))

        val r5 = listOf(
            KeyDef("s_symbols", "?123", KeyAction.Symbols, weight = 1.5f, isSpecial = true),
            KeyDef("s_lang", "🌐", KeyAction.LanguageSwitch, weight = 1f, isSpecial = true),
            KeyDef("s_space", "space", KeyAction.Space, weight = 5f, isSpecial = true),
            KeyDef("s_enter", "⏎", KeyAction.Enter, weight = 1.5f, isSpecial = true)
        )

        return listOf(r1, r2, r3, r4, r5)
    }
}
