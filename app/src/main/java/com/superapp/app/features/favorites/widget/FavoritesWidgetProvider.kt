package com.superapp.app.features.favorites.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.superapp.app.R
import com.superapp.app.features.favorites.data.FavoriteNode
import com.superapp.app.features.favorites.data.FavoritesRepository

class FavoritesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        private const val PREFS = "favorites_widget"
        private const val KEY_GROUP_PREFIX = "group_"

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val groupId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_GROUP_PREFIX + widgetId, null)
            val nodes = FavoritesRepository(context).load()
            val items = nodes.filter { it.parentId == groupId }.take(6)

            val views = RemoteViews(context.packageName, R.layout.widget_favorites)
            views.removeAllViews(R.id.widget_favorites_items)
            items.forEach { node ->
                val row = RemoteViews(context.packageName, R.layout.widget_favorite_item)
                row.setTextViewText(R.id.widget_favorite_title, node.name)
                if (node is FavoriteNode.App) {
                    val launch = context.packageManager.getLaunchIntentForPackage(node.packageName)
                    if (launch != null) {
                        row.setOnClickPendingIntent(
                            R.id.widget_favorite_title,
                            PendingIntent.getActivity(context, node.id.hashCode(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        )
                    }
                }
                views.addView(R.id.widget_favorites_items, row)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}
