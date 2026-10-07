package com.superapp.app.features.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/**
 * Custom IME entry point.
 *
 * The Compose UI is hosted in a ComposeView returned from onCreateInputView.
 * All editor interactions are funnelled through the helper methods below so
 * the Compose layer never touches InputConnection directly.
 */
class CustomKeyboardService : InputMethodService() {

    override fun onCreate() {
        super.onCreate()
        KeyboardSettingsStore.init(this)
        KeyboardClipboard.init(this)
    }

    override fun onCreateInputView(): View {
        KeyboardClipboard.refreshFromSystem(this)
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme {
                    KeyboardRoot(this@CustomKeyboardService)
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        KeyboardClipboard.refreshFromSystem(this)
    }

    // ------------------------------------------------------------------
    // Editor helpers used by the Compose UI
    // ------------------------------------------------------------------

    fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    fun backspace() {
        val ic = currentInputConnection ?: return
        val selected = runCatching { ic.getSelectedText(0) }.getOrNull()
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    fun sendEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    fun selectAll() { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }
    fun copy()      { currentInputConnection?.performContextMenuAction(android.R.id.copy) }
    fun cut()       { currentInputConnection?.performContextMenuAction(android.R.id.cut) }
    fun paste()     { currentInputConnection?.performContextMenuAction(android.R.id.paste) }

    /**
     * Best-effort undo/redo. Android has no public InputConnection undo API,
     * so we dispatch Ctrl+Z / Ctrl+Y KeyEvents. Many editors honour them,
     * some do not. This is documented behaviour, not a fake implementation.
     */
    fun undo() { currentInputConnection?.let { sendCtrlKey(it, KeyEvent.KEYCODE_Z) } }
    fun redo() { currentInputConnection?.let { sendCtrlKey(it, KeyEvent.KEYCODE_Y) } }

    private fun sendCtrlKey(ic: InputConnection, keyCode: Int) {
        val meta = KeyEvent.META_CTRL_ON
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    fun sendArrowLeft()  = sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
    fun sendArrowRight() = sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
    fun sendArrowUp()    = sendKey(KeyEvent.KEYCODE_DPAD_UP)
    fun sendArrowDown()  = sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
    fun sendDeleteForward() = sendKey(KeyEvent.KEYCODE_FORWARD_DEL)
    fun sendTab() = sendKey(KeyEvent.KEYCODE_TAB)

    private fun sendKey(keyCode: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    fun currentText(): String {
        val ic = currentInputConnection ?: return ""
        return runCatching {
            ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString().orEmpty()
        }.getOrDefault("")
    }

    fun selectedText(): String =
        runCatching { currentInputConnection?.getSelectedText(0)?.toString().orEmpty() }
            .getOrDefault("")

    /** Dispatches a CustomKeyKind action; unknown cases are ignored safely. */
    fun runCustomKey(kind: CustomKeyKind) {
        when (kind) {
            CustomKeyKind.TEXT -> Unit
            CustomKeyKind.SELECT_ALL -> selectAll()
            CustomKeyKind.COPY -> copy()
            CustomKeyKind.CUT -> cut()
            CustomKeyKind.PASTE -> paste()
            CustomKeyKind.UNDO -> undo()
            CustomKeyKind.REDO -> redo()
            CustomKeyKind.ARROW_LEFT -> sendArrowLeft()
            CustomKeyKind.ARROW_RIGHT -> sendArrowRight()
            CustomKeyKind.ARROW_UP -> sendArrowUp()
            CustomKeyKind.ARROW_DOWN -> sendArrowDown()
            CustomKeyKind.DELETE_FORWARD -> sendDeleteForward()
            CustomKeyKind.ENTER -> sendEnter()
            CustomKeyKind.TAB -> sendTab()
        }
    }

    /** Convenience for helpers (TranslationHelper etc.) that need a Context. */
    fun appContext(): Context = this
}
