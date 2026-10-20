# Plait

**Site and trailer:** https://mo3shma.github.io/ProjectPlait/ · **Download:** [plait.apk](https://github.com/mo3shma/ProjectPlait/releases/latest/download/plait.apk) (Android 8+)

An Android calendar that merges your calendars into one agenda, joins calls in one tap, and records voice notes that are transcribed on the phone.

Kotlin, Jetpack Compose, Liquid Glass look (Apple's Liquid Glass guidance, approximated with the open-source Backdrop library).

## Status

| Area | State |
|---|---|
| Today, Calendar and Notes tabs, Liquid Glass UI | Built. Not yet seen on a real device. |
| Calendar tab: Day (rows), Week (time grid), Month (grid), day pane that slides up while the calendar shrinks to the top half | Built. Layout logic tested; animations need a real-device look. |
| Meeting cards on Today: one colour per kind of meeting, with why you are meeting, agenda, guests and a join button | Built. Colours and layout need a real-device look. |
| Guest lists with accepted / declined / waiting | Built. Read from the phone's calendar, Google, and calendar links. |
| "Why you're meeting" without any AI service | Built. Runs on the phone, free: see "How the brief works". |
| Past events | Built. "Earlier today" on Today, dimmed events in every calendar view. |
| Outlook and Teams meetings | Built. Through the phone's Outlook/Exchange account, or by pasting a published Outlook calendar link. Teams links are found in Outlook's own link field and in invite text, including Microsoft's rewritten "Safe Links". |
| Join call (Meet, Zoom, Teams, Webex links found in the event) | Built. 7 unit tests. Opens the link with the system. |
| Voice notes: record, save, play back, bookmark | Built. Needs a real-device check (no emulator was available). |
| Live transcription, offline (Vosk, English small model, bundled) | Built. Needs a real-device check. |
| Recording with screen off (microphone foreground service) | Built. Needs a real-device check on Android 14+. |
| Phone calendars (Google, Outlook/Exchange, iCloud via a CalDAV app, Samsung, ...) | Built. Reads Android's calendar store. **No setup**, one permission. Needs a real-device check. |
| Google Calendar direct (read-only) | Built, fixture-tested parsing. Optional, needs the one-time Google Cloud setup below. |
| Calendars added by link (.ics): Outlook, iCloud, Google secret link, anything else | Built. Recurring meetings are expanded. Needs a real-device check. |
| Outlook / iCloud direct sign-in (Microsoft Graph, CalDAV login) | Not started. Needs a Microsoft Entra app registration, the same kind of setup as Google Cloud. |
| Summaries of notes | Not started (the old "Auto summary" only appears on the example event). |

115 unit tests cover link detection (including Outlook Safe Links), WAV encoding, note storage, Google and phone-calendar parsing, calendar-feed parsing with recurring meetings, caching, the "up next" logic, month/week grids, overlap layout and calendar navigation. Run them with `./gradlew :app:testDebugUnitTest`.

## Build

Needs JDK 17+ and the Android SDK (platform 36, build-tools 36). Set `sdk.dir` in `local.properties`.

```
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
```

Maven Central sometimes answers 429 (rate limit). Run the command again.

## Getting your calendars in

### Option A: calendars already on the phone (no setup)

Open Plait and tap **Use my phone's calendars**, then allow calendar access. Plait reads the calendars Android already syncs, so it covers every account that is set up in the phone's own Calendar app: Google, Outlook/Exchange, Samsung, and iCloud if you have added it through a CalDAV app such as DAVx5. Calendars you have hidden in the system Calendar app stay hidden. Declined and cancelled events are left out.

Limits:
- Plait only sees what the phone has synced. If an account isn't in the system Calendar app, it isn't in Plait.
- Android's calendar store has no field for video-call links, so Plait looks for Meet, Zoom, Teams and Webex links in the event's location and description. Zoom and Teams invites nearly always put the link there. Google sometimes keeps a Meet link out of the text, in which case the event shows no Join button. Option B reads the link straight from Google.

### Option B: add Outlook, iCloud or any calendar by link (no sign-in, no setup)

Tap the sliders button (top right), paste the calendar's published link and tap Add. Plait downloads it, expands recurring meetings, and refreshes it every 15 minutes.

- **Outlook / Microsoft 365:** Outlook on the web → Settings → Calendar → Shared calendars → Publish a calendar → choose "Can view all details" → copy the **ICS** link. Teams meeting links come through with the events.
- **iCloud:** Calendar app → share the calendar → Public Calendar → copy the link.
- **Google:** calendar Settings → Integrate calendar → Secret address in iCal format.

A published link is readable by anyone who has it, so treat it like a password. Work or school accounts often switch publishing off. If yours does, use Option A with the account added to the phone.

Why not "Sign in with Microsoft"? That needs an app registration in Microsoft Entra, and many company tenants block unverified apps. The link and the phone's own Exchange account avoid both.

### Option C: connect Google directly (one-time Google Cloud setup)

The app holds no client ID or secret. Google recognises it by package name and signing certificate, so you register those once.

1. Open console.cloud.google.com and create a project (or pick one).
2. APIs & Services → Library → enable **Google Calendar API**.
3. APIs & Services → OAuth consent screen → choose External, fill in the app name, add your own Google account under **Test users**, and add the scope `.../auth/calendar.readonly`.
4. APIs & Services → Credentials → Create credentials → OAuth client ID → **Android**.
   - Package name: `app.plait`
   - SHA-1: the fingerprint of the key that signed the APK you install.
     - APK from the Releases page: `77:EC:81:98:56:2D:3C:F1:81:44:EC:17:45:7B:31:B6:07:2E:52:BC`
     - APK built on your own machine: run `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
5. In Plait open the sliders button → Google account → Turn on, and approve read-only access. Events from three months back to six months ahead appear, from every calendar you have switched on in Google Calendar.

If sign-in says it "isn't set up for this build yet", the package name or SHA-1 in step 4 doesn't match the installed APK. The consent screen stays in "Testing" mode for personal use; publishing to other people needs Google's verification for the Calendar scope.

## Check on a phone

1. Install the APK. Open it: you see example events and a "Use my phone's calendars" card. Tap it and allow access: your real day should replace the examples.
2. Drag across the bottom tab bar. The knob should lift and refract. Tap the mic: it should grow into a three-item menu.
3. Open "Design review", tap the mic, allow the microphone. The timer runs, the waveform follows your voice, words appear as you speak (the first time can take several seconds while the speech model unpacks).
4. Press Home during a recording. A "Recording a voice note" notification should stay, and the recording should continue.
5. Stop. You land on the event with the note: play it, tap a bookmark chip to jump to it, read the transcript tab.
6. Airplane mode on, record again: it should work the same (nothing is sent anywhere).
7. Calendar tab: switch Day / Week / Month. Day is rows, Week and Month are grids. Swipe sideways to move between months, weeks and days.
8. In Month or Week, tap a day. A pane should rise over the bottom half while the calendar shrinks into the top half. Drag the pane down to close it, swipe it sideways or use the arrows to change day, tap an event to open it.
9. Today tab: "Earlier today" lists finished events. Past events are dimmed in the Calendar tab.
10. Add an Outlook link in the sliders sheet and check a Teams meeting shows "Join Microsoft Teams".
11. Today tab: each meeting is a coloured card with the reason for the meeting, the agenda (for the next one), and who is coming. Open one: the **Brief** tab has the full guest list with who has accepted, declined or not replied.
12. In your real calendar, find an invite with a description and one without. The first should show the invite's own words; the second should show an italic guess.

## How the brief works (free, on the phone)

Nothing here calls a server or a language model.

1. **Guests** come straight from the calendar: the phone's attendee table, Google's attendee list, or the `ATTENDEE` lines in a calendar link. Rooms are left out. The organizer is flagged and listed first.
2. **Cleaning the invite.** Descriptions are often HTML and full of join instructions. Plait removes the Teams, Zoom and Meet blocks, dial-in numbers, passcodes, Outlook Safe Links and bare URLs, and keeps what a person wrote.
3. **Why you're meeting.** In order: a `Purpose:`, `Goal:` or `Objective:` line; otherwise the one or two most representative sentences (ranked by how many of their words recur in the text, with a small bonus for the opening); otherwise a plain guess from the title and guest list, shown in italics and labelled as a guess. **Agenda:** bullets under an "Agenda" or "Topics" heading, or two or more bullets in a row.
4. **Kind and colour.** The title is matched against words like "1:1", "stand-up", "review", "planning", "interview", "lunch", "dentist", and the guest list decides ties (two people suggests a 1:1, mostly outside email domains suggests an external meeting). Each kind has its own card colour.
5. **Last time.** For a meeting with the same title, Plait looks for your own earlier voice note and shows a short summary and any follow-ups.
6. **Voice-note summaries and follow-ups** use the same ranking, plus phrases such as "we need to", "follow up" and "by Friday".

Limits: this finds and tidies what the invite already says. It can't explain a meeting whose invite is empty beyond a guess from the title, it only understands English, and the follow-up finder will miss things and sometimes pick up the wrong sentence. A real summarizer needs a language model. The option that is free and stays on the phone is a small downloadable model run with Google's MediaPipe LLM engine (a Gemma model of 1 to 2 GB), or Gemini Nano through Android's ML Kit GenAI on phones that have it. Both are possible later as an optional add-on.

## Email context (free, on the phone)

Share an email to Plait (select text in your mail app and choose Share, or share/open an `.eml` file), or paste it in a meeting's **Context** tab. Plait suggests the meeting it belongs to, using topic words, guests' addresses and how soon the meeting is, and attaches it when you tap.

For each meeting the Context tab shows how strongly the emails relate to it, highlights (decisions, risks, deadlines, asks, to-dos, numbers, attachments), and "to settle in the meeting". Meeting cards on Today show an "N emails" chip and a one-line takeaway.

- **Everywhere:** a rule-based engine in plain Kotlin. English only. It can misjudge relation and highlights; read the email if it matters.
- **Optional, supported phones only:** an "Ask Gemini Nano" button uses the phone's built-in model through ML Kit GenAI (AICore). It works only on devices that support it, may need a one-off model download, and can get things wrong. Its output is labelled as such.
- Mail is only read when you share or paste it. Plait has no mailbox access, and nothing leaves the phone.

Check on a phone: share text from a mail app, share an `.eml`, attach to a suggested meeting, open the Context tab, try "Try an example" in demo mode, and try the Gemini Nano button on a supported phone. None of this has been run on a device yet.

Files: `data/context/` (Email, Thread, ContextModel, ContextAnalyzer, AiPrompt), `ai/OnDeviceAi.kt`, `share/ShareInbox.kt`, `ui/screens/ContextTab.kt`, `ui/screens/AttachSheet.kt`.

## Home, hub and sending back (0.6)

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

### Voice notes, 0.6

- A voice detector (adaptive to the room's background noise) feeds only speech to the recogniser, keeps the 300 ms before a word, and ends a phrase after 440 ms of quiet. The recorder uses the phone's voice-recognition mic with noise suppression and automatic gain where available.
- Each phrase gets a confidence from the speech engine. Words it is unsure of are dropped from otherwise solid phrases, and shaky phrases are marked "not sure" in the transcript.
- Transcripts are tidied: filler ("um", "uh") and stutters removed, capital letters, question marks.
- On supported phones, Gemini Nano writes a summary, decisions and follow-ups after saving (or on tap). Elsewhere, the rule-based summary is used.
- Limits: the bundled speech model is small and English only, and it will still mishear names and jargon. The AI summary reads the transcript, so it inherits those mistakes.

## Home cards, notes and calendar (0.7)

- **Home is one card at a time, and the cards follow the day.** It opens on the part of the day you are in and you swipe sideways for the others (the chips on top jump too).
  - *Morning:* a greeting, a headline ("Four meetings today."), where you are in the day ("First up: Standup at 09:00, in 1 h."), the day strip, and every event in order with join buttons.
  - *Midday:* what is done, what is still ahead, and which finished meetings have no notes.
  - *Evening:* time in meetings, share of an 8 h workday, longest meeting, busiest hour, time by kind of meeting, who still needs notes (with an "Add note" button each), and a look at tomorrow.
  The numbers come from the calendar and the notes on this phone. They are only as complete as your calendar.
- **Notes come in three kinds** (Notes tab of a meeting): voice, written, and photo. A photo is read on the phone with ML Kit's text recogniser (bundled, offline) and kept as copyable text next to the picture. It reads printed text well, handwriting poorly, and Latin scripts only.
- **One button style.** Every button is as wide as its label, at least 48 dp (36 dp for small), and long labels wrap rather than being cut.
- **Calendar speed.** The month grid no longer re-measures 42 cells on every frame while a day opens, cells are plain boxes, switching view is a short fade, and the day pane has no inner animations.
- **Week view.** The chosen day sits in the middle of a vertical wheel. Drag the wheel to roll through days (it coasts, ticks as each day passes, and settles on a day); tap a day to jump to it. The chosen day's hours are shown beside it, wide enough to read.

## 0.8: calendar sheet, glass controls, Arabic photos

- **Calendar:** tapping a day in the month opens a sheet with that day's events; tap an event to open it, Back returns to the sheet. The grid itself never resizes (that was the lag). It appears at once; the event dots fill in a moment later, after they are worked out off the main thread. Day cells show a number and a few dots only.
- **Glass:** the home cards are real Liquid Glass (blurred, tinted, lit at the edge) over the soft background. Every button, chip and round icon button is drawn in the same glass look, in two sizes. The top-right buttons are the same size on every tab.
- **Press feedback everywhere:** buttons, chips, cards, calendar days, event rows and switches dip with a spring, light up under the finger and give a light haptic tick.
- **Text that wraps well:** headings and buttons break in balanced lines, body text avoids a lone short last line, long accounts are shortened in the middle (keeping the domain) and never wrap letter by letter, and button groups sit in wrapping rows so no button is squeezed. Delete is one consistent control: a trash button that turns into a red "Delete" which must be tapped again.
- **Arabic in photos:** choose the language under "Add a note" (Auto, English / Latin, Arabic). Arabic is read with Tesseract (Arabic and English data ship inside the app, about 5 MB); Auto reads with both and keeps the Arabic result when it finds enough Arabic letters. Arabic text displays right to left. Printed text only; handwriting and low-light photos read poorly, and it takes a few seconds.
- Not done: voice notes are still English only (the speech model is English). Arabic speech needs a model of 150 MB or more.

## 0.9: one-canvas month, day wheel, accounts, backup, 12-hour

- **Calendar:** Day and Month only. Day is the vertical wheel with the chosen day always in the middle and that day's hours beside it (Week is gone; the wheel replaces it). Month is drawn on a single canvas instead of 42 separate cells, which was the main cause of the lag: picking a day only repaints, and the grid never re-lays-out. Tapping a day opens a sheet of its events.
- **Home:** the cards are Liquid Glass with their accent colour kept as a tint, so the background shows through. Text follows the light or dark theme. Morning / Midday / Evening is a glass switch in the top row, left of the two round buttons, and it follows your swipe.
- **Feedback:** every tap now behaves like the red record button: the control lifts on a springy bounce, a light blooms under the finger, and a light haptic tick confirms it.
- **Accounts:** the hub lists accounts, not calendars. Switch an account off and none of its events appear; "Choose calendars" opens the account's calendars one by one. Holiday, birthday and similar calendars start switched off. Until you choose, everything shows, and the hub says so.
- **You and your devices:** set your name (used in the greeting), export a backup (settings, voice notes, written and photo notes, attached emails, calendar links, optionally recordings and photos) as one zip, and import it on another phone. Importing only adds what is missing and never replaces or deletes. The file is not encrypted. Calendar accounts themselves are not in the file: add them on the new phone, then pick the same accounts. Live cloud sync comes later.
- **Times are 12-hour** everywhere ("3:30 PM", "9 AM"). Events saved by earlier versions are converted when loaded.

## 0.9.1: pure black

Dark mode is now true black (#000000) with slightly lifted surfaces, softer colour glows, and a black window and system bars, so an OLED screen switches its pixels off. It is on by default and can be turned off in the hub ("Pure black in dark mode") for the earlier blue-grey dark.

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

## 0.11: prep, series memory, sharing, free times, protected backups

- **Prep (meeting page, Brief tab):** a 30-second plan: goal, what to remember, what to raise. Written by the AI you chose from the invite, guests, agenda, last time's note, open follow-ups and attached emails; or put together by rules when no AI is on. It is kept, editable, and can be written again or reset. Under two hours before your next meeting, Plait writes it quietly if an AI is on, and the reminder uses its goal line.
- **Since last time:** for a recurring meeting (same title, numbers ignored): the decisions from the last note and the follow-ups still open, with a Done button.
- **Share (meeting page):** PDF, Markdown or plain text of a meeting: why you met, agenda, prep, voice-note summaries and transcripts, written and photo notes, email subjects, open follow-ups. Built on the phone and passed to Android's share sheet. Arabic is laid out right to left.
- **Find a time (New meeting):** free gaps in your calendars for the meeting length (weekdays 9 AM to 5 PM, next 7 or 14 days). Tap one to fill the form, or send the options as a message. It sees only the calendars on this phone, not other people's availability.
- **Protected backups (hub):** an optional passphrase encrypts the backup (AES-256-GCM, key from PBKDF2-SHA256, 1 MB sealed chunks that cannot be reordered or cut). There is no recovery: forget the passphrase and the file cannot be opened.
- Note summaries can be written again, and every AI text says which engine wrote it.

## 0.12: no repeat downloads, sync, wide screens, focus time, team notes

- **Models are never downloaded twice.** A model counts as installed as soon as all its files are on the phone at the exact size, even if a download was interrupted at the very end or you copied the files in yourself. Before any download Plait looks for the files first. In the hub, "Already have a model?" can scan a folder you pick (any depth), files you pick, or the drop folder `Android/data/app.plait/files/models` (copy files there over USB). It copies matching files in; your originals are left alone. If Gemini Nano is ready, the hub says so above the downloads.
- **Sync between your phones, no server of ours:** choose a folder that Google Drive, Dropbox, Nextcloud or Syncthing keeps in step. Each phone writes one file there, encrypted on the phone with your passphrase (AES-256), and reads the others'. Notes, written and photo notes, emails, done follow-ups and prep merge by adding and by carrying deletions, so a sync cannot lose a note. Recordings and photos sync only if you turn that on; otherwise the transcript and summary come across and the card says the recording is on another phone. It runs when Plait opens and after you save a note, or with Sync now.
- **Check this phone (hub):** a self-check list (permissions, exact alarms, AI engine, models, widget, storage), a small on-phone log with crash records you can share, and a frame-rate overlay.
- **Tablets and foldables** can rotate and keep a centred column up to 720 dp wide. Phones stay upright.
- **Watches:** the meeting alert and its Join button are marked for Wear OS, so they appear on a paired watch. There is no separate watch app.
- **Desktop view:** "Notes as Markdown for a computer" saves every meeting with notes as a Markdown file in a zip with an index; open it in any Markdown reader.
- **Focus time:** in the evening card, if tomorrow has a free stretch of 90 minutes or more, Plait offers to hold it. Follow-ups can be booked as 30-minute to-dos into the first free gap. Nothing is added to your calendar until you tap.
- **Team notes:** "Send my notes to a teammate" (meeting page, Share) makes a file with your notes for that meeting, marked with your name. Emails and recordings go only if ticked; a passphrase is optional. The teammate imports it from the hub and it attaches to their copy of the meeting (same title, within 10 minutes), or shows as shared notes if they have none.

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

- All text now uses **AR One Sans**, the Google Fonts family closest to the name "One Sans" (there is no Google font with exactly that name). It covers Latin text; Arabic text falls back to the phone's own Arabic font. If you meant a different font (for example OnePlus Sans, which is not freely licensed), tell me and it is a one-file swap.

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

## Where things are

```
app/src/main/java/app/plait/
  ui/glass/        Liquid Glass modifier, press feedback, tab bar, morphing menu
  ui/screens/      Today, Calendar (day/week/month + pane), Sources sheet, Event, Record
  audio/           WavRecorder, VoskTranscriber, RecordingController, RecordingHub, RecordingService, NotePlayer
  data/            Model, CallLinks, Agenda, Calendars, NoteStore
  data/device/     DeviceCalendar (reads Android's calendar store)
  data/brief/      Brief (kind, why, agenda, last time), Text (invite cleaner, summarizer)
  data/ics/        IcsParser, Rrule (recurrence), Feeds (calendars added by link)
  data/google/     GoogleAuth, GoogleCalendarClient, GoogleParser
app/src/main/assets/model-en-us/   Vosk small English model (about 40 MB compressed)
```

## Third-party

- [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 1.0.6 (Apache-2.0): backdrop blur and lens refraction.
- [Vosk](https://alphacephei.com/vosk/) 0.3.75 and `vosk-model-small-en-us-0.15` (Apache-2.0): offline speech recognition.
- Font: AR One Sans (SIL Open Font Licence 1.1, from Google Fonts), used for all text.

## Known limits

- Notes are stored as WAV (about 2 MB per minute) in app storage. A compressed format is a later job.
- Transcription is English only, and less accurate than cloud services. Names and jargon will be wrong often.
- Plait records your own microphone. It does not capture the other side of a call, and Android does not allow that for normal apps.
- Recording a conversation can require consent from the other people in it, depending on where you are.
- Debug build only. No release signing, shrinking, or Play Store setup yet.

GSAP 3.12.5 (gsap.com, standard no-charge licence) animates the home-card skies. The rest of the app's motion (presses, tab bounce, swipes, entrances) stays native Compose springs, since GSAP cannot drive native views.
