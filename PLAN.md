# Phase 1: Gemma 4 Local Android Voice Assistant — Implementation Plan

## Context

Build a fully local Android voice assistant app using Google's Gemma 4 model (released April 2, 2026). Phase 1 scope: user installs the app, it downloads the model on first launch, then allows continuous voice-based Q&A — all on-device with no cloud dependencies.

The user requested LiveKit for voice interactions. Research reveals that LiveKit Agents are **server-side worker processes** — they cannot run inside an Android app. For a purely local app, LiveKit Android SDK is used only for its audio capture/VAD utilities, giving a clean migration path to full LiveKit architecture in Phase 2.

---

## Architecture

```
Microphone
  ↓
AudioRecord (16kHz, 32-bit float PCM)
  ↓
VoiceActivityDetector (energy + zero-crossing VAD)
  ↓ (speech end detected, ≤30s PCM clip)
Gemma 4 E2B (LiteRT-LM, on-device)
  ← native audio token input — no separate STT needed
  ↓ streaming text tokens
Kokoro TTS v1.0 (ONNX Runtime, on-device, ~82MB)
  ↓ PCM chunks
AudioTrack playback
```

**Why this architecture:**
- Gemma 4 E2B has **native audio input** (16kHz, max 30s) — no Whisper STT step required
- LiteRT-LM is Google's official, non-deprecated framework with pre-converted Gemma 4 models on HuggingFace
- Kokoro TTS supports streaming synthesis (first sentence plays before LLM finishes) — Android native TTS cannot stream

---

## Model Selection

**Primary target: Gemma 4 E2B** (2B effective parameters, ~4.2 GB INT4)

| Device RAM | Model | Format | Disk Size | Inference Speed |
|---|---|---|---|---|
| ≥6 GB | **Gemma 4 E2B** (primary) | `.litertlm` INT4 | ~4.2 GB | 12–20 tok/s |
| <6 GB | Gemma 4 E2B (show low-RAM warning) | `.litertlm` INT4 | ~4.2 GB | 10–18 tok/s |

Model source: `litert-community/gemma-4-e2b-it` on HuggingFace.
E4B is not included in Phase 1 — smaller download and wider device compatibility. E4B support can be added in Phase 2.

---

## Component Stack & Gradle Dependencies

```kotlin
// App build.gradle.kts
android {
    compileSdk = 35
    minSdk = 26           // AudioFormat.ENCODING_PCM_FLOAT requires API 21+, but 26 for WorkManager
    aaptOptions { noCompress += listOf(".litertlm", ".onnx") }
}

dependencies {
    // LiteRT-LM — Gemma 4 inference
    implementation("com.google.ai.edge.litertlm:litertlm-android:1.0.0-alpha04")
    implementation("com.google.ai.edge.litert:litert-gpu-delegate:1.0.0")

    // ONNX Runtime — Kokoro TTS
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")

    // LiveKit Android SDK — audio utilities / VAD (Phase 2: full voice agent)
    implementation("io.livekit:livekit-android:2.24.1")

    // WorkManager — background model download with resume
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // DataStore, Lifecycle, Coroutines, Hilt, OkHttp
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

---

## Project Structure

```
com.example.gemmavoice/
├── GemmaVoiceApp.kt
├── data/
│   ├── model/        ModelInfo.kt, DownloadState.kt
│   ├── repository/   ModelRepository.kt, SettingsRepository.kt
│   └── download/     ModelDownloadWorker.kt, ChecksumVerifier.kt
├── inference/
│   ├── LiteRtLmEngine.kt       — LiteRT-LM session, GPU fallback
│   ├── AudioTokenizer.kt       — PCM float[] → multimodal prompt
│   ├── GemmaVoiceSession.kt    — VAD clip → streaming text Flow
│   └── ModelSelector.kt        — RAM-based E2B selection
├── tts/
│   ├── KokoroEngine.kt         — ONNX session, text → PCM
│   └── TtsSynthesizer.kt       — sentence streaming → AudioTrack
├── audio/
│   ├── AudioCaptureManager.kt  — AudioRecord at 16kHz float
│   ├── VoiceActivityDetector.kt — energy + zero-crossing VAD
│   ├── PcmBuffer.kt            — ring buffer, Flow<FloatArray>
│   └── VadEvent.kt             — SpeechStart, SpeechEnd(pcm), Timeout
└── ui/
    ├── MainActivity.kt          — single-activity, Navigation host
    ├── onboarding/              OnboardingFragment + ViewModel
    ├── download/                DownloadFragment + ViewModel
    └── chat/                    ChatFragment + ChatViewModel + ConversationAdapter
