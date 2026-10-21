# Handoff

Read this first, then `CHANGELOG.md` (newest first) for the detail of each version.

## 1. Goal

Plait is a free Android calendar app. It merges every calendar account into one day, briefs the user on each meeting, lets them manage meetings, keeps voice, written, photo and checklist notes, and runs its AI on the phone. Nothing is sent to a server.

The user (GitHub `mo3shma`, X `@3shmaaa`) builds it with Claude and tests each APK on their own phone. Current aims:
- Make the app feel polished and consistent in two styles: Liquid Glass and Material 3 Expressive.
- Keep the AI honest, short and private, with strict rules checked in code.
- Keep the download small and let the app update itself.
- Give good transcripts for the user's real speech, which mixes Arabic and English.

Standing preferences:
- Don't ask questions, just work. Everything stays local and free.
- 12-hour times. Text is white in dark mode and black in light mode.
- Glass effects are off by default. Pill and circle shapes.
- One button design: clear glass with a crisp rim, never milky or "cloudy". Strong actions (Join, Record, +) are solid colour.
- Every pop-up and menu follows the chosen style.
- No AI attribution or disclaimer lines in the app. No dummy data in the app.
- Commits are authored `3shmaaa <343026483+mohamedashmawymail-dot@users.noreply.github.com>`. **No `Co-Authored-By` lines and no session links** anywhere (commits, files, releases). The user removed them on purpose and repeated it.
- After each round, send `plait.apk` and `plait-armv7.apk` (and the README when it changed) with SendUserFile. Say plainly what hasn't been run on a device.
- README and site follow the style of the user's other repo `mo3shma/speedy-dial`.

## 2. Current state

- **Version 0.23.0** (versionCode 45), on `main` of `github.com/mo3shma/ProjectPlait`. 295 unit tests pass. Last commit before this file: `e71f187`.
- **Not run on a real phone yet:** everything from 0.19 on. The user tests and reports bugs with screenshots.
- **Site:** https://mo3shma.github.io/ProjectPlait/ (GitHub Pages from `docs/`). Paths are case-sensitive.
- **Releases are made by hand by the user:** Releases → new tag `v<version>` → attach both APKs → Publish. Asset names must be exactly `plait.apk` (64-bit, about 96 MB) and `plait-armv7.apk` (32-bit, about 82 MB). The site's Download button and the in-app updater look for those names. The GitHub Actions workflow `release.yml` exists but the user said to forget it; it skips quietly while its secrets are missing.
- **Build:**
  ```
  # local.properties needs sdk.dir (this session used /home/user/android-sdk)
  ./gradlew :app:testDebugUnitTest
  ./gradlew :app:assembleRelease
  # app/build/outputs/apk/release/app-arm64-v8a-release.apk   -> plait.apk
  # app/build/outputs/apk/release/app-armeabi-v7a-release.apk -> plait-armv7.apk
  ```
  Maven Central sometimes answers 429; wait and run again. Release builds use R8 (`app/proguard-rules.pro`) and ABI splits.
- **Signing key:** releases are signed with the debug key whose SHA-1 is `77:EC:81:98:56:2D:3C:F1:81:44:EC:17:45:7B:31:B6:07:2E:52:BC`. A new cloud machine has a different debug key, so a build from it **will not install over the user's app**. The user holds the original `plait-signing.keystore` (store and key password `android`, alias `androiddebugkey`). Ask for it, then set `PLAIT_KEYSTORE`, `PLAIT_STORE_PASSWORD`, `PLAIT_KEY_ALIAS`, `PLAIT_KEY_PASSWORD` (see `app/build.gradle.kts`) or copy it to `~/.android/debug.keystore`.

## 3. Active files

| Area | Where (under `app/src/main/java/app/plait/`) |
| --- | --- |
| Voice: recording, live words, Whisper | `audio/RecordingController.kt`, `audio/VoskTranscriber.kt`, `audio/WhisperTranscriber.kt` (mixed languages, repeat cleanup), `audio/WavSegments.kt`, `audio/NoteProcessor.kt` |
| Transcript paragraphs, note summary | `data/Transcript.kt`, `ai/NoteAi.kt` (`NoteSummary`) |
| Language picker, Whisper card | `ui/screens/SpeechUpgrade.kt`, `ai/AiPrefs.kt` |
| AI facade, harness, models | `ai/Ai.kt`, `ai/AiHarness.kt`, `ai/LocalLlm.kt` (MediaPipe and LiteRT-LM), `ai/ModelCatalog.kt`, `ai/ModelCenter.kt`, `ai/ModelDownloadService.kt` |
| Updates, What's new, tour | `data/update/Updater.kt`, `data/update/WhatsNew.kt`, `ui/screens/UpdateUi.kt` |
| Suggested tasks and reminders | `data/tasks/Tasks.kt`, `reminder/TaskReminders.kt`, `ui/screens/SuggestedTasks.kt` |
| Home cards | `ui/screens/BriefHome.kt`, `TodayScreen.kt`, `PhaseSky.kt` |
| Meeting page, About, manage | `ui/screens/EventScreen.kt`, `MeetingCards.kt`, `MeetingInfo.kt`, `ManageMeeting.kt` |
| Notes | `ui/screens/MemoNotes.kt`, `NotesLibrary.kt`, `data/memo/`, `data/NoteStore.kt` |
| Style system | `ui/FauxGlass.kt`, `ui/Popup.kt`, `ui/glass/`, `ui/material/MaterialUi.kt`, `ui/theme/Theme.kt`, `ExpandDialog` in `ui/screens/NoteActions.kt` |
| Calendars | `data/Calendars.kt`, `data/device/`, `data/google/`, `data/ics/` |
| Widget | `widget/NextMeetingWidget.kt`, `res/layout/widget_day_*.xml` |
| Release, site, docs | `.github/workflows/release.yml`, `docs/` (site), `README.md`, `CHANGELOG.md` |

