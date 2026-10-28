package app.plait.i18n

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Where [Txt] reads strings: the app's resources on the phone, the res/values XML files in unit tests. */
fun interface TxtBackend {
    /** [count] is null for a plain string, the quantity for a plural. */
    fun text(id: Int, count: Int?, args: Array<out Any>): String
}

/**
 * Strings from resources for code that has no Context (data layer, generated text, notifications, widgets) in the app's
 * language. In Compose use stringResource / pluralStringResource instead. See docs/I18N.md.
 *
 *     Txt.s(R.string.common_cancel)
 *     Txt.s(R.string.gen_brief_next, title, time)      // "%1$s at %2$s"
 *     Txt.plural(R.plurals.common_meetings, n)         // "%d meetings"; n is the format argument when none is given
 */
object Txt {
    /** Set by [AppLocale.init]. Unit tests get theirs from META-INF/services (src/test/resources), with no setup. */
    @Volatile var backend: TxtBackend? = null

    private val active: TxtBackend
        get() = backend ?: java.util.ServiceLoader.load(TxtBackend::class.java).firstOrNull()?.also { backend = it }
            ?: error("Txt was used before PlaitApp started")

    fun s(@StringRes id: Int, vararg args: Any): String = active.text(id, null, args)

    fun plural(@PluralsRes id: Int, n: Int, vararg args: Any): String = active.text(id, n, args)
}
