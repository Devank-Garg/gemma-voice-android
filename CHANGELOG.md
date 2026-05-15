# Changelog

All notable changes to Gemma Voice are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

---

## [0.5.0] — 2026-05-15 — Voice Pipeline Stability + TTS Quality

### Fixed
- **Voice pipeline crash after first reply** — `PcmBuffer.chunks` is a `SharedFlow` that never completes; the VAD coroutine stayed alive after `SpeechEnd`, still subscribed to the buffer. A second mic tap launched a second VAD coroutine, both fired `processVoiceInput()` concurrently, causing two simultaneous `conv.sendMessageAsync()` calls on the same `Conversation` object → crash. Fixed by tracking `vadJob` and cancelling it at the start of every `startVoiceCapture()` call.
- **Silent token loss** — `shareIn(replay = 0)` dropped LLM tokens emitted before the UI collector subscribed. Changed to `replay = 512`.
- **Waveform animating during model response** — `WaveformBars` was `active` during both `LISTENING` and `SPEAKING` states, making the mic waveform animate while the model was talking. Now only active during `LISTENING`.

### Changed
- **`startVoiceCapture()`** — replaced VAD-based end-of-speech detection (was silently failing on this device) with a fixed 2-second capture window. VAD code preserved; swap back by restoring `vad.detect()` inside `vadJob`.
- **`TtsSynthesizer.synthesizeStream()`** — rewrote from `flow {}` to `channelFlow {}` with a dedicated synthesis coroutine on `Dispatchers.IO`. Sentence N+1 is now synthesized while sentence N is playing, eliminating inter-sentence silence gaps.
- **`TtsSynthesizer.synthesizeStream()`** — first audio chunk now fires after 3 words accumulate (regardless of punctuation), reducing perceived startup latency. Subsequent chunks flush at sentence boundaries plus soft boundaries (`,` `;` `:` after ≥ 20 chars).
- **`EnglishPhonemizer`** — replaced 250-word hardcoded dictionary with CMU Pronouncing Dictionary (134k entries, loaded from `assets/cmudict.txt` at Kokoro init time). Fallback chain: CMU dict → small hardcoded dict → rule-based.
- **`EnglishPhonemizer`** — added acronym detection: any all-caps word ≥ 2 letters (AI, GPU, LLM, API, CPU…) is spelled out letter-by-letter using a dedicated `LETTER_NAMES` map with correct English letter-name phonemes, instead of being looked up as a word in cmudict.
- **`KokoroEngine.initialize()`** — calls `EnglishPhonemizer.loadDict(context.assets)` on startup.
- **`ChatViewModel`** — removed unused `VoiceActivityDetector` injection; cleaned dead import.

### Added
- `app/src/main/assets/cmudict.txt` — CMU Pronouncing Dictionary 0.7b (135 166 lines).

### Known Issues
- VAD end-of-speech detection unreliable on test device (energy threshold / silence window mismatch with ambient noise). Using fixed 2-second window as workaround; restore VAD when tuning is done.
- TTS startup latency still noticeable (~Kokoro synthesis time for first 3 words). Latency floor is bounded by model inference speed; further reduction requires overlapping LLM and TTS more aggressively.

---

## [0.4.0] — 2026-04-25 — TTS Integration + Voice Pipeline Fixes

### Added
- **`KokoroEngine`** (`tts/`) — full Kokoro v1.0 ONNX TTS integration
  - Loads `kokoro-v1.0.onnx` and `voices/af_heart.bin` from `getExternalFilesDir/models/`
  - `initialize()` / `synthesize()` run on `Dispatchers.IO` (fixes ANR caused by blocking ONNX load on main thread)
  - Style vector indexed by token length for natural prosody
  - Clamps input to 510 tokens (model max)
- **`EnglishPhonemizer`** (`tts/`) — fully offline text → Kokoro token IDs
  - 38 ARPABET phones mapped to Kokoro token IDs
  - 250+ word pronunciation dictionary
  - Rule-based fallback: trigram → bigram → letter-by-letter
  - Number expansion (cardinal and ordinal)
- **`TtsSynthesizer`** (`tts/`) — streaming LLM → audio pipeline
  - Collects token flow, splits at sentence boundaries (`.` `!` `?` `\n`)
  - Synthesizes each sentence as it arrives; first audio chunk emits before LLM finishes
