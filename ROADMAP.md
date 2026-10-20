# Plait: competition and roadmap

Sources: a quick web scan (Oct 2026) plus what I know of these products. Competitor details change often and some review sites are affiliate pages, so check the specifics you plan to rely on.

## Who we are up against

| Group | Examples | What they do well |
|---|---|---|
| Calendar apps | Google Calendar, Outlook, Fantastical, Notion Calendar, Morgen, Business Calendar | Many accounts in one view, fast scheduling, natural-language entry, scheduling links, time-blocking |
| AI planners | Motion, Reclaim, Clockwise, Sunsama, Akiflow | Auto-schedule tasks around meetings, protect focus time, team analytics |
| Meeting-note tools | Otter, Fireflies, Fathom, Granola, Read, Notta | Record and transcribe calls, summaries, action items, CRM sync. Most upload audio to their cloud; bot-based ones join the call visibly |
| Phone built-ins | Google Recorder, Samsung voice and notes AI, Apple Voice Memos | Good local transcription, tied to the phone, no link to your calendar |

## Feature comparison

Legend: yes / partial / no. "Plait now" is what is in the 0.9.1 build, not yet tested on a phone.

| Feature | Plait now | Calendar apps | AI planners | Meeting-note tools |
|---|---|---|---|---|
| Merge Google, Outlook, iCloud, ICS in one view | yes (no Google Cloud setup needed) | yes | partial | no |
| Choose which accounts and calendars show | yes | yes | partial | no |
| Works with no sign-in to any Plait service | yes | no | no | no |
| Everything stays on the phone | yes | no | no | no |
| Daily brief that follows the time of day | yes | no | partial | no |
| "Why you're meeting" from the invite, guests, RSVP | yes | partial | no | partial |
| Attach emails to a meeting and see how they relate | yes (share or paste, no mailbox access) | no | no | no |
| Voice notes with offline transcript | yes (English) | no | no | yes, in the cloud |
| Written and photo notes, text read from the photo (incl. Arabic) | yes | no | no | no |
| AI summary and follow-ups | yes, on supported phones (Gemini Nano) | partial (cloud) | partial (cloud) | yes (cloud) |
| Create events that sync to the account | yes | yes | yes | no |
| Natural-language event entry | no | yes | yes | no |
| Scheduling links / find a time with others | no | yes | yes | no |
| Auto-schedule tasks and protect focus time | no | no | yes | no |
| Recurring meeting memory ("last time") | yes (from your own notes) | no | no | partial |
| Time-in-meetings analytics | yes (daily) | no | yes | partial |
| Join calls from the card (Meet, Teams, Zoom, Webex) | yes | yes | partial | yes |
| Record the call itself | partial (mic only) | no | no | yes |
| Home screen widget, notifications before meetings | no | yes | yes | partial |
| Wear OS / watch | no | yes | partial | no |
| Cross-device sync | export and import for now | yes | yes | yes |
| Team features, shared notes | no | partial | yes | yes |
| Pure-black OLED theme | yes | partial | no | no |

## What is better on our side

1. Privacy by construction: no account, no server, nothing uploaded. Every competitor in the note-taking group needs the cloud.
2. One brief for the day that connects calendar, emails and notes, and changes with the time of day.
3. Notes in any form (voice, text, photo) tied to the meeting, with the photo read on the phone, including Arabic.
4. No Google Cloud project to connect Google; Android's own accounts do the work, and writes go back to the same account.
5. Free to run: no per-seat AI cost, because the AI is on the phone.

## What we lack, and what is weaker than it looks

- Gemini Nano is only on some phones, and the strongest model needs a flagship with plenty of RAM. On other phones we fall back to rules, which are good at structure but not at prose.
- Voice transcription is English only and the bundled model is small. Names and jargon will be wrong.
- No widget or pre-meeting notification, which most people expect from a calendar.
- No natural-language entry, no "find a time", no tasks.
- No live sync between devices.
- Never run on a real device yet in this project: everything above needs a device pass.

## Suggested phases

Principle: each phase must work with no server, keep data on the phone, and use Gemini Nano where present, with a rules-based path where it is not.

### Phase 0: prove it on devices (tools built in 0.12; the testing itself still needs real phones)
- Run on three phones: one Pixel with Gemini Nano, one mid-range Android without it, one Samsung.
- Measure frame rate on Home, Calendar, Hub. Fix what drops.
- Check: recording with screen off, Vosk model unpack time, share-sheet intake, calendar write and sync to Google and Outlook, OCR in Arabic and English, import and export on a second phone.
- Add crash and error logs kept on the phone, with an "export logs" button.

### Phase 1: the daily loop (built in 0.10, needs a device pass)
- Widget: next meeting, countdown, join button. Notification 10 minutes before with the one-line context.
- Quick add: type "lunch with Sam Friday 1pm" and Gemini Nano (or rules) fills the form.
- Evening nudge to add notes, using the reminder card already built.
- Action items from voice and photo notes collected into a simple per-day list with done / snooze.
- Search across meetings, notes, photos and emails, all local.

### Phase 2: smarter on-device AI (built in 0.10 and 0.11; Google's on-device speech API skipped because Whisper covers it)
- Pre-meeting brief written by Gemini Nano from invite, past notes and attached emails, shown 10 minutes before.
- "Since last time" for recurring meetings, from your own notes.
- Ask your day: a text box that answers "what did we decide with Northwind?" from local notes only.
- Speech: use Google's on-device speech recognition API where the phone supports it, keep Vosk elsewhere; optional downloadable models for Arabic and other languages.
- Clear labelling of what the AI wrote and quick edit and regenerate.

### Phase 3: meetings with other people (built in 0.11, except live availability of other people, which needs a server)
- Find a time: read free and busy from your own calendars, propose slots, send as an invite through the account.
- Share a brief or notes as a file or link-free export (PDF, Markdown) from the phone.
- Optional end-to-end encrypted backup file for the export and import flow.

### Phase 4: sync and reach (built in 0.12, without a Plait server: encrypted sync through a folder your own cloud app keeps in step; watch alerts mirror to Wear OS; Markdown export serves as the desktop view)
- Cloud sync, end-to-end encrypted, optional. The server only stores sealed blobs; the phone holds the keys. Built on the backup format already in place.
- Wear OS glance, tablet and foldable layouts.
- Optional desktop viewer for notes (read only) via the same encrypted store.

### Phase 5: later (built in 0.12: focus-time suggestions, booking follow-ups, and explicit team sharing by file)
- Focus time suggestions and light auto-scheduling for tasks, proposed on the phone and applied only when approved.
- Team notes with explicit sharing.

## Risks to decide on early
- Gemini Nano access depends on the phone and on Google's beta APIs; keep every AI feature behind a rules fallback.
- Recording calls from other apps is restricted by Android; only the microphone path is reliable.
- Local-only means lost phone is lost data unless backups are made; remind people to export, and make encrypted backups the first part of Phase 3.
