# Changelog

What changed in each version of Plait, newest first.

## 0.25.0: two cards, a work-week recap, next-week briefs

- **Two cards on Morning and Midday.** Under the meetings brief there is now a second card in a contrasting colour (coral in the morning, mint at midday) called "Needs your attention". It lists the tasks you have not cleared: suggestions waiting for you to add or dismiss them, and unchecked lines of your checklists on meetings that have already started, stalest first. Tick, add or dismiss them right there, or tap one to open its meeting.
- **Set your work week.** Settings has a new "Work week" section: pick the day your week starts. A work week is five days from that day, so starting Sunday means Sunday to Thursday.
- **A recap on the last day of your work week.** On that day's Evening card, Plait looks back over the week in an assistant voice: meetings, time in meetings, your busiest day, notes taken, open tasks, with a bar for each day. With an AI on the phone it is written by the AI and checked against your numbers; without one, plain wording does the same job.
- **A look at next week on your days off.** On a day off with no meetings, the card offers "Brief me on next week". Tap it for a short assistant-style summary and a line for each day. Nothing is written until you tap.
- **About this meeting stays put.** The AI's paragraph is kept for each meeting until you tap "Write this again", so it is not rewritten when an invite changes or every time Home refreshes. When the AI had nothing useful to say, Plait remembers that too and does not ask again.
- Written week briefs are kept the same way and have the same "Write this again" button. Your Home refresh button still rewrites everything.
- Not yet checked on a phone: how the new cards look in Material style and in dark mode.

## 0.24.1: safer notes, clearer first run

- **Your notes can't be wiped by one bad record.** If a single note in the saved file is damaged, the others still load, and the unreadable file is kept as a copy instead of being overwritten by the next save.
- **Private notes stay out of cloud backup.** Android's automatic backup is switched off for Plait, so recordings and transcripts never leave the phone that way. Plait's own backup file is unchanged.
- **Recording stops cleanly if the microphone or storage fails.** It no longer spins forever; a message tells you, and what was recorded is kept.
- **No more leaked speech model.** Stopping a very short recording while the model was still loading no longer leaves about 100 MB in memory.
- **A clearer first run.** With no calendars on the phone, Home says so and offers "Add an account or link" instead of "Nothing on today". The recorder has a "Get Whisper Tiny" button when no speech model is installed.
- **Back button and status bar.** Scrolling cards fade out behind the back button and the clock instead of running under them.
- Calendar weekday labels are a little larger, and leftover "built-in recogniser" wording is gone.

## 0.24.0: live words from Whisper

- **Live words are written by Whisper.** While you record, each phrase is written a moment after you pause, in the language you picked (mixed pairs too), instead of by the small English-only recogniser. Arabic, English and mixed speech now show words live. The saved note is still one recording, and when you stop, Whisper reads the whole thing again for the final transcript.
- **The small English recogniser is gone**. Live words and transcripts now need a Whisper model from Settings → AI (Tiny is about 100 MB). Without one, the recording is saved as audio only.
- Not yet checked on a phone: how fast and how heavy on the battery this is on mid-range phones.

## 0.23.1: rebuild

- No changes to how the app works. This is a clean rebuild of 0.23.0, signed with the same key, so it installs over your current app.

## 0.23.0: mixed languages

- **Two languages in one note.** The language button now offers mixed pairs: Arabic + English, English + Arabic, French + English, French + Arabic, Spanish, German, Turkish, Hindi and Urdu with English. A mixed note is cut into short pieces at pauses, about a sentence each, and every piece is written in its own language: Whisper hears which one it is, and a guess outside the pair (Egyptian Arabic heard as another language) is redone in the main language. Pieces too short to tell take the language of the one before. Arabic sentences come out in Arabic and English ones in English, instead of one being translated or spelled in the other's letters.
- **No more stuck transcripts.** Whisper sometimes got stuck repeating itself ("ha-ha-ha-ha…", the same word over and over) on unclear or mixed speech, and the rest of the piece was lost. Repeats are now collapsed to one, for every language.
- On the "rough transcript" card, "Both" is now Arabic + English mixed.

## 0.22.1: one note, one flowing transcript

- **Pauses no longer split the note.** The transcript used to show every phrase after a pause as its own card. It now reads as paragraphs: phrases join up, and a new paragraph starts only after a real pause (about six seconds) or when one grows long. The live words while recording read the same way. Shared and exported notes too.
- **Pick the language you speak.** A language button on the record screen and on every voice note ("Transcribe again · English"), with 17 languages or "Any language". Telling Whisper the language makes it much more accurate than letting it guess. For a language other than English, the English-only live words are hidden instead of showing nonsense; Whisper writes the note when you stop.
- **Summaries read the whole transcript.** The AI used to see only the first part of a long note. Now a long transcript is read in parts, the key points of each part are noted, and the summary, decisions and follow-ups are written from all of them, through the same harness as About: fixed rules, JSON, every line checked against the transcript. It uses your chosen model, not the smallest one, and writes in the note's language. Transcribing again also summarises again.

## 0.22.0: transcripts that understand you

