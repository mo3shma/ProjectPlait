package app.plait

import app.plait.data.TimeFmt
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.i18n.TestStrings
import app.plait.i18n.Txt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

/** The string foundation: Txt reads the real resource files in unit tests, Arabic plurals pick the right form, nothing is left untranslated. */
class I18nTest {
    @After fun english() { AppLocale.locale = Locale.ENGLISH }

    @Test fun englishStringsWithArguments() {
        assertEquals("Cancel", Txt.s(R.string.common_cancel))
        assertEquals("Open Language", Txt.s(R.string.common_open_item, "Language"))
        assertEquals("4 of 6", Txt.s(R.string.common_n_of_m, 4, 6))
        assertEquals("1 meeting", Txt.plural(R.plurals.common_meetings, 1))
        assertEquals("0 meetings", Txt.plural(R.plurals.common_meetings, 0))
        assertEquals("12 meetings", Txt.plural(R.plurals.common_meetings, 12))
        assertEquals("Plait follows your phone's language unless you pick one here.", Txt.s(R.string.settings_language_note))
        assertEquals("3:30 PM", TimeFmt.clock(LocalTime.of(15, 30)))
        assertEquals("Ann", Bidi.isolate("Ann"))
    }

    @Test fun arabicPluralsUseEveryCategory() {
        AppLocale.locale = Locale.forLanguageTag("ar-u-nu-latn")
        val forms = listOf(0, 1, 2, 3, 10, 11, 99, 100, 101, 102, 103, 111).associateWith { Txt.plural(R.plurals.common_meetings, it) }
        assertEquals(
            mapOf(
                0 to "لا اجتماعات", 1 to "اجتماع واحد", 2 to "اجتماعان", 3 to "3 اجتماعات", 10 to "10 اجتماعات", 11 to "11 اجتماعًا",
                99 to "99 اجتماعًا", 100 to "100 اجتماع", 101 to "101 اجتماع", 102 to "102 اجتماع", 103 to "103 اجتماعات", 111 to "111 اجتماعًا",
            ),
            forms,
        )
        // Western digits and an Arabic morning/evening mark, never Arabic-Indic digits.
        assertEquals("4 من 6", Txt.s(R.string.common_n_of_m, 4, 6))
        assertEquals("3:30\u00A0م", TimeFmt.clock(LocalTime.of(15, 30)))
        assertEquals("إلغاء", Txt.s(R.string.common_cancel))
        assertEquals("⁨Ann⁩", Bidi.isolate("Ann"))
    }

    @Test fun everyEnglishStringHasAnArabicOneWithTheSameArguments() {
        val en = TestStrings.english
        val ar = TestStrings.arabic
        val args = Regex("""%(\d+\$)?[sd]""")
        fun argsOf(s: String) = args.findAll(s).map { it.value }.toSortedSet()
        val missing = (en.strings.keys - en.untranslatable - ar.strings.keys) + (en.plurals.keys - ar.plurals.keys)
        assertTrue("No Arabic for: $missing", missing.isEmpty())
        val extra = (ar.strings.keys - en.strings.keys) + (ar.plurals.keys - en.plurals.keys)
        assertTrue("Arabic only, no English: $extra", extra.isEmpty())
        val mismatched = en.strings.filter { (k, v) -> k in ar.strings && argsOf(v) != argsOf(ar.strings.getValue(k)) }.keys +
            en.plurals.filter { (k, v) -> k in ar.plurals && argsOf(v.getValue("other")) != argsOf(ar.plurals.getValue(k).getValue("other")) }.keys
        assertTrue("Format arguments differ: $mismatched", mismatched.isEmpty())
    }
}
