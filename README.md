# Saathi — Hindi/Hinglish AI Personal Assistant for Android

Saathi is a voice-first Android assistant: speak or type in Hindi/Hinglish, an
AI backend turns that into a structured action plan, and the Android app
executes it (calls, SMS, WhatsApp, apps, calendar, alarms, reminders, web
research) — always asking for confirmation before anything sensitive.

```
Voice/Text
  → Android SpeechRecognizer (hi-IN)
  → POST /api/chat on the Saathi backend
  → AI (Anthropic Claude) → structured JSON action plan
  → ActionMapper (validates) → confirmation dialog if sensitive
  → ActionExecutor (Call/SMS/WhatsApp/Calendar/Alarm/Reminder/App/URL)
  → TextToSpeech reads the result back
```

## Why there's a backend at all

**The Anthropic (Claude) API key never ships inside the APK.** It lives only in
the backend process's environment variables. The Android app only ever talks
to the Saathi backend, authenticated with a separate, rotatable
`x-saathi-key` header — not the LLM key.

## Repository layout

```
app/        Android app module (Kotlin, ViewBinding, min SDK 26)
backend/    Node.js/Express AI backend (holds ANTHROPIC_API_KEY, TAVILY_API_KEY)
.github/workflows/build.yml   CI: builds the debug APK + runs both test suites
```

## Capability checklist

| # | Capability | Where |
|---|---|---|
| 1 | Hindi/Hinglish voice input | `voice/SpeechInputManager.kt` (hi-IN + en-IN) |
| 2 | Speech-to-text | Android `SpeechRecognizer` |
| 3 | AI NLU | backend `/api/chat` (Anthropic Claude) |
| 4 | Intent classification + structured action planning | `backend/src/llm/promptSchema.js` → JSON action schema |
| 5 | Text-to-speech | `voice/TtsManager.kt` |
| 6 | Contact lookup | `contacts/ContactLookup.kt` |
| 7 | Call workflow | `actions/ActionExecutor.kt` → `ACTION_CALL` |
| 8 | SMS workflow | `ActionExecutor` → `SmsManager` |
| 9 | WhatsApp message workflow | `ActionExecutor` → `wa.me` deep link |
| 10 | Optional WhatsApp auto-send (user-enabled) | `accessibility/VoiceAccessibilityService.kt`, scoped to `com.whatsapp` only |
| 11 | Open installed apps | `ActionExecutor` → `PackageManager` |
| 12 | Open URLs | `ActionExecutor` → `ACTION_VIEW` |
| 13 | Reminders | `reminders/ReminderScheduler.kt` + `AlarmManager`, survives reboot via `BootReceiver` |
| 14 | Calendar events | `calendarutil/CalendarHelper.kt` → `CalendarContract` |
| 15 | Alarm creation | `ActionExecutor` → `AlarmClock.ACTION_SET_ALARM` |
| 16 | Notification handling | `notifications/SaathiNotificationListenerService.kt` |
| 17 | AI web research | backend `/api/research` (Tavily + summarization) |
| 18 | Hindi/Hinglish conversation | backend system prompt |
| 19 | Context-aware multi-step commands | backend `session/sessionStore.js`, keyed by per-install `session_id` |
| 20 | Safe confirmation for sensitive actions | `ActionMapper.ALWAYS_CONFIRM` + `MainActivity` confirmation dialog |
| 21 | Backend architecture, no secrets in APK | see above |
| 22 | Proper Android project structure | `app/src/main/...` |
| 23 | Unit tests | `app/src/test/...` (ActionMapper, LocalIntentParser), `backend/test/...` |
| 24 | GitHub Actions build | `.github/workflows/build.yml` |
| 25 | README | this file |
| 26 | Debug APK build verification | CI artifact `saathi-debug-apk`, see below |

## Running the backend

```bash
cd backend
cp .env.example .env
# edit .env: set ANTHROPIC_API_KEY, SAATHI_BACKEND_API_KEY (any long random string),
# optionally TAVILY_API_KEY for web research
npm install
npm start        # listens on PORT (default 8080)
npm test         # runs backend/test/*.test.js
```

Verify it's alive:

```bash
curl http://localhost:8080/healthz
# {"status":"ok"}
```

Deploy `backend/` anywhere that can run Node 18+ (Render, Railway, Fly.io, a
VPS behind HTTPS, etc.) and keep `.env` server-side only — it is already
git-ignored.

## Building the Android app

You need Android Studio (or just the Android SDK + JDK 17) with:

- `compileSdk 34`, `minSdk 26`
- Android Gradle Plugin 8.2.2, Kotlin 1.9.22

Before building, point the app at your deployed backend. In
`<project root>/local.properties` (not committed) or via `-P` flags, set:

```
SAATHI_BACKEND_BASE_URL=https://your-backend.example.com/
SAATHI_BACKEND_API_KEY=<same value as backend's SAATHI_BACKEND_API_KEY>
```

(These feed `buildConfigField` in `app/build.gradle` — they are an
app↔backend access token, not the LLM key, but keep them out of source
control the same way; `local.properties` is already git-ignored by the
Android Gradle Plugin's default `.gitignore` convention.)

Then:

```bash
./gradlew testDebugUnitTest   # unit tests, no emulator needed
./gradlew assembleDebug       # -> app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected device/emulator:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### First run on a device

1. Install the APK, open **Saathi**.
2. Grant Microphone permission when prompted (mic button).
3. Grant Contacts permission when you first ask it to call/message someone.
4. For **WhatsApp auto-send** (optional): Settings → Accessibility → Saathi →
   enable. Without this, Saathi still opens WhatsApp with the message typed
   in — you just tap Send yourself, which is the default and safer path.
5. For **notification reading**: Settings → Apps → Special access →
   Notification access → Saathi → enable.

## CI: how the APK build is actually verified

`.github/workflows/build.yml` has two jobs:

- **android** — sets up JDK 17 + Android SDK (`android-actions/setup-android`),
  installs `platforms;android-34` and `build-tools;34.0.0`, runs
  `gradle testDebugUnitTest` then `gradle assembleDebug`, and uploads
  `app-debug.apk` plus the unit test report as workflow artifacts.
- **backend** — installs backend deps and runs `npm test`.

This repo has no local Android SDK available in its dev environment, so the
GitHub Actions run — not a local build — is the real, reproducible proof the
project compiles and its tests pass. Check the **Actions** tab of this repo
for the latest run status and to download `saathi-debug-apk`.

## Security notes

- LLM provider key: backend env only, never in git, never in the APK.
- App↔backend key: separate secret, rotatable independently, still kept out
  of source control.
- Sensitive actions (call, SMS, WhatsApp send, alarms, calendar, reminders)
  always require an explicit on-device confirmation, even if the backend
  forgets to ask for one (`ActionMapper.ALWAYS_CONFIRM` enforces this
  regardless of what the LLM returns).
- WhatsApp auto-send is off by default and requires the user to separately
  enable Accessibility access; the service is scoped to `com.whatsapp` only
  and cannot read/act on any other app.
- The app never claims an action succeeded unless the underlying Android API
  call actually returned success (`ExecutionResult` in `ActionExecutor.kt`).
