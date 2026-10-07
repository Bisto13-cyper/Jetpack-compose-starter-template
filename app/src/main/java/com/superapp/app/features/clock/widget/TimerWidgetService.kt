package com.superapp.app.features.clock.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.superapp.app.R
import com.superapp.app.features.clock.ActiveTimer
import com.superapp.app.features.clock.ClockStore
import com.superapp.app.features.clock.ClockText
import com.superapp.app.features.clock.SavedTimer
import com.superapp.app.features.clock.TimerStatus

/** Supplies the saved-timer rows of [TimerWidgetProvider]'s list. */
class TimerWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var saved: List<SavedTimer> = emptyList()
        private var active: List<ActiveTimer> = emptyList()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            val store = ClockStore.get(context)
            saved = store.loadSaved().filter { it.enabled }
            active = store.loadActive()
        }

        override fun onDestroy() {}

        override fun getCount(): Int = saved.size

        override fun getViewAt(position: Int): RemoteViews {
            val timer = saved[position]
            val state = active.firstOrNull { it.savedId == timer.id }
            val views = RemoteViews(context.packageName, R.layout.clock_widget_timer_item)
            views.setTextViewText(R.id.clock_item_name, timer.name)
            views.setTextViewText(R.id.clock_item_duration, ClockText.durationWords(timer.durationMs))
            views.setTextViewText(
                R.id.clock_item_state,
                when (state?.status) {
                    TimerStatus.RUNNING -> "Running"
                    TimerStatus.PAUSED -> "Paused"
                    TimerStatus.FINISHED -> "Finished"
                    null -> "Tap to start"
                }
            )
            views.setOnClickFillInIntent(
                R.id.clock_item_root,
                Intent().putExtra(TimerWidgetProvider.EXTRA_SAVED_ID, timer.id)
            )
            return views
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = saved[position].id.hashCode().toLong()
        override fun hasStableIds(): Boolean = true
    }
}
