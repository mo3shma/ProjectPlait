package app.plait.diag

import android.Manifest
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.plait.ai.Ai
import app.plait.ai.AiPrefs
import app.plait.ai.ModelCatalog
import app.plait.ai.ModelCenter
import app.plait.ai.OnDeviceAi
import app.plait.data.Calendars
import app.plait.data.device.DeviceCalendar
import app.plait.widget.NextMeetingWidget
import java.io.File

data class Check(val name: String, val ok: Boolean, val detail: String)

/** A checklist for a real phone: what works, what is missing, and why. Run from the hub. */
object SelfCheck {
    suspend fun run(context: Context): List<Check> {
        AiPrefs.load(context)
        val out = ArrayList<Check>()
        fun add(name: String, ok: Boolean, detail: String) { out.add(Check(name, ok, detail)); if (!ok) Diag.log("CHECK", "$name: $detail") }
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

        add("Calendar: read", DeviceCalendar.hasPermission(context), if (DeviceCalendar.hasPermission(context)) "${Calendars.deviceCals.size} calendars seen" else "Allow it in the hub")
        add("Calendar: write", granted(Manifest.permission.WRITE_CALENDAR), "Needed to save meetings made in Plait")
        add("Microphone", granted(Manifest.permission.RECORD_AUDIO), "Needed for voice notes")
        if (Build.VERSION.SDK_INT >= 33) add("Notifications", granted(Manifest.permission.POST_NOTIFICATIONS), "Needed for alerts before meetings")
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        add("Exact alarms", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms(), "Alerts can be a few minutes late without it")
        add("Speech model (live)", ModelCatalog.speechModels.any { ModelCenter.isInstalled(context, it.id) }, "A Whisper model writes the live words; get one in the AI hub")
        val nano = OnDeviceAi.state()
        add("Gemini Nano", nano == OnDeviceAi.State.Ready, nano.name)
        val status = Ai.status(context)
        add("AI in use", status.ready, status.label + (status.note?.let { " · $it" } ?: ""))
        val installed = ModelCatalog.all.filter { ModelCenter.isInstalled(context, it.id) }
        add("Downloaded models", true, if (installed.isEmpty()) "none" else installed.joinToString { it.title })
        val widgets = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, NextMeetingWidget::class.java)).size
        add("Widget", true, if (widgets == 0) "not added yet" else "$widgets on the home screen")
        add("Free storage", context.filesDir.usableSpace > 500_000_000L, "${context.filesDir.usableSpace / 1_000_000} MB free")
        add("Memory", true, "${(Runtime.getRuntime().maxMemory() / 1_000_000)} MB for the app")
        return out
    }
}
