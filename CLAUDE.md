# Gemma Voice — Android App

## Project Overview
A fully local Android voice assistant. User speaks → on-device Gemma 4 processes audio natively → Kokoro TTS speaks the response. No cloud APIs, no server, fully offline after first model download.

## Developer Context
- Developer is new to Android/Kotlin — learning along the way
- Has strong AI/Python background
- Explain Kotlin/Android concepts briefly when introducing new patterns
- Prefer simple, readable code over clever abstractions
- One concept at a time — don't introduce multiple new patterns simultaneously

## Current Phase
**Phase 1 — Sprint 2 (next)**
Full plan is in `PLAN.md`. Build sprint by sprint, do not skip ahead.

## Tech Stack
- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0) | **Target/Compile SDK**: 36
- **Build**: Gradle with Kotlin DSL (`build.gradle.kts`) + version catalog (`gradle/libs.versions.toml`)
- **UI**: Jetpack Compose + Navigation Compose (NOT Fragments — project uses Compose throughout)
- **DI**: Hilt (kapt)
- **Async**: Kotlin Coroutines + Flow
- **LLM Inference**: LiteRT-LM (dependency commented out — add back in Sprint 3)
- **TTS**: Kokoro v1.0 via ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android:1.18.0`)
- **Audio**: Android AudioRecord/AudioTrack (16kHz, 32-bit float PCM)
- **VAD**: Energy + zero-crossing detector
- **Download**: WorkManager + OkHttp (resumable)
- **Storage**: DataStore (preferences)

## Model
- **Gemma 4 E2B** (`litert-community/gemma-4-E2B-it-litert-lm` on HuggingFace)
- File: `gemma-4-E2B-it.litertlm` · INT4 quantized · ~2.58 GB (NOT 4.2 GB)
- Download URL: `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm`
- Stored at: `Context.getExternalFilesDir(null)/models/`
- Manual placement (dev): `adb push` to `/sdcard/Android/data/com.example.gemmaapp/files/models/`

## Package Structure
```
com.example.gemmaapp/
├── GemmaVoiceApp.kt           — @HiltAndroidApp, HiltWorkerFactory, notification channels
├── MainActivity.kt            — @AndroidEntryPoint, hosts NavGraph
├── di/AppModule.kt            — provides WorkManager, OkHttpClient
├── data/
│   ├── model/                 ModelInfo.kt (GEMMA_4_E2B constant), DownloadState.kt
│   ├── repository/            ModelRepository.kt, SettingsRepository.kt
│   └── download/              ModelDownloadWorker.kt (@HiltWorker), ChecksumVerifier.kt
├── inference/                 Stubs — implement in Sprint 3
├── tts/                       Stubs — implement in Sprint 4
├── audio/                     Stubs — implement in Sprint 2
└── ui/
    ├── Screen.kt              — Home, Chat routes only
    ├── NavGraph.kt            — Home → Chat
    ├── home/                  HomeScreen + HomeViewModel (ACTIVE — fully implemented)
    ├── chat/                  ChatScreen stub + ChatViewModel (VoiceState enum)
    ├── download/              DownloadScreen stub (kept but not used in NavGraph)
    └── onboarding/            OnboardingScreen stub (kept but not used in NavGraph)
```

## Navigation Flow
```
App launch → HomeScreen (always)
HomeScreen → ChatScreen (only when model is ready)
```
HomeScreen handles both welcome UX and model download/status in one screen.

## Sprint Status
| Sprint | Status | Notes |
|---|---|---|
| 0 — Scaffold | ✅ Done | Gradle, Hilt, NavGraph, permissions |
| 1 — Download | ✅ Done | WorkManager download, HomeScreen UI, model status |
| 2 — Audio/VAD | ⏳ Next | AudioRecord, PcmBuffer, VoiceActivityDetector |
| 3 — Inference | 🔲 | LiteRtLmEngine, AudioTokenizer, GemmaVoiceSession |
| 4 — TTS | 🔲 | KokoroEngine (ONNX), TtsSynthesizer streaming |
| 5 — Integration | 🔲 | ChatViewModel state machine, ChatScreen, end-to-end |
| 6 — Polish | 🔲 | Error handling, settings, OOM graceful fail |

## Key Implementation Rules
- `androidResources { noCompress += listOf(".litertlm", ".onnx") }` — in app/build.gradle.kts, required
- LiteRT-LM engine takes 5–10s to init — show spinner, init once, keep alive
- Gemma 4 audio prompt: `"<start_of_turn>user\n<audio>\n<end_of_turn>\n<start_of_turn>model\n"`
- Audio spec: 16kHz, `AudioFormat.ENCODING_PCM_FLOAT`, mono
- Model download: write `.litertlm.part` → SHA-256 verify → rename (SHA-256 empty = skip verify)
- TTS streaming: split on sentence boundaries, play first chunk before LLM finishes
- `Context.getExternalFilesDir()` for model — app-private, no extra permissions
- ADB path on device: `/sdcard/Android/data/com.example.gemmaapp/files/models/`
- Use `MSYS_NO_PATHCONV=1` before adb commands in Git Bash to prevent path mangling
- ADB binary: `C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe`

## Theme / UI
- Always dark theme — `GemmaAPPTheme` forces dark, no dynamic color, no light variant
- Brand colors: `BrandPurple (#7C3AED)`, `BrandCyan (#06B6D4)`, background `#080B14`
- All colors defined in `ui/theme/Color.kt` — import from there, never hardcode hex in screens
- Gradient brush: `Brush.horizontalGradient(listOf(BrandPurple, BrandCyan))`
- Material Icons Extended dependency added — use `Icons.Default.*` freely

## Known Issues / TODOs
- LiteRT-LM `litertlm-android:0.10.2` added to build.gradle.kts (latest as of April 2026; versions are `0.x.x`, NOT `1.0.0-alpha04`)
- LiveKit (`livekit-android:2.24.1`) commented out — Phase 2 only
- WorkManager download UI works but in-app download needs real-device testing
- `DownloadScreen.kt` and `OnboardingScreen.kt` stubs kept but not wired into NavGraph

## Performance Targets
- LiteRT engine init: < 10s
- End-to-end latency (speech end → first audio): < 10s on mid-range device
- Peak RAM during inference: < 3 GB (E2B model)
