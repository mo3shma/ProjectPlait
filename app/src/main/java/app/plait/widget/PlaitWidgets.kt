package app.plait.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** The tasks and month entries in the picker. Data, alarm and layouts live in [NextMeetingWidget]; these only forward. A resize redraws, so the tasks list can show more rows. */
abstract class WidgetForwarder : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (!NextMeetingWidget.providerUpdateDue(context)) return
        refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) = refresh(context)

    override fun onDisabled(context: Context) = NextMeetingWidget.cancelIfNone(context)

    private fun refresh(context: Context) {
        val pending = goAsync()
        Thread { try { NextMeetingWidget.updateAll(context) } finally { pending.finish() } }.start()
    }
}

class TasksWidget : WidgetForwarder()

class MonthWidget : WidgetForwarder()
