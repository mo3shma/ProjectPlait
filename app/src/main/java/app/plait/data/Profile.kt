package app.plait.data

import android.content.Context
import android.os.Build
import app.plait.R
import app.plait.i18n.Txt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Who is using this copy of Plait and what to call this phone. Kept on the phone and carried by a backup. */
object Profile {
    var name by mutableStateOf(""); private set
    var device by mutableStateOf(""); private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences("plait", Context.MODE_PRIVATE)
        name = p.getString("profile_name", "").orEmpty()
        device = p.getString("device_label", null) ?: (Build.MODEL ?: Txt.s(R.string.settings_this_phone))
    }

    fun setName(context: Context, value: String) {
        name = value.trim().take(40)
        context.getSharedPreferences("plait", Context.MODE_PRIVATE).edit().putString("profile_name", name).apply()
    }

    fun setDevice(context: Context, value: String) {
        device = value.trim().take(40).ifBlank { Build.MODEL ?: Txt.s(R.string.settings_this_phone) }
        context.getSharedPreferences("plait", Context.MODE_PRIVATE).edit().putString("device_label", device).apply()
    }

    /** "Sam" from "Sam Aoki", for greetings. */
    fun firstName(): String = name.trim().substringBefore(' ')
}
