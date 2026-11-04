# KIARA AI

Native Android app (Kotlin, Jetpack Compose / Material 3, MVVM, Coroutines + Flow, OkHttp) that uses **your own Google Gemini API key**.

> This project was written without being compiled in the authoring environment. Expect to fix a few small compile errors on the first build; paste the Gradle error and I'll fix it.

## Folder structure

```
KiaraAI/
├─ settings.gradle.kts, build.gradle.kts, gradle.properties
├─ .github/workflows/build.yml        (cloud build, see Option A)
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ res/ (values, xml, drawable, mipmap)
      └─ java/com/kiara/ai/
         ├─ MainActivity.kt, KiaraApp.kt
         ├─ domain/     Models, Persona (system prompt), CommandParser (offline commands),
         │              ActionParser, Calculator
         ├─ data/       SecureStore (Keystore-backed key), SettingsStore, Stores (history/notes/reminders),
         │              GeminiClient (REST)
         ├─ device/     DeviceActions, KiaraAccessibilityService, KiaraNotificationListener, Reminders
         ├─ voice/      VoiceManager (SpeechRecognizer + TextToSpeech)
         ├─ viewmodel/  MainViewModel
         └─ ui/         Theme, Avatar, Home, Chat, Code, Tools, Settings screens
```

## Build

### Option A (recommended): build in the cloud from your phone
Building Compose apps inside Termux is heavy and fragile (see Option B). Easiest path:
1. Create a free GitHub repo, upload this whole `KiaraAI` folder (from Termux: `pkg install git`, `git init`, `git add .`, `git commit`, `git push`).
2. Open the repo's **Actions** tab > **Build APK** > **Run workflow**.
3. Download the `kiara-ai-debug-apk` artifact, unzip, install `app-debug.apk` (allow "install unknown apps").

### Option B: build inside Termux (best effort, not verified)
Quick way: `termux-setup-storage`, unzip the project, `cd KiaraAI && bash build-termux.sh` (it installs packages, checks your SDK, builds, and copies the APK to Downloads). Manual steps:
The official Android SDK build tools are x86 Linux binaries and don't run on Termux (aarch64). You need aarch64 builds and ~3 GB free RAM.
1. `pkg update && pkg install openjdk-17 gradle aapt2 git`
2. Get an aarch64 Android SDK (platform `android-34` + build-tools 34.x). Community builds exist, for example the "android-sdk-tools" / "termux-android-sdk" projects; follow their README. Set `export ANDROID_HOME=<sdk path>`, and create `local.properties` with `sdk.dir=<sdk path>`.
3. In `gradle.properties`, uncomment `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2`.
4. From the project folder: `gradle assembleDebug --no-daemon`
5. APK: `app/build/outputs/apk/debug/app-debug.apk`; install with `termux-open app/build/outputs/apk/debug/app-debug.apk`.

If Gradle runs out of memory, lower `-Xmx` in `gradle.properties` and close other apps. If it keeps failing, use Option A.

### Option C: Android Studio on a PC
Open the folder, let it sync, press Run.

## First run
1. Settings > **Gemini AI**: paste your key (free at aistudio.google.com/apikey), **Load models from my key** (optional), **Test connection**.
2. Grant microphone permission when asked.

## What's implemented (and what isn't)

| Feature | Status |
|---|---|
| Gemini chat with memory, persona, Hindi/Gujarati/English | Implemented (needs your key) |
| Key storage | Encrypted prefs, Android Keystore master key; never in source |
| Model select, test, enable/disable, temperature, reply length | Implemented; model list can be loaded from your key |
| Voice: speech-to-text, text-to-speech, mic start/stop | Implemented (uses phone's recognizer/TTS) |
| Continuous conversation with consent | Implemented (while app is open; pauses on silence) |
| Female voice | Best effort: picks a voice labelled female if the engine has one, else higher pitch. Quality depends on installed TTS voices |
| Hindi/Gujarati pronunciation | Depends on Google TTS voice data installed on the phone |
| **"Hey Kiara" wake word** | **Not implemented.** Needs an always-on hotword engine and a foreground service |
| Open apps, YouTube, YouTube search, WhatsApp, Chrome, Settings, Wi-Fi/Bluetooth settings, torch, volume | Implemented |
| "Play" a song | Opens YouTube search results; you tap the video (YouTube provides no key-free autoplay) |
| Read notifications | Implemented, needs in-app switch + Android notification access; memory only |
| Accessibility actions (home/back/recents, scroll, tap by label, read screen) | Implemented, needs in-app switch + Android accessibility service |
| Coding assistant (generate/explain/fix/debug/project) + highlighted editor, copy, save | Implemented (simple regex highlighting) |
| Translate, summarize, daily plan | Implemented via Gemini |
| Calculator, notes, reminders | Implemented offline; reminders are "in N minutes" and don't survive a reboot |
| Image understanding, text-file assistance | Implemented in Chat (attach buttons) |
| Internet search | Gemini's Google Search grounding toggle. There is no custom backend |
| Sending messages / calling | Not implemented, intentionally |
| Real blur glassmorphism | Translucent panels only |
| Avatar | Stylized vector drawing, animated |

## Notes
- Gemini model names change over time; use **Load models from my key** if the default returns 404.
- Release builds use the debug signing key so they're installable; make your own keystore before publishing.
