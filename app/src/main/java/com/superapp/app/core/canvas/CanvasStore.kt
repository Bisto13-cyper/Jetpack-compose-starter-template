package com.superapp.app.core.canvas

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * CANVAS FILE 1 - Remembers how the home canvas is arranged.
 *
 * It keeps:
 *  - hidden      : circles the user chose to hide (Canvas > Visibility)
 *  - positions   : circles the user dragged to a custom spot (Canvas > Drag & Drop)
 *  - ringSpread  : how far the automatic ring spreads out (Canvas > Node Layout)
 *  - centerScale : size of the center circle (Canvas > Node Layout)
 *  - editing     : true while the user is dragging circles on Home.
 *                  This one is NOT saved, so the app never starts in edit mode.
 *
 * It is a single shared object, so the Home screen and the Settings pages
 * always see the same data:
 *     val store = CanvasStore.get(context)
 *     val state by store.state.collectAsState()
 *     store.update { it.copy(ringSpread = 0.9f) }
 *
 * Positions are saved as fractions of the screen area (not pixels), so the
 * layout still looks right on other screen sizes and orientations.
 * Everything is stored privately on the phone.
 *
 * HOW TO ADD A NEW CANVAS SETTING:
 *  1. Add the field to CanvasState
 *  2. Add one line in save() and one in load()
 */

/** A custom spot: distance from the center, as a fraction of the half-width / half-height. */
data class NodePosition(val x: Float, val y: Float)

data class CanvasState(
    val hidden: Set<String> = emptySet(),
    val positions: Map<String, NodePosition> = emptyMap(),
    val ringSpread: Float = 1f,
    val centerScale: Float = 1f,
    val editing: Boolean = false
)

class CanvasStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())

    /** The current canvas layout. Collect it in the UI to react to changes. */
    val state: StateFlow<CanvasState> = _state.asStateFlow()

    /** Change the layout and save it. Example: store.update { it.copy(centerScale = 1.2f) } */
    fun update(change: (CanvasState) -> CanvasState) {
        val updated = change(_state.value)
        _state.value = updated
        save(updated)
    }

    // ---------- storage ----------

    private fun save(s: CanvasState) {
        val positions = JSONObject()
        s.positions.forEach { (id, p) ->
            positions.put(id, org.json.JSONArray().put(p.x.toDouble()).put(p.y.toDouble()))
        }

        prefs.edit()
            .putStringSet(K_HIDDEN, s.hidden.toSet())
            .putString(K_POSITIONS, positions.toString())
            .putFloat(K_RING_SPREAD, s.ringSpread)
            .putFloat(K_CENTER_SCALE, s.centerScale)
            .apply()
    }

    private fun load(): CanvasState {
        val defaults = CanvasState()

        val positions = runCatching {
            val json = JSONObject(prefs.getString(K_POSITIONS, "{}") ?: "{}")
            val map = mutableMapOf<String, NodePosition>()
            json.keys().forEach { id ->
                val pair = json.getJSONArray(id)
                map[id] = NodePosition(pair.getDouble(0).toFloat(), pair.getDouble(1).toFloat())
            }
            map.toMap()
        }.getOrDefault(emptyMap())

        return CanvasState(
            hidden = prefs.getStringSet(K_HIDDEN, emptySet())?.toSet() ?: emptySet(),
            positions = positions,
            ringSpread = prefs.getFloat(K_RING_SPREAD, defaults.ringSpread),
            centerScale = prefs.getFloat(K_CENTER_SCALE, defaults.centerScale),
            editing = false
        )
    }

    companion object {
        private const val FILE_NAME = "super_app_canvas"
        private const val K_HIDDEN = "hidden"
        private const val K_POSITIONS = "positions"
        private const val K_RING_SPREAD = "ring_spread"
        private const val K_CENTER_SCALE = "center_scale"

        @Volatile
        private var instance: CanvasStore? = null

        /** The one shared store. Safe to call from anywhere with any context. */
        fun get(context: Context): CanvasStore =
            instance ?: synchronized(this) {
                instance ?: CanvasStore(context.applicationContext).also { instance = it }
            }
    }
}
