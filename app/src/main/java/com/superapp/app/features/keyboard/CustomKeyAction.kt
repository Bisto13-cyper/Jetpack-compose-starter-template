package com.superapp.app.features.keyboard

/**
 * Custom programmable keys.
 *
 * IMPORTANT: Android IMEs cannot inject arbitrary physical key combinations
 * (Ctrl+C, Ctrl+V, Shift+P, ...) into arbitrary apps. InputConnection only
 * allows committing text, deleting text, and sending a limited set of
 * KeyEvents. We therefore expose only actions Android actually supports:
 *
 *   - Text insertion
 *   - Editor actions via performContextMenuAction (select-all/copy/cut/paste)
 *   - sendKeyEvent for a narrow set of KEYCODE_* values (arrows, delete,
 *     tab, enter)
 *   - Undo / Redo attempted via Ctrl+Z / Ctrl+Y — best-effort, apps may
 *     ignore it.
 */
enum class CustomKeyKind {
    TEXT,
    SELECT_ALL,
    COPY,
    CUT,
    PASTE,
    UNDO,
    REDO,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    ARROW_DOWN,
    DELETE_FORWARD,
    ENTER,
    TAB
}

data class CustomKey(
    val id: String,
    val label: String,
    val kind: CustomKeyKind,
    val text: String = ""
)

object CustomKeyCatalog {
    val defaults: List<CustomKey> = listOf(
        CustomKey("ck_select_all", "Select all", CustomKeyKind.SELECT_ALL),
        CustomKey("ck_copy", "Copy", CustomKeyKind.COPY),
        CustomKey("ck_cut", "Cut", CustomKeyKind.CUT),
        CustomKey("ck_paste", "Paste", CustomKeyKind.PASTE),
        CustomKey("ck_undo", "Undo", CustomKeyKind.UNDO),
        CustomKey("ck_redo", "Redo", CustomKeyKind.REDO),
        CustomKey("ck_left", "◀", CustomKeyKind.ARROW_LEFT),
        CustomKey("ck_right", "▶", CustomKeyKind.ARROW_RIGHT),
        CustomKey("ck_up", "▲", CustomKeyKind.ARROW_UP),
        CustomKey("ck_down", "▼", CustomKeyKind.ARROW_DOWN),
        CustomKey("ck_tab", "Tab", CustomKeyKind.TAB),
        CustomKey("ck_del_fwd", "Del", CustomKeyKind.DELETE_FORWARD)
    )
}
