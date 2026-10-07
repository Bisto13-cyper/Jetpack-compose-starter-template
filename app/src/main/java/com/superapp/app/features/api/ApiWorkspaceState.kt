package com.superapp.app.features.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/** Everything that belongs to ONE block: its saved configuration plus its own request/response/transform state. */
class ApiBlockState(initial: ApiBlock) {
    val id: String = initial.id

    var config by mutableStateOf(initial)

    var sending by mutableStateOf(false)
    var response by mutableStateOf<ApiResponse?>(null)
    var error by mutableStateOf<ApiResult.Failure?>(null)

    var transformRunning by mutableStateOf(false)
    var transformOutput by mutableStateOf<String?>(null)
    var transformError by mutableStateOf<String?>(null)
    var transformLogs by mutableStateOf<List<String>>(emptyList())

    var job: Job? = null

    fun clearTransformResult() {
        transformOutput = null
        transformError = null
        transformLogs = emptyList()
    }

    fun clearRuntime() {
        sending = false
        response = null
        error = null
        transformRunning = false
        clearTransformResult()
    }
}

class ApiWorkspaceState(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val storage = ApiStorage(appContext)
    private val handler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { flush() }

    val blocks = mutableStateListOf<ApiBlockState>()
    val variables = mutableStateListOf<KeyValue>()
    val history = mutableStateListOf<HistoryEntry>()

    init {
        val stored = storage.load()
        stored.blocks.forEach { blocks.add(ApiBlockState(it)) }
        variables.addAll(stored.variables)
        history.addAll(stored.history)
        if (blocks.isEmpty()) blocks.add(ApiBlockState(ApiBlock(name = "Request 1")))
    }

    // ------------------------------------------------------------ persistence

    fun scheduleSave() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 500)
    }

    fun flush() {
        handler.removeCallbacks(saveRunnable)
        storage.save(StoredWorkspace(blocks.map { it.config }, variables.toList(), history.toList()))
    }

    // ------------------------------------------------------------ blocks

    fun update(state: ApiBlockState, change: (ApiBlock) -> ApiBlock) {
        state.config = change(state.config)
        scheduleSave()
    }

    fun addBlock() {
        blocks.add(ApiBlockState(ApiBlock(name = "Request ${blocks.size + 1}")))
        scheduleSave()
    }

    fun duplicate(state: ApiBlockState) {
        val copy = state.config.copy(id = UUID.randomUUID().toString(), name = state.config.name + " copy")
        val index = blocks.indexOf(state)
        blocks.add(if (index >= 0) index + 1 else blocks.size, ApiBlockState(copy))
        scheduleSave()
    }

    fun delete(state: ApiBlockState) {
        state.job?.cancel()
        blocks.remove(state)
        scheduleSave()
    }

    fun reset(state: ApiBlockState) {
        state.job?.cancel()
        val c = state.config
        state.config = ApiBlock(id = c.id, name = c.name)
        state.clearRuntime()
        scheduleSave()
    }

    // ------------------------------------------------------------ variables

    fun setVariables(list: List<KeyValue>) {
        variables.clear()
        variables.addAll(list)
        scheduleSave()
    }

    private fun variableMap(): Map<String, String> =
        variables.filter { it.enabled && it.key.isNotBlank() }.associate { it.key.trim() to it.value }

    // ------------------------------------------------------------ history

    private fun addHistory(block: ApiBlock) {
        // Secrets are not copied into history; everything else is enough to reopen the request.
        val safe = block.copy(authToken = "", authPassword = "", apiKeyValue = "")
        history.add(0, HistoryEntry(time = System.currentTimeMillis(), block = safe))
        while (history.size > MAX_HISTORY) history.removeAt(history.size - 1)
        scheduleSave()
    }

    fun reopen(entry: HistoryEntry) {
        blocks.add(ApiBlockState(entry.block.copy(id = UUID.randomUUID().toString())))
        scheduleSave()
    }

    fun clearHistory() {
        history.clear()
        scheduleSave()
    }

    // ------------------------------------------------------------ send

    /** Sends the block's request; pressing it again while sending cancels the request. */
    fun send(state: ApiBlockState) {
        if (state.sending) {
            state.job?.cancel()
            return
        }
        val config = state.config
        state.sending = true
        state.error = null
        state.response = null
        state.clearTransformResult()
        addHistory(config)
        state.job = scope.launch {
            try {
                when (val result = ApiHttpClient.execute(config, variableMap())) {
                    is ApiResult.Success -> state.response = result.response
                    is ApiResult.Failure -> state.error = result
                }
            } catch (e: CancellationException) {
                state.error = ApiResult.Failure("Cancelled", "The request was cancelled.")
                throw e
            } finally {
                state.sending = false
            }
        }
    }

    // ------------------------------------------------------------ transform

    fun runTransform(state: ApiBlockState) {
        val resp = state.response
        if (resp == null) {
            state.clearTransformResult()
            state.transformError = "Send the request first: there is no response to transform."
            return
        }
        if (state.transformRunning) return
        state.clearTransformResult()
        state.transformRunning = true
        val code = state.config.transformCode
        scope.launch {
            try {
                val parsed = parseJsonOrNull(resp.body)
                val dataJson = if (parsed != null) jsonToText(parsed, false) else jsonQuote(resp.body)

                val headersObj = JSONObject()
                resp.headers.forEach { (k, v) ->
                    val key = k.lowercase()
                    headersObj.put(key, if (headersObj.has(key)) headersObj.getString(key) + ", " + v else v)
                }
                val responseJson = JSONObject()
                    .put("status", resp.code)
                    .put("statusText", resp.message)
                    .put("contentType", resp.contentType)
                    .put("timeMs", resp.timeMs)
                    .put("headers", headersObj)
                    .toString()
                val varsJson = JSONObject(variableMap() as Map<*, *>).toString()

                val result = ApiJsEngine.run(appContext, code, dataJson, varsJson, responseJson)
                state.transformOutput = result.output
                state.transformError = result.error
                state.transformLogs = result.logs
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.transformError = "Could not run the script: ${e.javaClass.simpleName}"
            } finally {
                state.transformRunning = false
            }
        }
    }

    fun resetTransform(state: ApiBlockState) {
        update(state) { it.copy(transformCode = DEFAULT_TRANSFORM) }
        state.clearTransformResult()
    }

    private companion object {
        const val MAX_HISTORY = 30
    }
}
