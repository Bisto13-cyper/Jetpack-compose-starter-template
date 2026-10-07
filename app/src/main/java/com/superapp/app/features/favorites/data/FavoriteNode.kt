package com.superapp.app.features.favorites.data

/** A favorite entry. parentId enables unlimited nested groups. */
sealed class FavoriteNode(open val id: String, open val parentId: String?, open val name: String) {
    data class Group(
        override val id: String,
        override val parentId: String?,
        override val name: String
    ) : FavoriteNode(id, parentId, name)

    data class App(
        override val id: String,
        override val parentId: String?,
        override val name: String,
        val packageName: String,
        val activityName: String? = null
    ) : FavoriteNode(id, parentId, name)
}
