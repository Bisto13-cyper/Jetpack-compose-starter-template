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
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.superapp.app.core.debug.DebugLog

/**
 * Custom IME entry point.
 *
 * The Compose UI is hosted in a ComposeView returned from onCreateInputView.
 * All editor interactions are funnelled through the helper methods below so
 * the Compose layer never touches InputConnection directly.
 */
class CustomKeyboardService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // An IME is a plain Service, so it has no lifecycle/saved-state owners.
    // Compose looks these up from the view tree and crashes with
    // "ViewTreeLifecycleOwner not found" if they are missing, so we provide them.
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        installKeyboardCrashLogging()
        runCatching {
            KeyboardSettingsStore.init(this)
            KeyboardClipboard.init(this)
        }.onFailure {
            Log.e("CustomKeyboardService", "Keyboard initialization failed", it)
            DebugLog.add(this, "KEYBOARD", "Initialization failed", it)
        }
    }

    private fun installKeyboardCrashLogging() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { DebugLog.add(this, "KEYBOARD_CRASH", "Uncaught exception on ${thread.name}", error) }
            previous?.uncaughtException(thread, error)
        }
    }

    override fun onCreateInputView(): View {
        return try {
            KeyboardClipboard.refreshFromSystem(this)
            attachViewTreeOwners(window?.window?.decorView)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            ComposeView(this).apply {
                attachViewTreeOwners(this)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    MaterialTheme { KeyboardRoot(this@CustomKeyboardService) }
                }
            }
        } catch (error: Throwable) {
            Log.e("CustomKeyboardService", "Could not create keyboard view", error)
            DebugLog.add(this, "KEYBOARD", "Could not create keyboard view", error)
            android.widget.TextView(this).apply { text = "Keyboard failed to load" }
        }
    }

    private fun attachViewTreeOwners(view: View?) {
        if (view == null) return
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        attachViewTreeOwners(window?.window?.decorView)
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
        KeyboardClipboard.refreshFromSystem(this)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Editor helpers used by the Compose UI
    // ------------------------------------------------------------------

    fun commitText(text: String) {
        runCatching { currentInputConnection?.commitText(text, 1) }
            .onFailure { DebugLog.add(this, "KEYBOARD_INPUT", "commitText failed", it) }
    }

    fun backspace() {
        val ic = currentInputConnection ?: return
        runCatching {
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) ic.commitText("", 1)
            else ic.deleteSurroundingText(1, 0)
        }.onFailure { DebugLog.add(this, "KEYBOARD_INPUT", "backspace failed", it) }
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
        val now = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(now,now,KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now,now,KeyEvent.ACTION_UP, keyCode, 0, meta))
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
