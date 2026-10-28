package app.plait.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language: English or Arabic. It follows the phone unless one is picked in Settings, Language (or in Android's own
 * per-app language screen on Android 13+). Android 13+ keeps that choice itself (LocaleManager); older phones keep it in prefs and
 * every screen and [wrap]ped context is built in it. Arabic always uses Western digits and the Gregorian calendar.
 *
 * Use [locale] for every date, time and number shown to people, [isRtl] for the rare direction decision Compose cannot make, and
 * [wrap] (or [Txt]) for a Context whose resources speak the app's language: a widget, a notification, a worker.
 */
object AppLocale {
    private const val KEY = "app_language"

    /** The locale to format with. English stays [Locale.ENGLISH], so every English string is exactly what it was. */
    @Volatile var locale: Locale = Locale.ENGLISH
        internal set

    /** The locale screens and resources are built in: the phone's own English (en-GB, en-US...) or Arabic with Western digits. */
    @Volatile private var config: Locale = Locale.ENGLISH

    val isRtl: Boolean get() = locale.language == "ar"

    /** Called once by [app.plait.PlaitApp], before any activity, service, receiver or widget runs. */
    fun init(context: Context) {
        refresh(context)
        Txt.backend = ResBackend(context.applicationContext)
    }

    /** Works the language out again from [locales] (the config just handed to the app or an activity). */
    fun refresh(context: Context, locales: LocaleList = context.resources.configuration.locales) {
        val picked = if (Build.VERSION.SDK_INT >= 33) "" else prefs(context).getString(KEY, "").orEmpty()
        config = resolve(if (picked.isNotEmpty()) LocaleList.forLanguageTags(picked) else locales)
        locale = if (config.language == "ar") config else Locale.ENGLISH
        // Code that formats with the default locale (String.format, "%d".format) then gets the same language and Western digits.
        if (Locale.getDefault() != config) Locale.setDefault(config)
    }

    /** A delta for an activity or [wrap]: just the language and its direction. */
    fun overrideConfig(): Configuration = Configuration().apply { setLocale(config) }

    /** [context] with resources in the app's language: getString, plurals and layout direction all follow it. */
    fun wrap(context: Context): Context = context.createConfigurationContext(overrideConfig())

    /** "" follows the phone, otherwise "en" or "ar". */
    fun choice(context: Context): String {
        val tags = if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            else prefs(context).getString(KEY, "").orEmpty()
        return Locale.forLanguageTag(tags.substringBefore(',')).language.takeIf { it == "en" || it == "ar" }.orEmpty()
    }

    /** Switches language. Android 13+ restarts the screen itself; older phones rebuild [context]'s activity. */
    fun choose(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            prefs(context).edit().putString(KEY, tag).commit()
            refresh(context, Resources.getSystem().configuration.locales)
            (context as? Activity)?.recreate()
        }
    }

    /** The first language in [list] the app has (Android's own rule for picking resources); English when there is none. */
    internal fun resolve(list: LocaleList): Locale {
        val all = (0 until list.size()).map { list[it] }
        val l = all.firstOrNull { it.language == "ar" || it.language == "en" } ?: Locale.ENGLISH
        if (l.language == "en" && l.country.isNotEmpty()) return l
        val b = Locale.Builder().setLocale(l)
        // A language picked in Settings comes without a region: keep the phone's (first day of the week, and so on).
        if (l.country.isEmpty()) runCatching {
            val phone = Resources.getSystem().configuration.locales.let { s -> (0 until s.size()).map { s[it] } }
            b.setRegion((all + phone).firstOrNull { it.country.isNotEmpty() }?.country.orEmpty())
        }
        if (l.language == "ar") b.setUnicodeLocaleKeyword("nu", "latn").setUnicodeLocaleKeyword("ca", "gregory")
        return b.build()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("plait", Context.MODE_PRIVATE)

    /** [Txt] on the phone: the app's resources in the app's language, rebuilt when the language changes. */
    private class ResBackend(private val app: Context) : TxtBackend {
        @Volatile private var res: Pair<Locale, Resources>? = null

        override fun text(id: Int, count: Int?, args: Array<out Any>): String {
            val r = res?.takeIf { it.first == config }?.second ?: wrap(app).resources.also { res = config to it }
            return when {
                count != null -> r.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf<Any>(count) else args))
                args.isEmpty() -> r.getString(id)
                else -> r.getString(id, *args)
            }
        }
    }
}