**The AI harness (use it for anything the AI writes as fact):** clean the input in code; rules in the system message with the material fenced in the user message; temperature 0.2; JSON out with one retry; check every field with `AiHarness.checked` / `paragraph` / `onTopic`; fall back to rules. Background writing uses the smallest installed model; note summaries use the chosen model and read long transcripts in parts.

## 4. Changes made (newest first; details in CHANGELOG.md)

- **0.23.0** Mixed-language notes: pairs such as Arabic + English. The note is cut at pauses; each piece is written in its own language and redone in the main one if Whisper guesses outside the pair. Repetition loops ("ha-ha-ha", "بيلا بيلا…") are collapsed.
- **0.22.1** Transcripts show as paragraphs, not one card per pause. Language picker on the record screen and on each voice note. Summaries read the whole transcript, in parts when it is long.
- **0.22.0** One-tap Whisper card on rough transcripts. Old notes are re-transcribed when a speech model arrives. Whisper Turbo added. Long speech is cut at the quietest moment.
- **0.21.1** Home cards grow open and shrink closed. The open card matches the card's colours. Card buttons are visible in Material.
- **0.21.0** Qwen3 0.6B / 1.7B and Gemma 4 E2B through LiteRT-LM. The best installed model is picked by quality, not size.
- **0.20.0** In-app updates. ABI-split APKs plus R8 (130 MB down to 85 MB). Onboarding tour and a What's new card. Clear-glass buttons.
- **0.19.0** One-paragraph About this meeting. Suggested tasks with reminders. Model downloads keep going in the background. Pop-ups and menus follow the chosen style.
- **0.18.5** Coloured buttons with glass off. The real AR One Sans font. README screenshots. Release workflow added.
- **Earlier** GitHub repo, Pages site and trailer; README in the Speedy Dial style; commits rewritten to remove the session link and co-author lines.

## 5. Failed attempts and traps

- **A local variable named `app`** (for example `val app = context.applicationContext`) hides the `app.plait` package, so fully qualified `app.plait.…` names fail to compile. This bit three times. Use imports.
- **The AR One Sans file was once an HTML page** (a failed download saved under the font's name), so the app silently used the default font. Now it is the real 188 KB variable font. Check `res/font/ar_one_sans.ttf` starts with `\0\1\0\0` if text looks wrong.
- **Glass off, tinted buttons:** the plain surface was drawn over the colour, turning Join, Record and + white or black. The surface now goes under (`liquidGlass` in `ui/glass/Glass.kt`).
- **Material card buttons** used the secondary tonal colour, the same as the Evening card. They now use `LocalCardInk`.
- **Forcing one language on a mixed note, or "auto" on the whole note:** one language got translated or spelled in the other's letters, and Whisper fell into a repeat loop that lost the rest. Fixed by cutting at pauses, per-piece detection and repeat cleanup. Whisper also keeps only about the first 29 s of a piece, so pieces stay at 25 s or less.
- **Capturing pop-ups in screenshots** isn't possible (they draw in their own window). I rendered their content directly instead.
- **Creating releases, tags and repository secrets from the cloud session is blocked by GitHub** (403). That is why the user publishes releases by hand.
- **Moving or recreating a release tag** from the session also failed. Only the user could delete and re-create it.
- **Screenshot tooling** (not in the repo; it lived in the session scratchpad) used Robolectric 4.16 with Compose, hardware PixelCopy and native graphics. Quirks: the Roboto fonts inside the `nativeruntime-dist-compat` jar had to be replaced with static AR One Sans files; the hue blend of tinted Liquid Glass doesn't render; Material You colours aren't real.
- **Offline test voices** (espeak) are robotic, so the mixed-language test on this machine proves the method, not real-accent accuracy.

## 6. Specific next steps

1. **Ask the user to try 0.23.0** and report: whether mixed Arabic + English notes read correctly; whether summaries now cover the end of long notes; whether pauses no longer split the transcript; and whether the language button is easy to find.
2. **Check on a phone** (nothing below has been run on a device): the R8-shrunk build (voice note with live words, photo text, an AI model, Google sign-in if used); the Qwen3 and Gemma 4 models; the in-app updater end to end (needs a published release with both APKs); the background model download; the card open and close animation in both styles.
3. **Get the signing key** (`plait-signing.keystore`) from the user before building any APK they should install over their current app.
4. **If a fix is needed:** bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a `## <version>: …` section at the top of `CHANGELOG.md` (the What's new card is built from it), run the tests, build both APKs, commit with no co-author line, push, then send both APKs.
5. **Ideas offered but not done:**
   - Real phone screenshots in the README, in the same 2-column grid.
   - Compress the Vosk model (about 5 MB smaller).
   - Test and tune very short phrases in mixed notes, which can get the wrong language.
   - A proper release signing key (switching forces a reinstall, so only with the user's agreement).
