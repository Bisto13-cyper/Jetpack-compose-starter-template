package com.superapp.app.features.api

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

const val DEFAULT_TRANSFORM =
    "// Available: data (parsed JSON, or text), vars, response, console.log()\n" +
        "// Use return to output a result.\n" +
        "return data;"

enum class ApiMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS;

    val allowsBody: Boolean get() = this == POST || this == PUT || this == PATCH || this == DELETE
}

enum class AuthType(val label: String) {
    NONE("None"), BEARER("Bearer"), BASIC("Basic"), API_KEY("API key")
}

enum class BodyType(val label: String, val contentType: String?) {
    NONE("None", null), JSON("JSON", "application/json"), TEXT("Text", "text/plain")
}

data class KeyValue(
    val id: String = UUID.randomUUID().toString(),
    val key: String = "",
    val value: String = "",
    val enabled: Boolean = true
)

data class ApiBlock(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New request",
    val method: ApiMethod = ApiMethod.GET,
    val url: String = "",
    val params: List<KeyValue> = emptyList(),
    val headers: List<KeyValue> = emptyList(),
    val bodyType: BodyType = BodyType.NONE,
    val body: String = "",
    val authType: AuthType = AuthType.NONE,
    val authToken: String = "",
    val authUser: String = "",
    val authPassword: String = "",
    val apiKeyName: String = "X-API-Key",
    val apiKeyValue: String = "",
    val apiKeyInHeader: Boolean = true,
    val transformCode: String = DEFAULT_TRANSFORM
)

data class ApiResponse(
    val code: Int,
    val message: String,
    val headers: List<Pair<String, String>>,
    val body: String,
    val contentType: String,
    val timeMs: Long,
    val sizeBytes: Long,
    val truncated: Boolean
)

sealed class ApiResult {
    data class Success(val response: ApiResponse) : ApiResult()
    data class Failure(val kind: String, val message: String) : ApiResult()
}

data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val time: Long,
    val block: ApiBlock
)

data class StoredWorkspace(
    val blocks: List<ApiBlock> = emptyList(),
    val variables: List<KeyValue> = emptyList(),
    val history: List<HistoryEntry> = emptyList()
)

// ---------------------------------------------------------------- variables

private val VARIABLE_PATTERN = Regex("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*\\}\\}")

/** Replaces {{name}} with the variable value. Unknown names are left untouched. */
fun String.applyVariables(vars: Map<String, String>): String =
    if (isEmpty() || vars.isEmpty()) this
    else VARIABLE_PATTERN.replace(this) { m -> vars[m.groupValues[1]] ?: m.value }

fun looksSensitive(name: String): Boolean {
    val n = name.lowercase()
    return listOf("token", "key", "secret", "password", "passwd", "auth", "cookie", "bearer").any { n.contains(it) }
}

// ---------------------------------------------------------------- JSON helpers

/** Returns a JSONObject/JSONArray if the text is a JSON object or array, otherwise null. */
fun parseJsonOrNull(text: String): Any? {
    val t = text.trim()
    if (t.isEmpty() || (t[0] != '{' && t[0] != '[')) return null
    return try {
        JSONTokener(t).nextValue().takeIf { it is JSONObject || it is JSONArray }
    } catch (e: Exception) {
        null
    }
}

/** JSON string literal (also a valid JavaScript string literal). */
fun jsonQuote(s: String): String {
    val sb = StringBuilder("\"")
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000c' -> sb.append("\\f")
            else -> if (c < ' ' || c == '\u2028' || c == '\u2029') {
                sb.append(String.format("\\u%04x", c.code))
            } else {
                sb.append(c)
            }
        }
    }
    return sb.append('"').toString()
}

