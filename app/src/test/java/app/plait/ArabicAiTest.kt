package app.plait

import app.plait.ai.AiHarness
import app.plait.ai.AiPrefs
import app.plait.ai.ArabicText
import app.plait.ai.Embedder
import app.plait.ai.ModelCatalog
import app.plait.ai.NoteAiPrompt
import app.plait.ai.NoteSections
import app.plait.data.CalEvent
import app.plait.data.Guest
import app.plait.data.Rsvp
import app.plait.data.Source
import app.plait.data.brief.BriefBuilder
import app.plait.data.context.AiPrompt
import app.plait.data.context.ContextAnalyzer
import app.plait.data.context.ContextItem
import app.plait.data.context.DateMentions
import app.plait.data.context.EmailParser
import app.plait.data.context.ExampleEmail
import app.plait.data.context.HighlightKind
import app.plait.data.context.Strength
import app.plait.data.context.Threads
import app.plait.i18n.AppLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val ARABIC: Locale = Locale.forLanguageTag("ar-u-nu-latn")
private val LONDON = ZoneId.of("Europe/London")
private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

/** Comparing Arabic: marks, letter variants, digits and clitics. */
class ArabicTextTest {
    @Test fun normalisesForComparisonOnly() {
        assertEquals("احمد", ArabicText.norm("أَحْمَدُ"))
        assertEquals("مدرسه", ArabicText.norm("مدرسة"))
        assertEquals("مستشفي", ArabicText.norm("مستشفى"))
        assertEquals("اذار", ArabicText.norm("آذار"))
        assertEquals("مرحبا", ArabicText.norm("مـرحـبـاً"))
        assertEquals("2026", ArabicText.norm("٢٠٢٦"))
        assertEquals("plan b", ArabicText.norm("Plan B"))
        assertEquals("3.5%", ArabicText.westernDigits("٣٫٥٪"))
        assertEquals("No digits", ArabicText.westernDigits("No digits"))
    }

    @Test fun cliticsComeOff() {
        assertTrue(ArabicText.bare(ArabicText.norm("وبالميزانية")).contains("ميزانيه"))
        assertTrue(ArabicText.bare(ArabicText.norm("للمدير")).containsAll(listOf("المدير", "مدير")))
        assertTrue(ArabicText.bare(ArabicText.norm("وسارة")).contains("ساره"))
        // A name that starts like a clitic still matches itself.
        assertEquals("وليد", ArabicText.bare("وليد").first())
        assertEquals(ArabicText.stem("الميزانية"), ArabicText.stem("بميزانية"))
        assertEquals(ArabicText.stem("المستخدمين"), ArabicText.stem("مستخدم"))
    }
}

/** The harness checks on Arabic answers and in the Arabic app. */
class ArabicHarnessTest {
    @After fun english() { AppLocale.locale = Locale.ENGLISH }

    private val src = "اجتماع أحمد وسارة حول إطلاق المنتج في الربع الرابع بميزانية ٥٠٠٠ دولار مع Karim"

    @Test fun arabicNumbersAreComparedAndShownAsWesternDigits() {
        assertEquals("الميزانية 5000 دولار", AiHarness.checked("الميزانية ٥٠٠٠ دولار", src, 14))
        assertNull(AiHarness.checked("الميزانية 7000 دولار", src, 14))
    }

    @Test fun latinNamesCountEvenWithAnArabicPrefix() {
        assertNotNull(AiHarness.checked("تتواصل سارة مع Karim بشأن الإطلاق", src, 14))
        assertNotNull(AiHarness.checked("ولـKarim رأي آخر في الإطلاق", src, 14))
        assertNull(AiHarness.checked("تتواصل سارة مع John بشأن الإطلاق", src, 14))
        assertNull(AiHarness.checked("وJohn موافق على الإطلاق", src, 14))
        // In an Arabic answer a Latin name at the very start is still a name.
        assertNull(AiHarness.checked("John يرسل الخطة", src, 14))
    }

    @Test fun anArabicNameAfterATitleMustBeInTheSource() {
        assertNotNull(AiHarness.checked("سترسل الأستاذة سارة التقرير", src, 14))
        assertNotNull(AiHarness.checked("للدكتــور أَحْمَد رأيٌ آخر", src, 14))
        assertNull(AiHarness.checked("سيرسل السيد خالد التقرير", src, 14))
        assertNull(AiHarness.checked("رأي د. منى مختلف", src, 14))
    }

