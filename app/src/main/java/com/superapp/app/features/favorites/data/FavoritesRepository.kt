package com.superapp.app.features.favorites.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FavoritesRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<FavoriteNode> = runCatching {
        val array = JSONArray(prefs.getString(KEY_NODES, "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                if (o.optString("type") == "group") {
                    add(FavoriteNode.Group(o.getString("id"), o.optString("parentId").takeIf { it.isNotEmpty() }, o.getString("name")))
                } else {
                    add(FavoriteNode.App(o.getString("id"), o.optString("parentId").takeIf { it.isNotEmpty() }, o.getString("name"), o.getString("packageName"), o.optString("activityName").takeIf { it.isNotEmpty() }))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun save(nodes: List<FavoriteNode>) {
        val array = JSONArray()
        nodes.forEach { node ->
            val o = JSONObject().apply {
                put("id", node.id)
                put("parentId", node.parentId ?: "")
                put("name", node.name)
                when (node) {
                    is FavoriteNode.Group -> put("type", "group")
                    is FavoriteNode.App -> {
                        put("type", "app")
                        put("packageName", node.packageName)
                        put("activityName", node.activityName ?: "")
                    }
                }
            }
            array.put(o)
        }
        prefs.edit().putString(KEY_NODES, array.toString()).apply()
    }

    fun createGroup(name: String, parentId: String? = null): FavoriteNode.Group =
        FavoriteNode.Group(UUID.randomUUID().toString(), parentId, name)

    fun createApp(name: String, packageName: String, parentId: String? = null, activityName: String? = null): FavoriteNode.App =
        FavoriteNode.App(UUID.randomUUID().toString(), parentId, name, packageName, activityName)

    companion object {
        private const val PREFS = "favorites_feature"
        private const val KEY_NODES = "nodes"
    }
}