fun jsonToText(value: Any?, pretty: Boolean, level: Int = 0): String = when {
    value == null || value === JSONObject.NULL -> "null"
    value is JSONObject -> {
        val keys = value.keys().asSequence().toList()
        if (keys.isEmpty()) {
            "{}"
        } else {
            val pad = "  ".repeat(level + 1)
            val items = keys.map { k ->
                (if (pretty) pad else "") + jsonQuote(k) + (if (pretty) ": " else ":") +
                    jsonToText(value.opt(k), pretty, level + 1)
            }
            if (pretty) "{\n" + items.joinToString(",\n") + "\n" + "  ".repeat(level) + "}"
            else "{" + items.joinToString(",") + "}"
        }
    }
    value is JSONArray -> {
        if (value.length() == 0) {
            "[]"
        } else {
            val pad = "  ".repeat(level + 1)
            val items = (0 until value.length()).map { i ->
                (if (pretty) pad else "") + jsonToText(value.opt(i), pretty, level + 1)
            }
            if (pretty) "[\n" + items.joinToString(",\n") + "\n" + "  ".repeat(level) + "]"
            else "[" + items.joinToString(",") + "]"
        }
    }
    value is String -> jsonQuote(value)
    value is Double -> if (value.isNaN() || value.isInfinite()) "null" else value.toString()
    else -> value.toString()
}

// ---------------------------------------------------------------- serialization

private fun List<KeyValue>.toJson(): JSONArray {
    val arr = JSONArray()
    forEach {
        arr.put(
            JSONObject().put("id", it.id).put("key", it.key).put("value", it.value).put("enabled", it.enabled)
        )
    }
    return arr
}

private fun JSONArray?.toKeyValues(): List<KeyValue> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        optJSONObject(i)?.let {
            KeyValue(
                id = it.optString("id", UUID.randomUUID().toString()),
                key = it.optString("key"),
                value = it.optString("value"),
                enabled = it.optBoolean("enabled", true)
            )
        }
    }
}

private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

fun ApiBlock.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("method", method.name)
    .put("url", url)
    .put("params", params.toJson())
    .put("headers", headers.toJson())
    .put("bodyType", bodyType.name)
    .put("body", body)
    .put("authType", authType.name)
    .put("authToken", authToken)
    .put("authUser", authUser)
    .put("authPassword", authPassword)
    .put("apiKeyName", apiKeyName)
    .put("apiKeyValue", apiKeyValue)
    .put("apiKeyInHeader", apiKeyInHeader)
    .put("transformCode", transformCode)

fun apiBlockFromJson(o: JSONObject): ApiBlock {
    val d = ApiBlock()
    return ApiBlock(
        id = o.optString("id", d.id),
        name = o.optString("name", d.name),
        method = enumOr(o.optString("method"), ApiMethod.GET),
        url = o.optString("url"),
        params = o.optJSONArray("params").toKeyValues(),
        headers = o.optJSONArray("headers").toKeyValues(),
        bodyType = enumOr(o.optString("bodyType"), BodyType.NONE),
        body = o.optString("body"),
        authType = enumOr(o.optString("authType"), AuthType.NONE),
        authToken = o.optString("authToken"),
        authUser = o.optString("authUser"),
        authPassword = o.optString("authPassword"),
        apiKeyName = o.optString("apiKeyName", d.apiKeyName),
        apiKeyValue = o.optString("apiKeyValue"),
        apiKeyInHeader = o.optBoolean("apiKeyInHeader", true),
        transformCode = o.optString("transformCode", DEFAULT_TRANSFORM)
    )
}

fun StoredWorkspace.toJson(): JSONObject {
    val blocksArr = JSONArray().also { a -> blocks.forEach { a.put(it.toJson()) } }
    val historyArr = JSONArray().also { a ->
        history.forEach {
            a.put(JSONObject().put("id", it.id).put("time", it.time).put("block", it.block.toJson()))
        }
    }
    return JSONObject()
        .put("blocks", blocksArr)
        .put("variables", variables.toJson())
        .put("history", historyArr)
}

fun storedWorkspaceFromJson(o: JSONObject): StoredWorkspace {
    val blocks = o.optJSONArray("blocks")?.let { a ->
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::apiBlockFromJson) }
    } ?: emptyList()
    val history = o.optJSONArray("history")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { h ->
                h.optJSONObject("block")?.let { b ->
                    HistoryEntry(h.optString("id", UUID.randomUUID().toString()), h.optLong("time"), apiBlockFromJson(b))
                }
            }
        }
    } ?: emptyList()
    return StoredWorkspace(blocks, o.optJSONArray("variables").toKeyValues(), history)
}
