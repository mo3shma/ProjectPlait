package app.plait.data.memo

import app.plait.R
import app.plait.i18n.Txt

/** Which alphabet to read a photo as. Auto tries both and keeps the better result. */
enum class OcrLang(private val res: Int) {
    Auto(R.string.note_ocr_auto), Latin(R.string.note_ocr_latin), Arabic(R.string.note_ocr_arabic);

    val label: String get() = Txt.s(res)
}

object OcrChoice {
    fun isArabicLetter(c: Char): Boolean =
        c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' || c in 'ࢠ'..'ࣿ' || c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿'

    /** Share of the letters in [text] that are Arabic, 0..1. */
    fun arabicShare(text: String): Float {
        val letters = text.count { it.isLetter() }
        if (letters == 0) return 0f
        return text.count { isArabicLetter(it) }.toFloat() / letters
    }

    /**
     * Picks between the Latin reader's text ([latin]) and the Arabic reader's text ([arabic], with its own
     * 0..100 confidence). The Latin reader cannot see Arabic at all, so any real amount of Arabic in the second
     * result means the page is Arabic (or mixed) and the second result is the one to keep.
     */
    fun pick(lang: OcrLang, latin: String?, arabic: String?, arabicConfidence: Int): String? = when (lang) {
        OcrLang.Latin -> latin
        OcrLang.Arabic -> arabic ?: latin
        OcrLang.Auto -> {
            val share = arabic?.let { arabicShare(it) } ?: 0f
            when {
                arabic != null && share >= 0.25f && arabicConfidence >= 35 -> arabic
                latin != null && latin.isNotBlank() -> latin
                else -> arabic ?: latin
            }
        }
    }
}