```

---

## Critical Files to Create/Modify

- `app/build.gradle.kts` — dependencies, aaptOptions
- `inference/LiteRtLmEngine.kt` — core inference wrapper
- `audio/VoiceActivityDetector.kt` — VAD pipeline
- `data/download/ModelDownloadWorker.kt` — resumable OkHttp download
- `ui/chat/ChatViewModel.kt` — state machine orchestrating audio→inference→TTS

---

## Voice Interaction State Machine

```
IDLE → (FAB tap) → LISTENING
LISTENING → (speech detected) → RECORDING
           → (8s timeout, no speech) → IDLE
RECORDING → (speech end, ≥500ms speech) → PROCESSING
           → (30s hard cap) → PROCESSING
           → (cancel tap) → IDLE
PROCESSING → (inference complete) → SPEAKING
           → (error) → ERROR → IDLE
SPEAKING → (TTS done) → IDLE
         → (FAB interrupt) → IDLE
```

---

## Key Implementation Details

### Model Download (ModelDownloadWorker)
- `WorkManager` `CoroutineWorker`, policy `KEEP` (survives kill/restart)
- OkHttp `Range: bytes=<offset>-` header for resume support
- Writes to `<filesDir>/models/<name>.litertlm.part`, renames after SHA-256 passes
- Storage: `Context.getExternalFilesDir()` — app-private, no permission needed, ~5GB free space required

### Gemma 4 Native Audio Input
```kotlin
val prompt = "<start_of_turn>user\n<audio>\n$systemPrompt<end_of_turn>\n<start_of_turn>model\n"
session.generateResponseAsync(prompt, audioData = pcmFloatArray) { partial, done -> ... }
```
Audio spec: 16kHz, 32-bit float, normalized [-1, 1], max 480,000 samples (30s).

### Streaming TTS (Kokoro ONNX)
- TtsSynthesizer splits token stream at sentence boundaries (`.`, `?`, `!`, or every ~80 tokens)
- Each sentence chunk synthesized and played via AudioTrack in stream mode
- First audio chunk plays before LLM finishes generating — reduces perceived latency

### LiteRT-LM Init
Engine takes 5–10s to initialize — show loading spinner on first entry to ChatFragment. Initialize once on app start, keep alive for session duration.

---

## Implementation Sprints

| Sprint | Days | Work |
|---|---|---|
| 0 — Scaffold | 1 | Project setup, Gradle, Hilt, NavGraph, permissions |
| 1 — Download | 2–3 | ModelDownloadWorker, SettingsRepository, Onboarding + Download UI |
| 2 — Audio/VAD | 4–5 | AudioRecord, PcmBuffer, VoiceActivityDetector, unit tests |
| 3 — Inference | 6–8 | LiteRtLmEngine, AudioTokenizer, GemmaVoiceSession, latency benchmarks |
| 4 — TTS | 9–10 | KokoroEngine (ONNX), TtsSynthesizer streaming, AudioTrack |
| 5 — Integration | 11–12 | ChatViewModel state machine, ChatFragment UI, end-to-end wiring |
| 6 — Polish | 13 | Error handling (OOM, corruption, permissions), settings screen |

---

## Verification / Test Plan

### Automated
- `VoiceActivityDetectorTest`: pre-recorded PCM clips (silence, speech, noise) → assert correct VadEvents
- `ChecksumVerifierTest`: known-good vs corrupted bytes
- `ModelDownloadWorkerTest`: mock OkHttp, assert `Range` header on resume
- `TtsSynthesizerTest`: token stream → assert two synthesis jobs for "Hello. World."

### Manual E2E Script
1. **Fresh install**: download E2B, kill at 50%, reopen → verify resume (not restart)
2. **Voice query**: "What is the capital of France?" → latency <6s to first audio
3. **Interrupt**: long response → tap FAB mid-playback → audio cuts, returns to LISTENING
4. **30s cap**: speak 35s continuously → auto-submits at 30s mark
5. **OOM graceful fail**: memory-pressure test → no crash, graceful error dialog

### Performance Targets
- LiteRT engine init: <10s
- End-to-end latency (speech end → first audio): <6s flagship, <10s mid-range
- Peak RAM during inference: <3 GB (E2B)

---

## Phase 2 Preview (deferred)

- Wake-word detection (always-on, no FAB tap)
- Whisper.cpp STT as fallback for audio Gemma 4 handles poorly
- **Full LiveKit migration**: swap `AudioCaptureManager` for LiveKit room + WebRTC, move inference to Python agent worker (self-hosted LiveKit server on Docker + Ollama with Gemma 4)
- Multi-turn conversation context (currently each turn is stateless)
- On-device RAG for personal document Q&A