- **`AudioPlayer`** (`tts/`) — plays PCM float chunks via AudioTrack (24 kHz, `USAGE_ASSISTANT`, stream mode)
- **`LiteRtLmEngine.sendAudio()`** — sends voice input to Gemma 4 E2B using `Content.AudioBytes`
- **`ChatViewModel.processVoiceInput()`** — full voice turn: audio → LLM → TTS → playback, with concurrent UI streaming via `shareIn`
- **10-minute inactivity timer** — auto-closes engine after 10 min idle; reloads on next user message
- **GPU/CPU backend indicator** in chat app bar (cyan for GPU, muted for CPU)
- **Markdown rendering** for assistant messages (`com.mikepenz:multiplatform-markdown-renderer-m3`)
- **Debug audio recorder** ("REC 5s" button in voice bar) — records 5 s of mic audio and saves `debug_audio.wav` to external files dir for ADB inspection; checks `RECORD_AUDIO` permission before starting

### Changed
- **`LiteRtLmEngine`** — added `sendAudio(pcm: FloatArray): Flow<String>`; added `audioBackend = Backend.CPU()` to `EngineConfig` (required for Gemma 4 audio routing)
- **`LiteRtLmEngine.sendAudio()`** — audio is now wrapped in a standard **16-bit PCM WAV** container before being passed to `Content.AudioBytes` (raw float32 bytes were silently rejected by the model)
- **`ChatViewModel`** — wired TTS synthesizer and audio player into voice pipeline; added `processVoiceInput()` with full error handling and logcat logging (`ChatVM` tag)
- **`ChatScreen`** — user message bubble label corrected to "User" (was "Gemma"); added REC 5s debug button with live status text

### Fixed
- **ANR on startup** — `KokoroEngine.initialize()` and `synthesize()` were blocking the main thread; moved to `withContext(Dispatchers.IO)`
- **Compile error** — `return FloatArray(0)` inside `withContext { }` lambda is a non-local return (not allowed); fixed to `return@withContext FloatArray(0)` throughout `KokoroEngine.synthesize()`
- **Debug recorder crash** — `recordDebugAudio()` was calling `AudioRecord` without checking `RECORD_AUDIO` permission first; now checks permission and wraps in try-catch, showing error inline instead of crashing

### Known Issues
- **Voice pipeline crashes after first reply** — tested: tap mic → say "hello" → Gemma replies correctly (text + audio). App then crashes on the second voice turn. Root cause not yet identified; likely an exception in `shareIn` / `SharingStarted.Eagerly` flow after the first turn completes. Under investigation.
- **Kokoro TTS requires manual model placement** — `kokoro-v1.0.onnx` and `voices/af_heart.bin` must be pushed via ADB to `/sdcard/Android/data/com.example.gemmaapp/files/models/`; no in-app download yet

---

## [0.3.0] — 2026-04-19 — Model Locate + App Icon

### Added
- **"Locate Model" file picker** (`HomeScreen`) — replaces Download button; opens system file browser so the user can point to an existing `.litertlm` file on the device
- **`HomeViewModel.onModelLocated()`** — resolves a content URI to a real file path (handles primary external storage and generic `DATA` column fallback); shows an inline ADB hint if resolution fails
- **`ModelRepository.saveModelPath()`** — persists a user-selected model path to DataStore

### Changed
- **`ModelRepository.getModelPath()`** — now `suspend`; checks DataStore-saved custom path first, then the default ADB location (`getExternalFilesDir/models/`)
- **`ModelRepository.observeDownloadState()`** — uses `flatMapLatest` on `settingsRepository.modelPath`; immediately emits `Complete` when a valid custom path is set
- **`HomeViewModel`** — removed `startDownload()`; added `locateError: StateFlow<String?>` for path-resolution failures
- **`HomeScreen`** — model card now shows "Locate" button (folder icon) instead of "Download"; error message displayed inline if path resolution fails
- **`DownloadScreen` / `DownloadViewModel`** — removed broken `startDownload()` references (screen is unused stub)
- **`AndroidManifest.xml`** — added `FOREGROUND_SERVICE_DATA_SYNC` permission and `SystemForegroundService` foreground service type declaration (`dataSync`) required for Android 14+
- **App icon + name** — updated launcher icons (all densities) and `strings.xml` app name

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