- **Whisper, one tap away.** The built-in live recogniser is small and English only, so it mishears accents, and its text was what stayed in the note unless Whisper had been picked in Settings. A voice note with only that rough text now shows a card: say whether you mostly speak English, Arabic or both, and get Whisper (Small on most phones, Base on phones with little memory) in one tap. The download carries on in the background.
- **Old notes get better too.** When a speech model arrives, every voice note still on the rough transcript is read again with it, newest first, then summarised and searched for tasks again.
- **Whisper Turbo.** The most accurate option, close to cloud services and best with strong accents and mixed English and Arabic. 1 GB, for phones with 8 GB of RAM.
- **Cleaner cuts.** Long stretches of talk are now split for Whisper at the quietest moment instead of every 25 seconds, so words are no longer cut in half. Whisper also uses more of the phone's cores, so it finishes sooner.

## 0.21.1: cards that open and close smoothly

- **Cards grow open and shrink closed.** Tapping a home card (or any card that opens into a pop-up, and the press-and-hold menus) now grows it out of the card with a spring, and Close shrinks it away instead of it vanishing.
- **The open card matches the card.** In Liquid Glass it has the same tinted sky as the card on Home; in Material 3 Expressive the same container colour and text colour.
- **Buttons on cards stand out in Material 3 Expressive.** "Open" on the evening card was the same colour as the card. Card buttons now take the card's own text colour, filled or outlined, so they always show.
- Long meeting names on buttons are cut at a word with "…" ("Open MOBIHITS & Mobizone…") instead of mid-word.

## 0.21.0: newer, smaller AI models

- **Qwen3 0.6B, the new small model.** 347 MB instead of 547 MB for Qwen 2.5 0.5B, newer and better at following Plait's rules. A good first download for most phones.
- **Qwen3 1.7B.** A clear step up for summaries, prep and answers, in under 1 GB.
- **Gemma 4 E2B.** Google's best small model and the best writing in Plait, Arabic included. 2.6 GB, for phones with 8 GB of RAM.
- **Picked by quality.** With several models on the phone, Plait uses the best one, not the biggest file; writing nobody asked for still uses the smallest.
- The new models run on Google's LiteRT-LM engine, on 64-bit phones. The Qwen 2.5 models stay for 32-bit phones and anyone who has them. All are Apache-2.0, need no account, and run with "thinking" off so answers stay short.

## 0.20.0: updates in the app, a smaller download, a welcome tour

- **Updates in the app.** Plait checks GitHub for a new version when it opens (at most twice a day, sending nothing about you) and shows a card with what's new. Update now downloads it in the background with progress and hands it to Android's installer, which keeps your notes and settings. The first time, Android asks you to let Plait install apps. Settings → Updates and help can check by hand or switch the automatic check off.
- **A smaller download.** 85 MB instead of 130 MB. Each APK now carries only the native code for its kind of phone (plait.apk for 64-bit phones, plait-armv7.apk for older 32-bit ones; the updater picks the right one), and unused library code is removed when the app is built.
- **A welcome tour.** A new install opens with six short pages on what Plait does, ending with connecting the phone's calendars. Settings → Updates and help → Take the tour shows it again.
- **What's new after each update.** The first time a new version opens, a card lists what changed. It is also in Settings → Updates and help.
- **Clear buttons, no fog.** Buttons, pills and the search bar are clear glass with a crisp rim, like the glass pills over the home background, instead of a milky gradient. The add-note pills each have a light colour of their own: Voice coral, Write violet, Photo teal, List amber.

## 0.19.0: brief About, suggested tasks, downloads that keep going

- **About this meeting is one short paragraph.** From the invite: at most two sentences, about 35 words, only what the invite says. With no details in the invite: one careful line from the title ("Likely a check-in on the dashboard"). The guess must share a subject word with the title, so it cannot wander off; with no AI, it reads "Likely about <title>".
- **Suggested tasks.** After a voice note or a photo note, Plait suggests up to four tasks from what was said or written, with a due time when the note gives one ("today", "by Thursday", "next week"). They show in a new card on the meeting. Add puts a task on the meeting's checklist and sets a reminder (today 5 PM, tomorrow 9 AM, Friday 9 AM, or next Monday 9 AM); the notification has Done, which ticks it off, and Tomorrow. Dismissed tasks are never suggested again. With no AI, phrases like "we need to" and "follow up" are used.
- **Model downloads keep going when you leave the app.** Android froze the app in the background, which stopped the download. A download now runs in a foreground service with a progress notification and a Cancel button, keeps the phone and Wi-Fi awake, retries a dropped connection five times, carries on after Android stops the app, and says when the model is ready.
- **Every pop-up and menu follows the chosen style.** Liquid Glass: a lit glass panel with glass pill buttons and a red Delete. Material 3 Expressive: a tonal container with tonal buttons and an error-coloured Delete. This covers note actions, the record and manage menus, editing, checklists, linking a note, the day sheet and the new-meeting and attach sheets.
- The AI checks no longer mistake the first word of a sentence for a made-up name.

## 0.18.5: colours and the real font

