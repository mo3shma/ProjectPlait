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

Legend: yes / partial / no. "Plait now" is what is in the 0.24.0 build. Most of it is not yet tested on a phone.

| Feature | Plait now | Calendar apps | AI planners | Meeting-note tools |
|---|---|---|---|---|
| Merge Google, Outlook, iCloud, ICS in one view | yes (no Google Cloud setup needed) | yes | partial | no |
| Choose which accounts and calendars show | yes | yes | partial | no |
| Works with no sign-in to any Plait service | yes | no | no | no |
| Everything stays on the phone | yes | no | no | no |
| Daily brief that follows the time of day | yes | no | partial | no |
| "Why you're meeting" from the invite, guests, RSVP | yes | partial | no | partial |
| Attach emails to a meeting and see how they relate | yes (share or paste, no mailbox access) | no | no | no |
| Voice notes with offline transcript | yes (Whisper, Arabic and English, mixed too) | no | no | yes, in the cloud |
| Written and photo notes, text read from the photo (incl. Arabic) | yes | no | no | no |
| AI summary and follow-ups | yes, on supported phones (Gemini Nano) | partial (cloud) | partial (cloud) | yes (cloud) |
| Create events that sync to the account | yes | yes | yes | no |
| Natural-language event entry | yes (quick add) | yes | yes | no |
| Scheduling links / find a time with others | partial (find a time from your own calendars) | yes | yes | no |
| Auto-schedule tasks and protect focus time | no | no | yes | no |
| Recurring meeting memory ("last time") | yes (from your own notes) | no | no | partial |
| Time-in-meetings analytics | yes (daily) | no | yes | partial |
| Join calls from the card (Meet, Teams, Zoom, Webex) | yes | yes | partial | yes |
| Record the call itself | no | no | no | yes |
| Home screen widget, notifications before meetings | yes | yes | yes | partial |
| Wear OS / watch | partial (alerts mirror to the watch) | yes | partial | no |
| Cross-device sync | yes, encrypted, through a folder you choose | yes | yes | yes |
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
- Voice transcription needs a Whisper model (about 100 MB for Tiny) downloaded from Settings → AI. Without one, a recording is saved as audio only, and the small models mishear names and jargon.
- Live words appear a phrase behind, after each pause. Speed and battery on mid-range phones are not measured yet.
- Sync goes through a folder the user's own cloud app keeps in step, not a live service.
- Little of this has run on a real device yet: it all needs a device pass.

## Suggested phases

Principle: each phase must work with no server, keep data on the phone, and use Gemini Nano where present, with a rules-based path where it is not.

### Phase 0: prove it on devices (tools built in 0.12; the testing itself still needs real phones)
- Run on three phones: one Pixel with Gemini Nano, one mid-range Android without it, one Samsung.
- Measure frame rate on Home, Calendar, Hub. Fix what drops.
- Check: recording with screen off, Whisper load time and live-word delay, share-sheet intake, calendar write and sync to Google and Outlook, OCR in Arabic and English, import and export on a second phone.
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
- Speech: Whisper through sherpa-onnx for live words and the final transcript, with downloadable models for Arabic and other languages (done in 0.24).
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