    @Test fun nothingAndCodeAreDropped() {
        assertNull(AiHarness.checked("لا يوجد.", src, 14))
        assertNull(AiHarness.checked("راجع https://example.com", src, 14))
        assertEquals("واحد اثنان ثلاثة…", AiHarness.checked("واحد اثنان ثلاثة أربعة", src, 3))
    }

    @Test fun theArabicAppDropsAnAnswerThatIsNotArabic() {
        assertEquals("Plan the launch with Karim", AiHarness.checked("Plan the launch with Karim", src, 14))
        AppLocale.locale = ARABIC
        assertNull(AiHarness.checked("Plan the launch with Karim", src, 14))
        assertEquals("خطة الإطلاق مع Karim", AiHarness.checked("خطة الإطلاق مع Karim", src, 14))
    }

    @Test fun paragraphsEndAtArabicQuestionMarks() {
        assertEquals(
            "هل نبدأ الإطلاق؟ نعم نبدأ في الربع الرابع.",
            AiHarness.paragraph("هل نبدأ الإطلاق؟ نعم نبدأ في الربع الرابع. ثم نراجع الميزانية.", src, 40),
        )
        assertEquals("نراجع خطة الإطلاق.", AiHarness.paragraph("نراجع خطة الإطلاق", src, 40))
    }

    @Test fun arabicTitlesHaveTopicWords() {
        val title = "الاجتماع الأسبوعي: ميزانية التسويق"
        assertEquals(listOf(ArabicText.stem("ميزانية"), ArabicText.stem("التسويق")), AiHarness.topicWords(title))
        assertTrue(AiHarness.onTopic("مراجعة الميزانية مع الفريق", title))
        assertFalse(AiHarness.onTopic("خطة العطلة الصيفية", title))
    }

    @Test fun theLanguageRuleIsAddedOnlyInArabicAndOnlyOnce() {
        assertEquals("RULES", AiHarness.withLanguage("RULES"))
        AppLocale.locale = ARABIC
        val once = AiHarness.withLanguage("RULES")
        assertTrue(once, once.startsWith("RULES\n") && once.contains("العربية الفصحى") && once.contains("JSON"))
        assertEquals(once, AiHarness.withLanguage(once))
    }

    @Test fun jsonWithArabicCommasStillParses() {
        val o = AiHarness.parseJson("{\"points\": [\"النقطة الأولى\"، \"النقطة الثانية\"]}")
        assertEquals(2, o!!.getJSONArray("points").length())
    }

    @Test fun meaningSearchLeavesArabicToWordSearch() {
        assertFalse(Embedder.understands("ميزانية التسويق للربع الرابع"))
        assertTrue(Embedder.understands("budget review with Karim"))
    }
}

/** Arabic emails and invites: cues, dates, threads, sign-offs. */
class ArabicContextTest {
    @After fun english() { AppLocale.locale = Locale.ENGLISH }

    @Test fun classifiesArabicSentences() {
        assertEquals(HighlightKind.Decision, ContextAnalyzer.kindOf("قررنا اعتماد الخطة الثانية."))
        assertEquals(HighlightKind.Decision, ContextAnalyzer.kindOf("وتم الاتفاق على السعر النهائي."))
        assertEquals(HighlightKind.Risk, ContextAnalyzer.kindOf("هناك خطر حقيقي على موعد الإطلاق."))
        assertEquals(HighlightKind.Risk, ContextAnalyzer.kindOf("أخشى أن يحدث تأخير في التسليم."))
        assertEquals(HighlightKind.Deadline, ContextAnalyzer.kindOf("يرجى إرسال الملف قبل يوم الخميس."))
        assertEquals(HighlightKind.Ask, ContextAnalyzer.kindOf("هل يمكنك مشاركة الأرقام؟"))
        assertEquals(HighlightKind.Ask, ContextAnalyzer.kindOf("نرجو مراجعة العقد مع الفريق."))
        assertEquals(HighlightKind.Action, ContextAnalyzer.kindOf("سأرسل العرض غدًا صباحًا."))
        assertEquals(HighlightKind.Number, ContextAnalyzer.kindOf("تبلغ الميزانية ١٢٠ ألف ريال."))
        assertEquals(HighlightKind.Number, ContextAnalyzer.kindOf("انخفضت المبيعات بنسبة ٢٠٪ هذا الشهر."))
        assertEquals(HighlightKind.Attachment, ContextAnalyzer.kindOf("التفاصيل في الملف المرفق."))
        assertNull(ContextAnalyzer.kindOf("كان الطقس جميلًا أمس."))
    }