- **Coloured buttons keep their colour with glass effects off** (the default). The plain surface was drawn on top of the colour, so Join, Record, Reply to organizer and the + button came out nearly white in light mode and nearly black in dark mode, and their labels could not be read. The surface now goes underneath.
- **AR One Sans is really used.** The font file in the app was a broken download (a web page saved under the font's name), so every screen fell back to the phone's own font. It is now the real AR One Sans (SIL Open Font Licence).
- Settings says "1 calendar" instead of "1 calendars".

## 0.18.4: a proper AI harness

- **How Plait now asks the AI for anything it shows as fact** (About this meeting first):
  1. The input is cleaned in code before the model sees it: HTML, style sheets, scripts, links, dial-ins and codes are removed.
  2. The rules go in the model's system message; the invite goes in the user message between fence lines, marked as material, not instructions.
  3. Low temperature (0.2), so the model keeps to the likeliest words instead of inventing.
  4. The answer must be one JSON object with fixed keys (purpose, agenda, prepare; or likely, topics, prepare for a guess from the title). A broken answer gets one retry.
  5. Every field is checked in code: word limits, no code or links, and no number or name that is not in the invite (or, for a guess, in the title). Anything that fails is dropped, never shown.
  6. If nothing survives, plain rules take over, and those never show raw invite text.
- The style-sheet line ("color: #1a73e8 !important;") came from the rule-based fallback reading the raw invite; the invite cleaner now drops style sheets, scripts, comments and code-like lines for everything that reads invites.

## 0.18.3: manage Google meetings, answer invitations

- **Manage meeting works for meetings read straight from Google too.** The first change asks Google once for permission to edit events; Google then sends guests the update or cancellation. Edit details, Reschedule, Cancel this one, Delete the series / Cancel meeting.
- **Someone else's meeting:** the same button lets you answer **Going, Maybe or Decline** (phone calendars and Google); the organizer is told.
- The **account pill** on a meeting page keeps its full width; the manage button sits at the far right on its own.
- **About this meeting** no longer reads style sheets from HTML invites ("color: #1a73e8 !important;" and the like are removed before and after the AI).

## 0.18.2: manage meetings

- **Manage meeting** (calendar button at the top right of a meeting from the phone's calendars). For meetings you organise: Edit details (title, place, description), Reschedule (date and time pickers; the length is kept), Cancel meeting / Delete, and for repeating meetings Reschedule this one, Cancel this one or Delete the whole series. Destructive choices ask first. Changes are written to the phone's calendar; Google or Exchange then syncs them and sends guests the update or cancellation. Open in calendar app is always there. Meetings organised by someone else only offer Open in calendar app.
- **Close** now matches Join and the record button: a calm translucent red pill with a thin light rim, dark label, 64 dp tall.
- **About this meeting:** fixed answers like "n Summary:" (the model wrote a literal "\n"); only Purpose, Agenda and Prepare survive (Summary, Overview, Goal count as Purpose), and an answer with nothing under them is treated as empty.

## 0.18.1: strict About, smoother, day widget

- **About this meeting** follows strict rules: only facts from the invite, at most Purpose (one sentence), Agenda (2–4 short bullets) and Prepare (one sentence); call details, signatures and legal text are removed before and after; an invite with only call details counts as empty and gets the title guess. Answers are cut to size in code whatever the model writes.
- **Smoother:** this build is a release build (not a debug build), which runs Compose much faster, and it installs the ahead-of-time profiles that Compose ships, so first scrolls and animations are not interpreted.
- **Close** in pop-ups looks like the + button: a red glass pill with a dark cross, full width.
- **Add-note pills** are two to a row and equal in size: Voice, Write, Photo and List all show their word.
- **Widget: day brief.** Always the whole day, like the morning card, in four sizes: 2×1 (count and next meeting), 2×2 (big count and next), 4×2 (headline, summary, next with countdown and Join), 4×4 (all that plus the day in order, done ones ticked). The app also hands the widget the meetings it shows, so it matches Home even for calendars the widget cannot read itself.
- **Search with AI** (Notes tab): with an AI chosen, pressing Search widens your question into related words and names, searches them all, and answers from what it found, citing the notes. "Also searched" shows the extra words.

## 0.18.0: working widget, lighter AI, tasks, inviting

- **Widget fixed.** The layouts used a view type that home-screen widgets are not allowed to contain, so the launcher showed nothing; they now use only allowed views.
- **A big model no longer slows the app.** Writing nobody asked for (day summary, prep, About, voice-note summaries) waits a moment for the screen to settle and, with "Lighter model for automatic writing" on (Settings > AI, on by default), runs on the smallest downloaded model. Questions and summaries you ask for still use your chosen model.
- **About this meeting** uses only the invite; with no invite text it gives a guess from the title. Notes stay in their own card. The AI is told to lay its answer out as labelled lines, bullets and blank lines between sections, and cards show the first lines as lines and bullets rather than one run-on paragraph.
- **Pop-ups** close with a full-width red Close button.
- **Home cards** keep their light colours and dark text in dark mode too.
- **Series:** a note in one meeting also reaches later meetings with the same name on the same calendar, even when the calendar does not mark them as repeating.
- **Checklists** are built as tasks: type a task, add it, tick, rename or remove; the same editor opens when you edit a list.
- **Add people and Share link** on the Guests card. Add people opens Android's contact picker (it lists contacts from every account on the phone, and Plait only sees the one you pick). For a meeting in the phone's calendars the guest is written into the meeting and your calendar app sends the invitation when it syncs; otherwise an invitation email is drafted. Share link sends the title, time and call link through any app.

## 0.17.2: lighter, smarter About, brief widget

- **Lighter animation:** with glass effects off (the default) the home sky is a still gradient, so nothing redraws every frame; the month pager builds only the month on screen.
- **About this meeting** looks in order at: the invite's text; if there is none, this meeting's own notes (not deleted ones, not older meetings in the series); if there are none, a clearly marked guess from the title ("Likely about", "Might cover", "Prepare"), with Record and Write. AI answers are checked against what they were given.
- **Every card on the Brief tab opens** (Guests, About, Notes summary) into a scrollable pop-up with tidy paragraphs and bullets and a Close pill in the current style.
- **Account pill** on a meeting page is as tall as the back button with a fixed gap after it; in Material 3 Expressive it is a tonal chip with the calendar's initial.
- **Widget: Plait brief.** The home-screen widget now shows the brief for the time of day, coloured like the morning, midday and evening cards. Small: the next meeting with a live countdown and Join. Medium: plus the greeting, the day's headline and a line about what's left (or tomorrow, in the evening). Large: plus the rest of the day, four meetings. It picks the layout to fit as you resize it, reads Google and subscribed calendars as well as the phone's, and refreshes when you leave the app, when Plait syncs and every 30 minutes.
- **Back works** again while a day's events are open in the calendar (the month swipe edge is only reserved when no day is open).

## 0.17.0: checklists, series notes, glass calendar

- **Checklists** are a new kind of note: "List" next to Write and Photo (in a meeting or on the Notes tab), one item per line. Tick items off on the card, add more at the bottom, edit with press-and-hold. Use them for an agenda, the outcome, or follow-up tasks. They show in the Notes summary (open items) and under a new **Lists** filter.
- **Repeating meetings carry notes forward**: notes, photos and lists from an earlier meeting in the series show under "From earlier in this series" in later ones, and in their Notes summary.
- **Home cards** fade out softly at the bottom (and a little at the top) as they scroll.
- **Evening numbers** are two tiles to a row, half the card each, with no empty space.
- **Swipe to delete:** only the uncovered strip turns into a red pill beside the row (nothing shows through the card) and the bin sits in its middle.
- **Calendar:** the Day / Month switch is liquid glass, month days are glass tiles with a sheen and rim, and the day wheel is glass.

## 0.16.3: fuller cards, easy months, honest About

- **Home cards** show the whole part of the day again (scroll inside the card; tap for the bigger pop-up). Each meeting is a tall banner: title first and large, then the time with its length, where (call app or place), guests' faces, flags and Join.
- **Months are pages**: the calendar follows your finger, flings and snaps both ways (a fifth of a swipe is enough), with the next month alongside.
- The **logo and name row** no longer overlaps the Calendar and Notes headers; their content starts below it, lined up with Brief.
- **About this meeting** only uses what is written in the invite and in this meeting's notes. Any line the AI writes with a name or number that is not in those is dropped. With no description and no notes it says "No info attached to this invite" and gives an **Expected** line based on the title, plus Record and Write.

## 0.16.2: refresh, logo, polish

- Home cards fill the space between the Morning / Midday / Evening switch and the dock again.
- **Refresh really refreshes:** it reloads the phone, Google and subscribed calendars, syncs with your other phones, and has the AI write the day summary, meeting prep and "About this meeting" again from the fresh data.
- **Plait logo and name** sit at the left of the Home top row. Settings now has a gear icon.
- **Text colours** now follow the theme everywhere, including text that has no colour of its own, which could turn dark on dark or light on light after switching light/dark or style.
- **About this meeting** is rewritten by the AI in plain sentences; join links, ids, slashes and rulers are stripped from the invite first and again from the result.
- **Calendar months:** a lighter swipe and flick is enough, the left edge no longer triggers Android's Back gesture, and the month title slides as it changes.
- **Material 3 Expressive:** pills and circles throughout (round filter chips, round toggle buttons, a pill navigation bar) and one card corner radius. Pop-ups open with a spring.

## 0.16.1: calmer home, bouncier gestures

- **Home cards** are shorter. Morning / Midday / Evening sit under the refresh and settings icons, as wide as the card. The card is a short summary (headline, a line or two, the next meetings); tap it to open everything in a full-screen pop-up. Meetings now lead with the **title**, then the time.
- **Glass effects are off by default** (turn them on in Settings > Appearance).
- **Motion:** page changes and tab changes use the same Material 3 Expressive springs in both styles, with a quick fade; much less scaling and flashing.
- **Calendar months follow your finger** (drag, flick or let go past a third), with the neighbouring month sliding in beside it.
- **Swipe to delete** is springy: the row overshoots as it goes, the bin pops, and lifting early bounces the row back and cancels.

## 0.16.0: real Material 3 Expressive

- **Style is now a whole-app switch.** Settings > Appearance > Style offers Liquid Glass or Material 3 Expressive. Because the app builds its interface in one style, choosing the other asks you to restart (a "Restart now" button is in the pop-up and stays in Settings until you do).
- **Material 3 Expressive** uses Material's own components and theme: your phone's Material You colours, the expressive spring motion, Material buttons with shape morphing, the Material navigation bar for Brief / Calendar / Notes, segmented buttons for the other switches, the floating "+" action menu, filter chips, switches, ripples with a springy squish on press, flat tonal containers for the home cards and meeting cards (no glass, no animated sky), and spring page transitions.
- Needs Material3 1.5.0-alpha10 (the expressive components are only public in the 1.5 line).

## 0.15.3: Notes tab tidy

- Add-a-note is now one row of glass pills in the Join / Record style (Write, Photo; Voice too inside a meeting). Photo opens a small pop-up for Take photo, Choose, and the picture language. The Notes filter (All, Voice, Text, Photo) is the same glass switch used elsewhere, not loose chips. Voice notes start from the + button.

## 0.15.2: meeting page polish

- **Record** is the round red glass mic again on the meeting page; it asks Record a call or Take a voice note. **Join** reads "Join Teams" / "Join Meet" / "Join Zoom" on one line.
- **Cards have a size limit.** Guests shows the first three (tap for everyone), About this meeting shows up to three short sections (tap for the full text) and Notes summary shows the first two (tap to read all). Full versions open in a solid pop-up.
- **Teams pages** mix Teams violet, Microsoft blue and Xbox green, and card tints are a little stronger.

## 0.15.1: tidier, clearer

- The **assistant is removed**; the bottom-right button is a round **+** again with three choices: Voice note, Record this call, New meeting. The Record pill above the Home cards is gone.
- Pop-up menus and sheets (Record, New meeting, note actions, linking) are solid again so their text is clear.
- **Material 3 Expressive** now uses proper tonal roles: filled accent buttons, tinted containers, a filled pill on the selected tab, no glass rims and no animated sky.
- The Notes tab's add-note card is smaller.

## 0.15.0: meeting pages, assistant, styles

- **Meeting page.** Cards are now Guests (with Reply to organizer, Email everyone, Reply to the invite email as glass pills), About this meeting (the key points of the invite, picked out rather than copied; or "No info attached to this invite" with Record and Write pills) and Notes summary. The separate Meeting info card is gone. The Brief / Notes / Transcript switch is liquid glass, and every card is liquid glass tinted from the service the meeting runs on: Teams purples, Google Meet's blue, green, yellow and red, Zoom blues, Webex teals (or the calendar's colours when there is no call link).
- **Record** is a red liquid-glass pill on the meeting page next to Join, and above the cards on Home. Tapping it asks: Record a call, or Take a voice note. The old round mic button is gone from Home.
- **Assistant.** The bottom-right button on Home is now the assistant. Ask about your day, meetings, transcripts, notes, photos and follow-ups; it answers from your calendar and notes with the sources listed, using the AI you chose in Settings.
- **Settings pages.** Tapping a section opens it on its own page (it slides in; Back returns to the list). Appearance has a new **Style** switch (Liquid Glass or Material 3 Expressive: solid tonal surfaces and bouncier springs), **Glass effects** and **Reduce animations**. Phones that report low memory start with glass off and animations reduced.
- **Lighter animation.** The animated sky's web layer runs only on the card you are looking at, and not at all with reduced animations, plain surfaces or Material. Big glass surfaces skip the refraction shader. Pages now slide in and out with a spring-like ease (shorter and shallower when animations are reduced), and the Brief / Calendar / Notes tabs slide between each other.
- No more "written by AI" or "can get things wrong" lines anywhere.

## 0.14.5: One Sans

- All text now uses **AR One Sans**, the Google Fonts family closest to the name "One Sans" (there is no Google font with exactly that name). It covers Latin text; Arabic text falls back to the phone's own Arabic font.

## 0.14.2: animated skies, true guest answers, Join, meeting info

- **Skies move for real.** The colour of each home card now flows in Compose (so it always animates unless Android's animations are switched off), and GSAP adds the sun, rays, clouds and stars on top.
- **Guests** are read for every event from the phone's calendar, not only those flagged as having guests, and show the calendar's own answer: Going, Maybe, Declined or Waiting. An organizer whose answer is blank counts as Going, and an organizer the calendar names but leaves out of the guest list is added to it.
- **Join** is a liquid-glass pill like the red mic button: full width next to the mic on the meeting page, and the same pill (shorter) on the home cards. The main buttons on the Home and meeting screens (tab bar, mic, Join, record controls) are all 64 dp tall, so they line up.
- **Meeting info** is a new card on the Brief, under the invite text: the AI you chose picks out Purpose, Agenda, Decisions needed, Prepare and Details from the invite (once per invite, remembered). With no invite text it says "This meeting has no info" and offers Record and Write buttons that attach to the meeting.

## 0.14.0: readable in both themes, a real Notes library

- **Readable everywhere.** Text is pure white in dark mode and pure black in light mode, on every screen and on the home cards. The home skies follow the theme: pale sunrise, sky and dusk in light mode, deep ones (with stars in the evening) in dark mode. The cards keep their tinted glass colour (morning gold, midday blue, evening lilac) with the animated sky laid over it at moderate strength. Glass rows on the cards are frosted white in light mode and smoked in dark mode.
- **No example data.** The built-in example meetings, notes and transcripts are gone. Until a calendar is connected, Home says so and offers to connect one; everything you see is yours.
- **Notes tab is a library.** All voice, written and photo notes in one list, newest first, with filters (All, Voice, Text, Photo). It no longer depends on meetings: tap a note to play it, read the transcript, or read the text taken from a picture. "Link to a meeting" (also "Change meeting" and "Unlink") connects a note to a calendar event when you want it there. The mic on the Notes tab records an unlinked note. Swipe left to delete (with Undo), press and hold for Open, Link, Edit and Delete.
- **Meetings:** Context is removed. Brief shows the invite's description, or "No info attached to this invite.", followed by a Notes summary of the notes linked to that meeting. Guests are back on the Brief with their RSVP; tap a guest to email them, or use "Reply to organizer", "Email everyone" and, when an invite email was attached to the meeting, "Reply to the invite email" (it opens your email app with the address and subject filled in). Tabs are Brief, Notes, Transcript.

## 0.13.0: living home cards, swipe notes, one quiet AI

- **Home cards** have an animated sky, driven by GSAP (bundled, works offline; falls back to a drawn version if the phone has no WebView): a morning sunrise (warm gradient, a sun that rises and breathes, soft rays), a midday sky (blue gradient with drifting clouds) and an evening dusk that darkens toward night with the hour and brings out twinkling stars. Text colour follows the sky. Rows and tiles on the cards are glass. With animations turned off in Android the sky stands still.
- **Buttons** share the look of the record button: lit glass with a bright rim, a soft glow under coloured ones, a lift and bloom on press. Entrances and presses are springier.
- **Tabs:** the label always fits inside the highlight and bounces as the highlight approaches and leaves.
- **Notes:** swipe a note left to delete it (with Undo), or press and hold for Edit and Delete. The delete buttons are gone. Voice, text and photo notes behave the same on the Notes tab and inside a meeting.
- **No AI nagging.** The "rewrite with another AI" and "turn on an AI" prompts are removed everywhere. Once you pick a model it is used silently; the morning summary is written once a day by it.
- **Nothing empty:** no brief, prep or context card is shown when there is nothing to put in it.
- **Calendar** shows the full month name.

## 0.12.1: tidy settings, one default AI

- **Settings** (the icon at the top right) now folds. Each section is a card with a one-line summary of its state ("Writing: Qwen 2.5 · 1.5B · Voice: Whisper Base", "10 min before", "Off") and opens when tapped; which ones are open is remembered while you use the app. Sections: Accounts to show, Add an account, Import a calendar, Where new meetings are saved, AI on this phone, Reminders and widget, Appearance, You and backups, Sync between your phones, Check this phone, Advanced.
- **One default AI.** Whatever you choose in "AI on this phone" is used for everything that needs it: summaries of voice notes, meeting prep, the explanation of attached emails, the morning rewrite, answers to your questions, and quick add for sentences the rules can't read. A model you download (or that Plait finds on the phone) becomes the default straight away. The new "Your defaults" card shows what is used for writing, voice notes and search, and the chosen option is marked Default.

## 0.12: no repeat downloads, sync, wide screens, focus time, team notes

- **Models are never downloaded twice.** A model counts as installed as soon as all its files are on the phone at the exact size, even if a download was interrupted at the very end or you copied the files in yourself. Before any download Plait looks for the files first. In the hub, "Already have a model?" can scan a folder you pick (any depth), files you pick, or the drop folder `Android/data/app.plait/files/models` (copy files there over USB). It copies matching files in; your originals are left alone. If Gemini Nano is ready, the hub says so above the downloads.
- **Sync between your phones, no server of ours:** choose a folder that Google Drive, Dropbox, Nextcloud or Syncthing keeps in step. Each phone writes one file there, encrypted on the phone with your passphrase (AES-256), and reads the others'. Notes, written and photo notes, emails, done follow-ups and prep merge by adding and by carrying deletions, so a sync cannot lose a note. Recordings and photos sync only if you turn that on; otherwise the transcript and summary come across and the card says the recording is on another phone. It runs when Plait opens and after you save a note, or with Sync now.
- **Check this phone (hub):** a self-check list (permissions, exact alarms, AI engine, models, widget, storage), a small on-phone log with crash records you can share, and a frame-rate overlay.
- **Tablets and foldables** can rotate and keep a centred column up to 720 dp wide. Phones stay upright.
- **Watches:** the meeting alert and its Join button are marked for Wear OS, so they appear on a paired watch. There is no separate watch app.
- **Desktop view:** "Notes as Markdown for a computer" saves every meeting with notes as a Markdown file in a zip with an index; open it in any Markdown reader.
- **Focus time:** in the evening card, if tomorrow has a free stretch of 90 minutes or more, Plait offers to hold it. Follow-ups can be booked as 30-minute to-dos into the first free gap. Nothing is added to your calendar until you tap.
- **Team notes:** "Send my notes to a teammate" (meeting page, Share) makes a file with your notes for that meeting, marked with your name. Emails and recordings go only if ticked; a passphrase is optional. The teammate imports it from the hub and it attaches to their copy of the meeting (same title, within 10 minutes), or shows as shared notes if they have none.

## 0.11: prep, series memory, sharing, free times, protected backups

- **Prep (meeting page, Brief tab):** a 30-second plan: goal, what to remember, what to raise. Written by the AI you chose from the invite, guests, agenda, last time's note, open follow-ups and attached emails; or put together by rules when no AI is on. It is kept, editable, and can be written again or reset. Under two hours before your next meeting, Plait writes it quietly if an AI is on, and the reminder uses its goal line.
- **Since last time:** for a recurring meeting (same title, numbers ignored): the decisions from the last note and the follow-ups still open, with a Done button.
- **Share (meeting page):** PDF, Markdown or plain text of a meeting: why you met, agenda, prep, voice-note summaries and transcripts, written and photo notes, email subjects, open follow-ups. Built on the phone and passed to Android's share sheet. Arabic is laid out right to left.
- **Find a time (New meeting):** free gaps in your calendars for the meeting length (weekdays 9 AM to 5 PM, next 7 or 14 days). Tap one to fill the form, or send the options as a message. It sees only the calendars on this phone, not other people's availability.
- **Protected backups (hub):** an optional passphrase encrypts the backup (AES-256-GCM, key from PBKDF2-SHA256, 1 MB sealed chunks that cannot be reordered or cut). There is no recovery: forget the passphrase and the file cannot be opened.
- Note summaries can be written again, and every AI text says which engine wrote it.

## 0.10: choose your AI, better speech, search, and the daily loop

- **AI on this phone (hub):** pick what writes summaries and answers: Automatic (Gemini Nano, then a downloaded model, then Plait's rules), Gemini Nano, a downloaded model, or rules only. Downloadable models show their exact size before you tap:
  Qwen 2.5 0.5B (547 MB), Qwen 2.5 1.5B (1.6 GB), Whisper Tiny (104 MB), Base (161 MB), Small (375 MB), meaning-search (6 MB). Nothing downloads until you tap; downloads resume; you can cancel or delete to get the space back; on mobile data you are asked twice. Every AI-written text says which engine wrote it.
- **Speech:** the built-in recogniser still shows words live. If a Whisper model is chosen, the saved recording is re-read after you stop, split at silences, with a language of your choice or auto-detect (Arabic included). "Improve transcript" re-runs it on older notes.
- **Search and ask (Notes tab):** word search over meetings, voice notes, written notes, photo text and emails, English and Arabic, nothing to download. With the 6 MB model it also matches by meaning (English only). "Ask" has your chosen AI answer from the passages found and cite them.
- **Follow-ups (Notes tab):** what you said you would do, from the AI's list or from phrases like "we need to", and from `- [ ]` lines in written notes. Done, or Tomorrow.
- **Quick add:** type "lunch with sam@acme.com friday 1pm for 1h at Cafe Nero" in New meeting and the form fills in (English).
- **Widget and alerts:** a home-screen widget (next meeting, live countdown, Join); an alert 5/10/15/30 minutes before each meeting with a line about what it is for and a Join button; and a 6:00 PM reminder when finished meetings have no notes. They use the phone's alarm clock and work with Plait closed.
- Plait now has an app icon.

Not verified on a device: the downloaded-model runtime (MediaPipe) with these exact model files, Whisper's auto language detection, alarm timing under battery savers, and the widget countdown. The download links are public Hugging Face files; if a host moves a file, the download will fail with a message.

## 0.9.1: pure black

Dark mode is now true black (#000000) with slightly lifted surfaces, softer colour glows, and a black window and system bars, so an OLED screen switches its pixels off. It is on by default and can be turned off in the hub ("Pure black in dark mode") for the earlier blue-grey dark.

## 0.9: one-canvas month, day wheel, accounts, backup, 12-hour

- **Calendar:** Day and Month only. Day is the vertical wheel with the chosen day always in the middle and that day's hours beside it (Week is gone; the wheel replaces it). Month is drawn on a single canvas instead of 42 separate cells, which was the main cause of the lag: picking a day only repaints, and the grid never re-lays-out. Tapping a day opens a sheet of its events.
- **Home:** the cards are Liquid Glass with their accent colour kept as a tint, so the background shows through. Text follows the light or dark theme. Morning / Midday / Evening is a glass switch in the top row, left of the two round buttons, and it follows your swipe.
- **Feedback:** every tap now behaves like the red record button: the control lifts on a springy bounce, a light blooms under the finger, and a light haptic tick confirms it.
- **Accounts:** the hub lists accounts, not calendars. Switch an account off and none of its events appear; "Choose calendars" opens the account's calendars one by one. Holiday, birthday and similar calendars start switched off. Until you choose, everything shows, and the hub says so.
- **You and your devices:** set your name (used in the greeting), export a backup (settings, voice notes, written and photo notes, attached emails, calendar links, optionally recordings and photos) as one zip, and import it on another phone. Importing only adds what is missing and never replaces or deletes. The file is not encrypted. Calendar accounts themselves are not in the file: add them on the new phone, then pick the same accounts. Live cloud sync comes later.
- **Times are 12-hour** everywhere ("3:30 PM", "9 AM"). Events saved by earlier versions are converted when loaded.

## 0.8: calendar sheet, glass controls, Arabic photos

- **Calendar:** tapping a day in the month opens a sheet with that day's events; tap an event to open it, Back returns to the sheet. The grid itself never resizes (that was the lag). It appears at once; the event dots fill in a moment later, after they are worked out off the main thread. Day cells show a number and a few dots only.
- **Glass:** the home cards are real Liquid Glass (blurred, tinted, lit at the edge) over the soft background. Every button, chip and round icon button is drawn in the same glass look, in two sizes. The top-right buttons are the same size on every tab.
- **Press feedback everywhere:** buttons, chips, cards, calendar days, event rows and switches dip with a spring, light up under the finger and give a light haptic tick.
- **Text that wraps well:** headings and buttons break in balanced lines, body text avoids a lone short last line, long accounts are shortened in the middle (keeping the domain) and never wrap letter by letter, and button groups sit in wrapping rows so no button is squeezed. Delete is one consistent control: a trash button that turns into a red "Delete" which must be tapped again.
- **Arabic in photos:** choose the language under "Add a note" (Auto, English / Latin, Arabic). Arabic is read with Tesseract (Arabic and English data ship inside the app, about 5 MB); Auto reads with both and keeps the Arabic result when it finds enough Arabic letters. Arabic text displays right to left. Printed text only; handwriting and low-light photos read poorly, and it takes a few seconds.
- Not done: voice notes are still English only (the speech model is English). Arabic speech needs a model of 150 MB or more.

## 0.7: home cards, notes and calendar

- **Home is one card at a time, and the cards follow the day.** It opens on the part of the day you are in and you swipe sideways for the others (the chips on top jump too).
  - *Morning:* a greeting, a headline ("Four meetings today."), where you are in the day ("First up: Standup at 09:00, in 1 h."), the day strip, and every event in order with join buttons.
  - *Midday:* what is done, what is still ahead, and which finished meetings have no notes.
  - *Evening:* time in meetings, share of an 8 h workday, longest meeting, busiest hour, time by kind of meeting, who still needs notes (with an "Add note" button each), and a look at tomorrow.
  The numbers come from the calendar and the notes on this phone. They are only as complete as your calendar.
- **Notes come in three kinds** (Notes tab of a meeting): voice, written, and photo. A photo is read on the phone with ML Kit's text recogniser (bundled, offline) and kept as copyable text next to the picture. It reads printed text well, handwriting poorly, and Latin scripts only.
- **One button style.** Every button is as wide as its label, at least 48 dp (36 dp for small), and long labels wrap rather than being cut.
- **Calendar speed.** The month grid no longer re-measures 42 cells on every frame while a day opens, cells are plain boxes, switching view is a short fade, and the day pane has no inner animations.
- **Week view.** The chosen day sits in the middle of a vertical wheel. Drag the wheel to roll through days (it coasts, ticks as each day passes, and settles on a day); tap a day to jump to it. The chosen day's hours are shown beside it, wide enough to read.

## 0.6: home, hub and sending back

- **Brief (home):** a big headline for the day ("Three meetings ahead."), a short summary (next meeting, outside guests, back-to-back runs, longest break, what needs prep), a strip showing how the day is laid out, then one bold card per meeting. If emails are attached to a meeting, its card leads with the most useful line from them. All of it is worked out on the phone. On phones with Gemini Nano, a button rewrites the summary in friendlier words.
- **Back goes back:** the tab, scroll position and selected calendar day on the home screen are kept, so returning from a meeting, the hub or a recording lands where you left. After recording from a meeting, Back-from-save returns to that meeting.
- **Hub (the hub icon, top right):**
  - lists every account the phone syncs (Google, Outlook/Exchange, iCloud via CalDAV) with a switch per calendar;
  - "Add a Google account" and "Add Outlook, Microsoft 365 or Exchange" open Android's own add-account screen, so Plait never sees a password and the calendars appear on return;
  - import by link (Outlook/iCloud/Google ICS) or by `.ics` file;
  - choose where meetings made in Plait are saved.
- **New meeting (mic button, then "New meeting"):** title, day, time, length, guests, place, notes. It is saved to the calendar chosen in the hub through Android's calendar provider. Android's sync then pushes it to Google, Outlook or iCloud, and guests get the account's invite. With no account, it goes to a "Plait (this phone only)" calendar. Needs the calendar write permission.
- **Notes to the calendar:** optional switch in the hub. After a voice note, the summary and follow-ups are written into the event description between "Plait notes" markers (replaced, never duplicated). Works for events from phone calendars.
- **Speed:** entrance animations play once per screen arrival (not on every scroll), press feedback and loops no longer recompose cards, tab switches are instant, screen transitions are plain fades, and the glass loses its chromatic effect.

### Voice notes

- A voice detector (adaptive to the room's background noise) feeds only speech to the recogniser, keeps the 300 ms before a word, and ends a phrase after 440 ms of quiet. The recorder uses the phone's voice-recognition mic with noise suppression and automatic gain where available.
- Each phrase gets a confidence from the speech engine. Words it is unsure of are dropped from otherwise solid phrases, and shaky phrases are marked "not sure" in the transcript.
- Transcripts are tidied: filler ("um", "uh") and stutters removed, capital letters, question marks.
- On supported phones, Gemini Nano writes a summary, decisions and follow-ups after saving (or on tap). Elsewhere, the rule-based summary is used.
- Limits: the bundled speech model is small and English only, and it will still mishear names and jargon. The AI summary reads the transcript, so it inherits those mistakes.
