# Changelog

All notable changes to Gemma Voice are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

---

## [0.2.0] — 2026-04-18 — Sprint 2 + 3 (Audio, Inference, Chat UI)

### Added
- **`ChatMessage`** data class (`data/model/ChatMessage.kt`) — role, text, timestamps, token count, tok/s, streaming flag
- **`LiteRtLmEngine`** (`inference/`) — full LiteRT-LM 0.10.2 integration
  - GPU backend with automatic CPU fallback
  - Persistent `Conversation` session (maintains history across turns)
  - `sendMessage()` returns `Flow<String>` for token streaming
  - `resetConversation()` to clear history without reloading model
- **`AudioCaptureManager`** (`audio/`) — AudioRecord at 16 kHz, `ENCODING_PCM_FLOAT`, mono; coroutine-based capture loop writing to `PcmBuffer`
- **`VoiceActivityDetector`** (`audio/`) — energy-threshold VAD emitting `SpeechStart`, `SpeechEnd(pcm)`, `Timeout`; 800 ms silence window, 150 ms min speech, 30 s hard cap
- **`ChatViewModel`** — full implementation
  - `EngineState` sealed class (Idle / Loading / Ready / Error)
  - Auto-initialises engine on entry if model is downloaded
  - `sendTextMessage()` — streaming inference with live tok/s tracking
  - `startVoiceCapture()` / `stopVoiceCapture()` — wires audio pipeline (inference hook stubbed for Sprint 3 audio)
  - `clearConversation()` to reset engine conversation
- **`ChatScreen`** — complete UI from design
  - Gradient app bar ("Gemma Voice" purple→cyan) with live engine status dot
  - Message list: `UserBubble` (gradient fill), `AssistantBubble` (glass effect, streaming cursor, tok/s metadata), `DateDivider`, `ThinkingIndicator` (animated bouncing dots)
  - Voice bar: 32-bar animated waveform, mic button with ripple rings (listening), spinning dots (thinking), stop icon (speaking)
  - Keyboard toggle → text input field with send button (functional text chat)
  - Engine loading overlay (spinner while model initialises)
  - Empty state with pulsing gradient orb
  - `RECORD_AUDIO` permission request on mic tap
- **`litert_lm_android_docs.md`** — LiteRT-LM API reference (Engine, Conversation, multi-modal, tool calling)
- **`VoiceAgent/`** — Claude Design reference files (chat.jsx, android-frame.jsx, HTML preview, home screenshot)

### Changed
- **`ChatViewModel`** — replaced stub with full implementation; `VoiceState` enum kept, `EngineState` sealed class added
- **`NavGraph`** — passes `onBack = { navController.popBackStack() }` to `ChatScreen`
- **`LiteRtLmEngine`** — replaced `TODO("Sprint 3")` stub with real implementation
- **`AudioCaptureManager`** — replaced `TODO("Sprint 2")` stub; added `pcmChunks()` helper
- **`VoiceActivityDetector`** — replaced `TODO("Sprint 2")` stub with energy VAD
- **`AndroidManifest.xml`** — added `libvndksupport.so` and `libOpenCL.so` native library entries for GPU acceleration
- **`CLAUDE.md`** — corrected LiteRT-LM version (was `1.0.0-alpha04`, actual versions are `0.x.x`; using `0.10.2`)

### Build
- **Migrated kapt → KSP** (`com.google.devtools.ksp:2.1.0-1.0.29`) — faster annotation processing, fixes Kotlin metadata compatibility issues
- Kotlin: `2.0.21` → `2.1.0`
- Hilt: `2.51.1` → `2.58` — required to read Kotlin 2.2.x metadata from LiteRT-LM 0.10.2
- LiteRT-LM: added `litertlm-android:0.10.2` (Google Maven, `com.google.ai.edge.litertlm`)

---

## [0.1.0] — 2026-04-17 — Sprint 0 + 1 (Scaffold, Download, Home UI)

### Added
- Project scaffold: Gradle KTS, version catalog (`libs.versions.toml`), Hilt DI, Navigation Compose
- `ModelDownloadWorker` — resumable OkHttp download via `Range` header; SHA-256 verify; `.part` → rename pattern
- `ModelRepository` — WorkManager orchestration, `observeDownloadState()` Flow, `getModelPath()`
- `SettingsRepository` — DataStore preferences (model path, onboarding flag)
- `HomeScreen` + `HomeViewModel` — MicHero glow, ModelCard (Idle / Downloading / Verifying / Complete / Error states), gradient CTA button
- `ChecksumVerifier` — SHA-256 file verification
- Dark theme: `BackgroundDark #080B14`, `BrandPurple #7C3AED`, `BrandCyan #06B6D4`
- `GemmaVoiceApp` — `@HiltAndroidApp`, `HiltWorkerFactory`, notification channels
- Stubs: `LiteRtLmEngine`, `AudioCaptureManager`, `VoiceActivityDetector`, `KokoroEngine`, `TtsSynthesizer`, `AudioTokenizer`, `GemmaVoiceSession`, `ChatScreen`, `ChatViewModel`