    @Test fun findsArabicDaysDatesAndTimes() {
        val tue = LocalDate.of(2026, 10, 20)
        assertEquals("Tuesday", DateMentions.find("نلتقي يوم الثلاثاء إن شاء الله", tue, null))
        assertEquals("20 Oct", DateMentions.find("الموعد النهائي 20 أكتوبر", tue, null))
        assertEquals("20 Oct", DateMentions.find("نلتقي في ٢٠ تشرين الأول", tue, null))
        assertEquals("20 Oct", DateMentions.find("الاجتماع في ٢٠/١٠", tue, null))
        assertNull(DateMentions.find("نلتقي يوم الأربعاء", tue, null))
        // "أحد" is "someone"; only الأحد is Sunday.
        assertNull(DateMentions.find("سيحضر أحد الزملاء", LocalDate.of(2026, 10, 18), null))
        assertEquals("13:30", DateMentions.find("الساعة 1:30 م", null, "13:30"))
        assertEquals("13:30", DateMentions.find("الساعة ١٣:٣٠ تمامًا", null, "13:30"))
        assertEquals("10:30", DateMentions.find("10:30 صباحًا يناسبني", null, "10:30"))
        assertNull(DateMentions.find("الساعة 2:30 م", null, "13:30"))
        AppLocale.locale = ARABIC
        assertEquals("الثلاثاء", DateMentions.find("see you on Tuesday", tue, null))
        assertEquals("20 أكتوبر", DateMentions.find("الموعد النهائي 20 أكتوبر", tue, null))
    }

    @Test fun splitsAGmailArabicReply() {
        val msgs = Threads.split(GMAIL_AR, "أحمد العلي <ahmed@northwind.com>")
        assertEquals(msgs.toString(), 2, msgs.size)
        assertEquals("أحمد العلي", msgs[0].from)
        assertTrue(msgs[0].text, msgs[0].text.startsWith("بعد مكالمة اليوم"))
        assertFalse("greeting and sign-off are dropped: ${msgs[0].text}", msgs[0].text.contains("مرحبًا") || msgs[0].text.contains("التحيات"))
        assertEquals("سارة خليل", msgs[1].from)
        assertTrue(msgs[1].dateText.orEmpty(), msgs[1].dateText.orEmpty().contains("9:40"))
        assertEquals("نرجو إرسال أرقام ميزانية التسويق المحدثة قبل الخميس.", msgs[1].text)
    }

    @Test fun splitsAnOutlookArabicHeaderBlockAndAForward() {
        val outlook = """
نعم، سأرسل العرض المحدث غدًا.

من: سارة خليل <sara@acme.com>
تاريخ الإرسال: الاثنين، 19 أكتوبر 2026 09:40 ص
إلى: أحمد العلي <ahmed@northwind.com>
الموضوع: عرض الأسعار

هل العرض جاهز للمراجعة؟
""".trim()
        val msgs = Threads.split(outlook, "أحمد العلي <ahmed@northwind.com>")
        assertEquals(msgs.toString(), 2, msgs.size)
        assertEquals("سارة خليل", msgs[1].from)
        assertEquals("الاثنين، 19 أكتوبر 2026 09:40 ص", msgs[1].dateText)
        assertEquals("هل العرض جاهز للمراجعة؟", msgs[1].text)

        val fwd = Threads.split("انظروا أدناه.\n\n---------- الرسالة المُعاد توجيهها ---------\nالنص القديم هنا.", null)
        assertEquals(listOf("انظروا أدناه.", "النص القديم هنا."), fwd.map { it.text })
    }

    @Test fun anArabicNormalSentenceIsNotAQuoteHeader() {
        val msgs = Threads.split("في الاجتماع القادم كتب أحمد ملاحظة:\nنراجع الأرقام معًا.", null)
        assertEquals(1, msgs.size)
    }

    @Test fun pastedArabicHeadersAreRead() {
        val mail = EmailParser.fromText("الموضوع: عرض الأسعار\nمن: أحمد <ahmed@x.com>\nإلى: سارة <sara@x.com>\n\nنص الرسالة")
        assertEquals("عرض الأسعار", mail.subject)
        assertEquals("أحمد <ahmed@x.com>", mail.from)
        assertEquals("سارة <sara@x.com>", mail.to)
    }

