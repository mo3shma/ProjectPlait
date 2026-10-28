package app.plait.i18n

import app.plait.R
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [Txt] in JVM unit tests: reads src/main/res/values{,-ar}/strings*.xml directly, so code that builds text with Txt runs and
 * is checked without Android. Installed with no setup through META-INF/services (src/test/resources). The language follows
 * [AppLocale.locale]: tests are English unless one sets `AppLocale.locale = Locale.forLanguageTag("ar-u-nu-latn")`
 * (and puts Locale.ENGLISH back after).
 */
class TestStrings : TxtBackend {
    override fun text(id: Int, count: Int?, args: Array<out Any>): String {
        val l = AppLocale.locale
        val ar = l.language == "ar"
        val raw = if (count == null) {
            val name = stringNames[id] ?: error("No string resource with id $id")
            (if (ar) arabic.strings[name] else null) ?: english.strings[name] ?: error("String $name is missing")
        } else {
            val name = pluralNames[id] ?: error("No plurals resource with id $id")
            val items = (if (ar) arabic.plurals[name] else null) ?: english.plurals[name] ?: error("Plurals $name is missing")
            items[category(l, count)] ?: items.getValue("other")
        }
        val fmtArgs = if (count != null && args.isEmpty()) arrayOf<Any>(count) else args
        return if (count == null && args.isEmpty()) raw else String.format(l, raw, *fmtArgs)
    }

    internal class Values(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>, val untranslatable: Set<String>)

    companion object {
        private val res: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        internal val english by lazy { load("values") }
        internal val arabic by lazy { load("values-ar") }
        private val stringNames by lazy { R.string::class.java.fields.associate { it.getInt(null) to it.name } }
        private val pluralNames by lazy { R.plurals::class.java.fields.associate { it.getInt(null) to it.name } }

        internal fun load(dir: String): Values {
            val strings = HashMap<String, String>()
            val plurals = HashMap<String, Map<String, String>>()
            val untranslatable = HashSet<String>()
            val files = File(res, dir).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty()
            for (f in files) {
                val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).documentElement
                val kids = root.childNodes
                for (i in 0 until kids.length) {
                    val e = kids.item(i) as? Element ?: continue
                    val name = e.getAttribute("name")
                    if (e.getAttribute("translatable") == "false") untranslatable += name
                    when (e.tagName) {
                        "string" -> strings[name] = unescape(e.textContent)
                        "plurals" -> {
                            val items = e.getElementsByTagName("item")
                            plurals[name] = (0 until items.length).associate { j ->
                                val it = items.item(j) as Element
                                it.getAttribute("quantity") to unescape(it.textContent)
                            }
                        }
                    }
                }
            }
            return Values(strings, plurals, untranslatable)
        }

        /** What aapt does to a string: collapse whitespace outside "quotes", drop the quotes, resolve backslash escapes. */
        internal fun unescape(s: String): String {
            val out = StringBuilder()
            var quoted = false
            var space = false // unquoted whitespace seen; written as one space only if more text follows
            fun put(c: Char) { if (space && out.isNotEmpty()) out.append(' '); space = false; out.append(c) }
            var i = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\\' && i + 1 < s.length -> when (val n = s[++i]) {
                        'n' -> put('\n')
                        't' -> put('\t')
                        'u' -> { put(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> put(n)
                    }
                    c == '"' -> quoted = !quoted
                    !quoted && c.isWhitespace() -> space = true
                    else -> put(c)
                }
                i++
            }
            return out.toString()
        }

        /** CLDR plural categories for the two languages Plait speaks (integers only, which is all the app counts). */
        internal fun category(l: Locale, n: Int): String = when (l.language) {
            "ar" -> when {
                n == 0 -> "zero"
                n == 1 -> "one"
                n == 2 -> "two"
                n % 100 in 3..10 -> "few"
                n % 100 in 11..99 -> "many"
                else -> "other"
            }
            else -> if (n == 1) "one" else "other"
        }
    }
}
