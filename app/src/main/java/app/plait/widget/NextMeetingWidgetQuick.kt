package app.plait.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** The 4x3 quick-add entry in the picker. Data, alarm and layouts live in [NextMeetingWidget]; this only forwards. */
class NextMeetingWidgetQuick : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (!NextMeetingWidget.providerUpdateDue(context)) return
        val pending = goAsync()
        Thread { try { NextMeetingWidget.updateAll(context) } finally { pending.finish() } }.start()
    }

    override fun onDisabled(context: Context) = NextMeetingWidget.cancelIfNone(context)
}