    private fun arabicCall(): CalEvent {
        fun g(n: String, e: String, org: Boolean = false, self: Boolean = false) = Guest(n, e, Rsvp.Accepted, organizer = org, self = self)
        return CalEvent(
            "ar", "13:30", "30m", "مراجعة ميزانية التسويق", Source.Google, "Google Meet",
            startMs = at("2026-10-20T12:30:00Z"), endMs = at("2026-10-20T13:00:00Z"),
            guests = listOf(g("أحمد العلي", "ahmed@northwind.com", org = true), g("سارة خليل", "sara@acme.com"), g("You", "you@acme.com", self = true)),
        )
    }

    private fun item(body: String, subject: String, from: String, id: String = "c1") =
        ContextItem(id, "ar", subject, from, "سارة خليل <sara@acme.com>", null, body, 1L, "pasted")

    @Test fun analysesAnArabicThread() {
        val ev = arabicCall()
        val s = ContextAnalyzer.analyze(ev, BriefBuilder.build(ev), listOf(item(GMAIL_AR, "رد: ميزانية التسويق", "أحمد العلي <ahmed@northwind.com>")), LONDON)
        assertEquals(Strength.Strong, s.strength)
        val kinds = s.highlights.groupBy({ it.kind }, { it.text })
        assertTrue(s.highlights.toString(), kinds[HighlightKind.Decision].orEmpty().any { it.contains("اتفقنا") })
        assertTrue(s.highlights.toString(), kinds[HighlightKind.Risk].orEmpty().any { it.contains("تأخير") })
        assertTrue(s.highlights.toString(), kinds[HighlightKind.Deadline].orEmpty().any { it.contains("قبل الخميس") })
        assertTrue(s.relation.toString(), s.relation.any { it.startsWith("Mentions ميزانية") })
        assertTrue(s.relation.toString(), "Mentions Tuesday" in s.relation)
        assertTrue(s.relation.toString(), "أحمد العلي is in the thread" in s.relation)
        assertTrue(s.people.toString(), s.people.containsAll(listOf("أحمد العلي", "سارة خليل")))
    }

    @Test fun theArabicAppLabelsTheAnalysisInArabic() {
        AppLocale.locale = ARABIC
        val ev = arabicCall()
        val s = ContextAnalyzer.analyze(ev, BriefBuilder.build(ev), listOf(item(GMAIL_AR, "رد: ميزانية التسويق", "أحمد العلي <ahmed@northwind.com>")), LONDON)
        assertTrue(s.relation.toString(), "إشارة إلى ⁨الثلاثاء⁩" in s.relation)
        assertTrue(s.relation.toString(), "⁨أحمد العلي⁩ ضمن المراسلة" in s.relation)
        assertEquals("قرار", HighlightKind.Decision.label)
        assertEquals("صلة قوية", Strength.Strong.label)
    }

    @Test fun theExampleEmailIsArabicAndReadsAsOne() {
        AppLocale.locale = ARABIC
        val ev = CalEvent("ex", "10:00", "1h", "مراجعة الميزانية", Source.Google, "", startMs = at("2026-10-20T09:00:00Z"), endMs = at("2026-10-20T10:00:00Z"))
        val m = ExampleEmail.forEvent(ev, ZoneId.of("UTC"))
        assertEquals("قبل مراجعة الميزانية", m.subject)
        assertEquals("خالد <colleague@example.com>", m.from)
        assertTrue(m.body, m.body.contains("يوم الثلاثاء") && m.body.contains("20%"))
        val s = ContextAnalyzer.analyze(ev, BriefBuilder.build(ev), listOf(ContextItem("x", "ex", m.subject, m.from, m.to, null, m.body, 1L, "pasted")), ZoneId.of("UTC"))
        val kinds = s.highlights.map { it.kind }.toSet()
        assertTrue(s.highlights.toString(), kinds.containsAll(listOf(HighlightKind.Decision, HighlightKind.Risk)))
    }
}

/** Prompts, saved sections and labels in Arabic; English is checked by the existing tests. */
class ArabicPromptTest {
    @After fun english() { AppLocale.locale = Locale.ENGLISH }

    @Test fun noteSectionsReadInEitherLanguage() {
        val s = NoteAiPrompt.parse("الملخص: ستُرسل الأرقام يوم الجمعة.\nالقرارات:\n- لا يوجد\nالمتابعات:\n- يرسل سام الأرقام\n- تحجز بريا القاعة")
        assertEquals("ستُرسل الأرقام يوم الجمعة.", s.summary)
        assertTrue(s.decisions.isEmpty())
        assertEquals(listOf("يرسل سام الأرقام", "تحجز بريا القاعة"), s.followUps)
    }

