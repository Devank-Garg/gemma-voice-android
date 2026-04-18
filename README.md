# Gemma Voice — Local Android AI Assistant

A fully offline Android voice assistant powered by Google's Gemma 4 model. Speak a question, Gemma answers on-device, Kokoro TTS speaks the response. No cloud APIs, no data leaves the device.

---

## Architecture

```
Microphone
  ↓
AudioRecord (16kHz, 32-bit float PCM)
  ↓
VoiceActivityDetector (energy + zero-crossing VAD)
  ↓  speech end detected (≤30s clip)
Gemma 4 E2B — LiteRT-LM (on-device, native audio input)
  ↓  streaming text tokens
Kokoro TTS v1.0 — ONNX Runtime (on-device, ~82 MB)
  ↓  PCM chunks
AudioTrack playback
```

**Why this stack:**
- Gemma 4 E2B has **native audio input** — no separate STT (Whisper) needed
- LiteRT-LM is Google's official, actively maintained inference framework
- Kokoro TTS supports sentence-streaming — first audio plays before LLM finishes

---

## Tech Stack

| Layer | Library |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Navigation Compose |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| LLM Inference | LiteRT-LM `0.10.2` |
| TTS | Kokoro v1.0 via ONNX Runtime `1.18.0` |
| Audio | Android AudioRecord / AudioTrack |
| VAD | Energy + zero-crossing detector |
| Download | WorkManager + OkHttp (resumable) |
| Storage | DataStore Preferences |

**Min SDK:** 26 (Android 8.0) · **Target SDK:** 36

---

## Model

**Gemma 4 E2B** (`litert-community/gemma-4-E2B-it-litert-lm` on HuggingFace)
- Format: `.litertlm` INT4 quantized · ~2.58 GB
- Native audio input: 16kHz, 32-bit float, max 30s
- Download once on first launch; stored in app-private external storage

### Manual model placement (during development)

```bash
# Create directory on device
adb shell mkdir -p /sdcard/Android/data/com.example.gemmaapp/files/models

# Push model file
adb push gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.example.gemmaapp/files/models/gemma-4-E2B-it.litertlm
```

---

## Sprint Status

| Sprint | Status | Scope |
|---|---|---|
| 0 — Scaffold | ✅ Complete | Gradle, Hilt (KSP), NavGraph, permissions, folder structure |
| 1 — Download | ✅ Complete | ModelDownloadWorker (resumable), SettingsRepository, Home UI |
| 2 — Audio/VAD | ✅ Complete | AudioCaptureManager (16kHz PCM float), PcmBuffer, VoiceActivityDetector (energy VAD) |
| 3 — Inference (text) | ✅ Complete | LiteRtLmEngine (GPU→CPU), text chat via LiteRT-LM 0.10.2, streaming Flow |
| 3 — Inference (audio) | 🔲 Pending | AudioTokenizer, GemmaVoiceSession (connects VAD clip → engine) |
| 4 — TTS | 🔲 Pending | KokoroEngine (ONNX), TtsSynthesizer streaming |
| 5 — Chat UI | ✅ Complete | ChatScreen (full design), ChatViewModel state machine |
| 6 — Polish | 🔲 Pending | Error handling, settings screen, OOM graceful fail |

---

## Project Structure

```
com.example.gemmaapp/
├── GemmaVoiceApp.kt           — @HiltAndroidApp, WorkManager config, notification channels
├── MainActivity.kt            — @AndroidEntryPoint, NavGraph host
├── di/
│   └── AppModule.kt           — WorkManager, OkHttpClient providers
├── data/
│   ├── model/                 ModelInfo.kt, DownloadState.kt, ChatMessage.kt
│   ├── repository/            ModelRepository.kt, SettingsRepository.kt
│   └── download/              ModelDownloadWorker.kt, ChecksumVerifier.kt
├── inference/                 LiteRtLmEngine.kt ✅, AudioTokenizer (stub), GemmaVoiceSession (stub), ModelSelector
├── tts/                       KokoroEngine (stub), TtsSynthesizer (stub)
├── audio/                     AudioCaptureManager.kt ✅, VoiceActivityDetector.kt ✅, PcmBuffer.kt, VadEvent.kt
└── ui/
    ├── Screen.kt + NavGraph.kt
    ├── home/                  HomeScreen + HomeViewModel (welcome + model status)
    ├── chat/                  ChatScreen ✅ + ChatViewModel ✅ (text chat live, voice UI ready)
    ├── download/              DownloadScreen stub
    └── onboarding/            OnboardingScreen stub
```

---

## Voice State Machine

```
IDLE → (FAB tap) → LISTENING
LISTENING → (speech detected) → RECORDING
           → (8s timeout) → IDLE
RECORDING → (speech end, ≥500ms) → PROCESSING
           → (30s cap) → PROCESSING
PROCESSING → (done) → SPEAKING → IDLE
           → (error) → ERROR → IDLE
SPEAKING → (FAB interrupt) → IDLE
```

---

## Performance Targets

| Metric | Target |
|---|---|
| LiteRT engine init | < 10s |
| Speech end → first audio | < 10s mid-range, < 6s flagship |
| Peak RAM during inference | < 3 GB |

---

## Phase 2 (deferred)

- Wake-word detection (always-on)
- Full LiveKit migration: swap AudioCaptureManager for LiveKit room + WebRTC, move inference to Python agent on self-hosted server
- Multi-turn conversation context
- On-device RAG for personal documents
