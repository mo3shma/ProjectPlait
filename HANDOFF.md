# Plait: handoff for the next session

Read this first, then `CHANGELOG.md` (newest first) for the detail of every change.

## Where things stand

- **Version:** 0.23.0 (versionCode 45), on `main` of `github.com/mo3shma/ProjectPlait`. 295 unit tests pass.
- **Built and pushed, but not yet run on a real phone:** everything from 0.19 onward. The user tests the APKs on their own phone, so expect bug reports with screenshots.
- **Website:** https://mo3shma.github.io/ProjectPlait/ (GitHub Pages from `docs/`, served from `main`). Paths are case-sensitive.
- **Releases:** published by hand by the user: Releases → new tag `v<version>` → attach both APKs → Publish. Asset names must be exactly `plait.apk` (64-bit) and `plait-armv7.apk` (32-bit), because the site's Download button and the in-app updater look for those names. The user asked to forget the release workflow (`.github/workflows/release.yml`). It skips quietly while the signing secrets are missing; leave it alone.

## How the user works (standing preferences)

- Don't ask questions, just work. Keep everything local and free (no paid APIs, no server).
- 12-hour times. Text is white in dark mode and black in light mode.
- Glass effects off by default. Pill and circle shapes. Buttons follow one design language: clear glass with a crisp rim, never "cloudy" or milky. Strong actions (Join, Record, +) are solid colour.
- Material 3 Expressive is a whole-app style switch that needs a restart, and every pop-up and menu must follow the chosen style.
- No AI attribution or disclaimer lines in the app. No dummy data in the app.
- After each round, send `plait.apk` and `plait-armv7.apk` (and the README when it changed) with SendUserFile. Be honest about what hasn't been run on a device.
- Git commits are authored by `3shmaaa <343026483+mohamedashmawymail-dot@users.noreply.github.com>` (set in the repo's git config). **No `Co-Authored-By` lines and no session links** anywhere in commits, files or releases; the user removed them deliberately.
- README and site follow the style of the user's other repo `mo3shma/speedy-dial` (banner, Follow on X @3shmaaa, feature paragraphs, screenshot grid, tables).

## Build and test

```
# local.properties needs sdk.dir (the session used /home/user/android-sdk)
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
# outputs: app/build/outputs/apk/release/app-arm64-v8a-release.apk  -> plait.apk
#          app/build/outputs/apk/release/app-armeabi-v7a-release.apk -> plait-armv7.apk
```

- Maven Central sometimes answers 429. Wait and run again.
- Release builds use R8 (`app/proguard-rules.pro` keeps the native-backed libraries whole) and ABI splits.
- Releases are signed with the debug key whose SHA-1 is `77:EC:81:98:56:2D:3C:F1:81:44:EC:17:45:7B:31:B6:07:2E:52:BC`. A new cloud machine has a different debug key, so a build from it **will not install over the user's app**. The user holds the original key file (`plait-signing.keystore`, store and key password `android`, alias `androiddebugkey`). Ask them to provide it, then set `PLAIT_KEYSTORE`, `PLAIT_STORE_PASSWORD`, `PLAIT_KEY_ALIAS` and `PLAIT_KEY_PASSWORD` (see `app/build.gradle.kts`), or copy it to `~/.android/debug.keystore`.

## Code map (app/src/main/java/app/plait)

| Area | Where |
| --- | --- |
| Calendars (device, Google, `.ics`) | `data/Calendars.kt`, `data/device/`, `data/google/`, `data/ics/` |
| Home cards (Morning, Midday, Evening) | `ui/screens/BriefHome.kt`, `TodayScreen.kt`, `PhaseSky.kt` |
| Meeting page, About, guests, manage | `ui/screens/EventScreen.kt`, `MeetingCards.kt`, `MeetingInfo.kt`, `ManageMeeting.kt` |
| Notes (voice, write, photo, list) | `ui/screens/MemoNotes.kt`, `NotesLibrary.kt`, `data/memo/`, `data/NoteStore.kt` |
| Suggested tasks and reminders | `data/tasks/Tasks.kt`, `reminder/TaskReminders.kt`, `ui/screens/SuggestedTasks.kt` |
| AI facade, harness, models | `ai/Ai.kt`, `ai/AiHarness.kt`, `ai/LocalLlm.kt` (MediaPipe and LiteRT-LM), `ai/ModelCatalog.kt`, `ai/ModelCenter.kt`, `ai/ModelDownloadService.kt` |
| Voice: record, live words, Whisper | `audio/RecordingController.kt`, `audio/VoskTranscriber.kt`, `audio/WhisperTranscriber.kt` (mixed languages, repeat cleanup), `audio/WavSegments.kt`, `audio/NoteProcessor.kt` |
| Transcript paragraphs, note summary | `data/Transcript.kt`, `ai/NoteAi.kt` (`NoteSummary`) |
| Updates, What's new, tour | `data/update/Updater.kt`, `data/update/WhatsNew.kt`, `ui/screens/UpdateUi.kt` |
| Style system | `ui/FauxGlass.kt` (clear glass), `ui/Popup.kt` (pop-up surface, menu buttons), `ui/glass/`, `ui/material/MaterialUi.kt`, `ui/theme/Theme.kt` |
| Pop-ups that grow and shrink | `ExpandDialog` in `ui/screens/NoteActions.kt` |
| Widget | `widget/NextMeetingWidget.kt` and `res/layout/widget_day_*.xml` |

## The AI harness (keep using it)

Anything the AI writes that the app shows as fact goes through `AiHarness`:
1. Clean the input in code.
2. Put the rules in the system message and fence the material in the user message.
3. Use a low temperature (0.2).
4. Ask for a JSON object, with one retry.
5. Check every field in code with `checked` / `paragraph` / `onTopic`.
6. Fall back to rules if nothing passes.

Background writing uses the smallest installed model. Note summaries use the chosen model and read long transcripts in parts.

## Gotchas learned the hard way

- **A local variable named `app`** (for example `val app = context.applicationContext`) hides the `app.plait` package. Fully qualified `app.plait.…` names then fail to compile. Use imports.
- The **AR One Sans** font file was once an HTML page. It is now the real variable font (188 KB). Check `res/font/ar_one_sans.ttf` starts with `\0\1\0\0` if text looks wrong.
- With glass effects off, tinted glass must draw the surface **under** the colour (`liquidGlass` in `ui/glass/Glass.kt`), or Join and Record turn white.
- In Material, card buttons (`ButtonStyle.Ink`/`InkSoft`) take `LocalCardInk`, so they never match the card colour.
- sherpa-onnx Whisper keeps only the first ~29 s of a piece, so pieces are at most 25 s and split at the quietest moment.
- Screenshots on a Linux machine work with Robolectric 4.16 plus Compose (hardware PixelCopy, native graphics). Three quirks:
  - Fonts: swap the system Roboto files in the `nativeruntime-dist-compat` jar for static AR One Sans instances.
  - Liquid Glass: the hue blend doesn't render.
  - Material You colours there aren't real.

  The harness lived in the session scratchpad and is not in the repo.
- The user's GitHub noreply address credits their account. The repo is also reachable as `mo3shma/projectplait` (redirects).

## Open threads and ideas

- **Needs checking on a phone:**
  - R8-shrunk build: voice note, photo text, AI model, Google sign-in.
  - LiteRT-LM models (Qwen3, Gemma 4).
  - The in-app updater end to end (needs a published release with both APKs).
  - Mixed-language transcription on real speech.
  - The background download service.
  - Card grow and shrink animations.
- **Known limits:**
  - Very short phrases in mixed-language notes can get the wrong language.
  - The live words while recording are English-only (Vosk small).
- **Offered but not done:**
  - Real app screenshots in the README from the user's phone.
  - Moving the speech model (Vosk) out of the APK or compressing it with xz (about −5 MB).
  - A proper (non-debug) signing key; switching now would force a reinstall.