    @Test fun noteSectionsAreWrittenInTheAppLanguage() {
        val sections = NoteSections("ملخص قصير", listOf("اعتماد الخطة"), listOf("يرسل سام الأرقام"))
        assertEquals("Summary: ملخص قصير\nDecisions:\n- اعتماد الخطة\nFollow-ups:\n- يرسل سام الأرقام", NoteAiPrompt.render(sections))
        AppLocale.locale = ARABIC
        val ar = NoteAiPrompt.render(sections)
        assertEquals("الملخص: ملخص قصير\nالقرارات:\n- اعتماد الخطة\nالمتابعات:\n- يرسل سام الأرقام", ar)
        assertEquals(sections, NoteAiPrompt.parse(ar))
        assertTrue(NoteAiPrompt.build(app.plait.data.SavedNote("n", "e", "الميزانية", 1L, 1000, "n.wav", emptyList(), emptyList(), null)).trimEnd().endsWith("أو اكتب - لا يوجد"))
    }

    @Test fun emailSectionsReadInArabic() {
        val s = AiPrompt.parse("العلاقة: تمهّد لنقاش الميزانية.\nالنقاط الرئيسية:\n- خفض بنسبة 20%\nما يجب حسمه في الاجتماع:\n- من يتولى المتابعة")
        assertEquals("تمهّد لنقاش الميزانية.", s.relation)
        assertEquals(listOf("خفض بنسبة 20%"), s.keyPoints)
        assertEquals(listOf("من يتولى المتابعة"), s.toSettle)
    }

    @Test fun theEmailPromptIsArabicInTheArabicApp() {
        AppLocale.locale = ARABIC
        val ev = CalEvent("e1", "10:00", "1h", "مراجعة الميزانية", Source.Google, "", startMs = at("2026-10-20T09:00:00Z"), endMs = at("2026-10-20T10:00:00Z"))
        val items = listOf(ContextItem("i1", "e1", "الأرقام", "Jo <jo@x.com>", "", null, "قررنا الخطة.", 1L, "pasted"))
        val p = AiPrompt.build(ev, BriefBuilder.build(ev), items, ZoneId.of("UTC"))
        assertTrue(p, p.contains("[الرسالة 1] من: Jo | الموضوع: الأرقام"))
        assertTrue(p, p.contains("الثلاثاء 20 أكتوبر"))
        assertTrue(p, p.trimEnd().endsWith("- حتى 3 نقاط قصيرة"))
    }

    @Test fun modelAndLanguageLabelsFollowTheApp() {
        assertEquals("Arabic + English", AiPrefs.languageName("ar+en"))
        assertEquals("Any language", AiPrefs.languageName("auto"))
        assertEquals("Plait's own rules only", AiPrefs.TextChoice.Rules.label)
        assertEquals("Meaning search", ModelCatalog.byId("search-use")!!.title)
        AppLocale.locale = ARABIC
        assertEquals("العربية + الإنجليزية", AiPrefs.languageName("ar+en"))
        assertEquals("أي لغة", AiPrefs.languageName("auto"))
        assertEquals("اكتشاف تلقائي", AiPrefs.speechLanguages.first().second)
        assertEquals("قواعد Plait الخاصة فقط", AiPrefs.TextChoice.Rules.label)
        assertEquals("تلقائي", AiPrefs.TextChoice.Auto.label)
        assertEquals("البحث بالمعنى", ModelCatalog.byId("search-use")!!.title)
        assertEquals("Qwen3 · 0.6B", ModelCatalog.byId("text-qwen3-0.6b")!!.title)
        assertEquals("⁨1.6 GB⁩", ModelCatalog.formatSize(1_597_913_616))
        assertEquals("⁨547 MB⁩", ModelCatalog.formatSize(546_660_344))
    }
}

private val GMAIL_AR = """
مرحبًا سارة،

بعد مكالمة اليوم، اتفقنا على تجديد عقد الميزانية بقيمة 48 ألف ريال سنويًا. هل يمكن تأكيد بنود الدعم قبل اجتماع الثلاثاء؟
أخشى أن يكون هناك تأخير في التسليم بسبب نقص الموظفين.

مع خالص التحيات،
أحمد

في الاثنين، 19 أكتوبر 2026 في 9:40 ص تمت كتابة ما يلي بواسطة ‪سارة خليل‬‏ <‪sara@acme.com‬‏>:
> نرجو إرسال أرقام ميزانية التسويق المحدثة قبل الخميس.
> شكرًا
""".trim()
