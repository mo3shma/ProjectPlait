package app.plait.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** The 2x2 entry in the picker. Data, alarm and layouts live in [NextMeetingWidget]; this only forwards. */
class NextMeetingWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread { try { NextMeetingWidget.updateAll(context) } finally { pending.finish() } }.start()
    }

    override fun onDisabled(context: Context) = NextMeetingWidget.cancelIfNone(context)
}
