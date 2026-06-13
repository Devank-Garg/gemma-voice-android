# J.A.R.V.I.S — System Architecture

## Overview

J.A.R.V.I.S is a fully on-device Android voice assistant. The user speaks, Gemma 4 E2B processes the audio natively, and Android TTS speaks the response. No cloud APIs, no server, fully offline after model placement.

**Key constraints that shaped every design decision:**
- The Gemma 4 E2B model is ~2.58 GB and takes 5–10s to initialize.
- LiteRT-LM's `sendMessageAsync` returns a hot `SharedFlow` that **never emits completion**.
- Audio inference can take several seconds before the first token appears.
- All processing runs on-device; RAM is tight (~3 GB peak during inference).

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        UI Layer                         │
│  HomeScreen ──── HomeViewModel                          │
│  ChatScreen ──── ChatViewModel                          │
└──────────────────────┬──────────────────────────────────┘
                       │ StateFlow<UiState>
┌──────────────────────▼──────────────────────────────────┐
│                    Domain Layer                         │
│  AudioCaptureManager → VoiceActivityDetector            │
│  LiteRtLmEngine       TtsSynthesizer → AndroidTtsEngine │
│  ModelRepository      SettingsRepository                │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│                   Platform / SDK                        │
│  AudioRecord   LiteRT-LM 0.10.2   Android TextToSpeech  │
│  WorkManager   DataStore           OkHttp               │
└─────────────────────────────────────────────────────────┘
```

---

## Dependency Injection (Hilt)

All singletons are provided at `SingletonComponent` scope via Hilt.

```
SingletonComponent
├── AppModule
│   ├── WorkManager
│   └── OkHttpClient
├── AudioCaptureManager (@Singleton)
├── PcmBuffer           (@Singleton)
├── VoiceActivityDetector (@Singleton)
├── LiteRtLmEngine      (@Singleton)
├── AudioTokenizer      (@Singleton)
├── ModelRepository     (@Singleton)
├── SettingsRepository  (@Singleton)
├── AndroidTtsEngine    (@Singleton)
└── TtsSynthesizer      (@Singleton)

ViewModels (ViewModelComponent — per-ViewModel scope)
├── ChatViewModel   → injects all domain singletons above
├── HomeViewModel   → injects ModelRepository
└── DownloadViewModel → injects ModelRepository
```

Hilt workers use `@HiltWorker` + `@AssistedInject` for WorkManager integration.

---

## Data Flow: Voice Turn (Primary Path)

```
User taps mic
    │
    ▼
ChatViewModel.startVoiceCapture()
    │   sets voiceState = LISTENING
    │   launches noSpeechJob (8s timeout watchdog)
    │
    ▼
AudioCaptureManager.startCapture()          [Dispatchers.IO]
    │   AudioRecord @ 16kHz, PCM_FLOAT, mono
    │   1024-sample chunks → PcmBuffer (SharedFlow)
    │
    ▼
VoiceActivityDetector.detect(pcmChunks)
    │   Phase 1 — Calibration (500ms)
    │     Samples ambient RMS
    │     threshold = max(0.01f, ambientRms × 3.5f)
    │
    │   Phase 2 — Detection
    │     energy > threshold → SpeechStart
    │     900ms silence → SpeechEnd(pcm: FloatArray)
    │
    ▼ SpeechEnd(pcm)
ChatViewModel.processVoiceInput(pcm)
    │   finalizeAllStreamingMessages()  ← defensive cleanup
    │   appends placeholder AssistantMessage (isStreaming=true)
    │   sets voiceState = PROCESSING
    │
    ├──────────────────────────────────────────────────────┐
    │  engine.sendAudio(pcm)                               │
    │      pcm → 16-bit WAV container                      │
    │      Content.AudioBytes + Content.Text("respond")    │
    │      → LiteRT-LM Conversation.sendMessageAsync()     │
    │      returns SharedFlow<String> (never completes)    │
    │                                                      │
    │  shareIn(viewModelScope, Eagerly, replay=512)         │
    │  ─── tokenFlow ──────────────────────────────────────┤
    │                                                      │
    ├─ Job A: ttsSynthesizer.synthesizeAndPlay(tokenFlow)  │
    │      coroutineScope {                                │
    │        collectJob: collect tokens → buffer           │
    │          first-chunk: 2+ spaces OR sentence boundary │
    │          subsequent: sentence/soft boundaries        │
    │          engine.speak(sentence) [Android TTS]        │
    │        watchdog: poll 150ms                          │
    │          exits when lastTokenMs stale > 1200ms       │
    │          AND firstTokenReceived == true              │
    │        collectJob.cancel()                           │
    │      }                                               │
    │      engine.sealQueue(onDone)                        │
    │         → fires when TTS queue drains                │
    │         → finalizeAssistantMessage()                 │
    │         → voiceState = IDLE                          │
    │                                                      │
    └─ Job B: tokenFlow.collect { token →                  │
          if (startMs == 0L) startMs = now()   ← first tok│
          accumulated += token                             │
          patchStreamingMessage(accumulated, tps)          │
       }                                                   │
       (never completes; killed when coroutine is cleared) │
                                                          │
AndroidTtsEngine                                          │
    │   speak() → pendingCount++                          │
    │   TextToSpeech.QUEUE_ADD                            │
    │   onDone() → pendingCount--                         │
    │   if (pendingCount==0 && sealed) → drainCallback()  │
    │                                                     │
    ▼                                                     │
Speaker output                                            │
```

---

## Data Flow: Text Turn

```
User types + sends
    │
    ▼
ChatViewModel.sendTextMessage(text)
    │   appends UserMessage + placeholder AssistantMessage
    │   sets voiceState = PROCESSING
    │
    ▼
engine.sendMessage(text) → Flow<String>
    │
    ▼
.collect { token →
    accumulated += token
    patchStreamingMessage(accumulated, tps)
}
    │   Flow completes (text mode has normal completion)
    ▼
finalizeAssistantMessage()
voiceState = IDLE
```

---

## Data Flow: Model Initialization

```
App launch
    │
    ▼
ChatViewModel.init block
    │
    ├── modelRepository.getModelPath()
    │       ↳ DataStore custom path (if set by user)
    │       ↳ External files dir default path
    │
    ├── path found → loadEngine(path)
    └── path null → observe download completion → loadEngine(path)

loadEngine(path):
    │
    ▼
uiState.engineState = Loading
    │
    ├── engine.initialize(modelPath)          [suspend, IO]
    │       Try Backend.GPU()
    │       On failure: Backend.CPU()
    │       EngineConfig → Engine.initialize()
    │       engine.createConversation(buildConversationConfig())
    │
    └── ttsSynthesizer.initializeEngine()     [suspend]
            suspendCancellableCoroutine
            TextToSpeech(context) { status →
                if OK → resume; else → resumeWithException
            }
    │
    ▼
uiState.engineState = Ready
uiState.backendLabel = "GPU" | "CPU"
```

---

## Key Components

### AudioCaptureManager
Wraps `AudioRecord` into a coroutine-friendly interface. Reads 1024-sample float chunks on `Dispatchers.IO` and emits them into `PcmBuffer` (a `MutableSharedFlow` with `extraBufferCapacity=64`).

Audio spec: 16 kHz · mono · `ENCODING_PCM_FLOAT`

### VoiceActivityDetector
Energy-based VAD with **per-session adaptive noise floor**. On each `startVoiceCapture()` call, the first 500ms of audio calibrates the ambient threshold — this handles quiet offices, loud kitchens, and everything in between without manual tuning.

State machine: Calibrating → Idle → Speaking → Idle  
Events: `SpeechStart`, `SpeechEnd(pcm)`, `Timeout`

### LiteRtLmEngine
Thin wrapper around LiteRT-LM `Engine` + `Conversation`. Maintains a single persistent `Conversation` across all turns (the model sees full history). GPU is attempted first; falls back to CPU without crashing.

**Critical behaviour:** `sendMessageAsync` returns a `SharedFlow` that never calls `onCompletion`. All code that consumes this flow must handle non-termination explicitly (see TtsSynthesizer watchdog and `shareIn` with `replay=512`).

### AndroidTtsEngine
Android `TextToSpeech` wrapper with reliable queue-drain detection. The problem it solves: `setOnUtteranceProgressListener.onDone` fires per-utterance, and there's no built-in "all done" signal. Solution: `AtomicInteger pendingCount` tracks in-flight utterances; `sealQueue(callback)` marks that no more utterances will be added and fires the callback once `pendingCount` reaches zero (even if it already has).

### TtsSynthesizer
Bridges the LLM token stream to Android TTS. Two design challenges:
1. **The hot stream never ends** — solved by a `coroutineScope` with a watchdog loop that exits after 1200ms idle. The countdown only starts after `firstTokenReceived`, because audio preprocessing can delay the first token by several seconds.
2. **Low latency** — the first TTS call is made after just 2 words (or any sentence boundary), not after a full sentence. Subsequent chunks flush at sentence boundaries.

### ChatViewModel
The state machine hub. Owns all concurrent jobs (`vadJob`, `inactivityJob`) and coordinates the audio → inference → TTS pipeline. Key patterns:
- `finalizeAllStreamingMessages()` is called defensively at the start of each new turn to clear any messages left in streaming state by a previously aborted turn.
- `startMs = 0L` resets to `currentTimeMillis()` on first token so tok/s excludes audio-processing latency.
- The 10-minute inactivity timer calls `engine.close()` to free ~3 GB of model RAM.

---

## State Machines

### VoiceState (ChatViewModel)
```
         ┌─────────────┐
    ┌────►│    IDLE     │◄────────────────────────────┐
    │     └──────┬──────┘                             │
    │            │ mic tap                             │
    │     ┌──────▼──────┐                             │
    │     │  LISTENING  │  VAD detects speech         │
    │     └──────┬──────┘                             │
    │            │ SpeechEnd                           │
    │     ┌──────▼──────┐                             │
    │     │ PROCESSING  │  waiting for first token     │
    │     └──────┬──────┘                             │
    │            │ first token queued to TTS           │
    │     ┌──────▼──────┐                             │
    │     │  SPEAKING   │  TTS playing                │
    │     └──────┬──────┘                             │
    │            │ TTS queue drained (onDone)          │
    │            └────────────────────────────────────┘
    │
    │     ┌─────────────┐
    └─────│    ERROR    │  any exception in pipeline
          └─────────────┘
```

### EngineState (ChatViewModel)
```
IDLE → Loading → Ready
              ↘ Error
```

### DownloadState (ModelRepository)
```
Idle → Downloading → Verifying → Complete
              ↘ Error
```

---

## Concurrency Model

| Scope | Dispatcher | Purpose |
|---|---|---|
| `viewModelScope` | Default | Coroutine lifetime tied to ViewModel |
| `Dispatchers.IO` | IO thread pool | AudioRecord reads, file I/O, WorkManager |
| `Dispatchers.Main` | Main thread | StateFlow updates, Compose recomposition |
| `SharingStarted.Eagerly` | viewModelScope | Token flow shared between TTS + UI jobs |

### SharedFlow replay buffer
`tokenFlow = engine.sendAudio(pcm).shareIn(viewModelScope, SharingStarted.Eagerly, replay=512)`

Two collectors subscribe to `tokenFlow`:
- **Job A** (TTS): May start slightly after the flow begins
- **Job B** (UI): Collects in parallel

`replay=512` ensures Job A doesn't miss tokens emitted before it subscribes. Without this, early tokens are silently dropped and TTS starts mid-sentence.

---

## Memory Management

| Phase | Approximate RAM |
|---|---|
| App idle (no model) | ~80 MB |
| Model loading | 2.5–3 GB |
| Active inference (GPU) | ~2.8 GB |
| Active inference (CPU) | ~2.6 GB |
| After inactivity timeout | ~80 MB |

`engine.close()` is called:
1. After 10 minutes of inactivity (`inactivityJob`)
2. On `ViewModel.onCleared()` (back navigation / process kill)

---

## File Layout

```
com.example.gemmaapp/
├── GemmaVoiceApp.kt            Application, Hilt entry, notification channels
├── MainActivity.kt             Single activity, Compose host
│
├── di/
│   └── AppModule.kt            WorkManager + OkHttpClient providers
│
├── data/
│   ├── model/
│   │   ├── ChatMessage.kt      Message data class (role, text, tps, isStreaming)
│   │   ├── DownloadState.kt    Sealed class: Idle/Downloading/Verifying/Complete/Error
│   │   └── ModelInfo.kt        ModelInfo data class + GEMMA_4_E2B constant
│   ├── repository/
│   │   ├── ModelRepository.kt  Model path resolution, WorkManager orchestration
│   │   └── SettingsRepository.kt DataStore wrapper
│   └── download/
│       ├── ModelDownloadWorker.kt  Resumable OkHttp download (@HiltWorker)
│       └── ChecksumVerifier.kt    SHA-256 file verification
│
├── audio/
│   ├── AudioCaptureManager.kt  AudioRecord → PcmBuffer coroutine pipeline
│   ├── PcmBuffer.kt            MutableSharedFlow<FloatArray> ring buffer
│   ├── VoiceActivityDetector.kt Adaptive energy VAD (calibrate → detect)
│   └── VadEvent.kt             SpeechStart / SpeechEnd(pcm) / Timeout
│
├── inference/
│   ├── LiteRtLmEngine.kt       LiteRT-LM Engine+Conversation wrapper (GPU/CPU)
│   ├── AudioTokenizer.kt       Float32 PCM → byte array encoder
│   ├── GemmaVoiceSession.kt    Stub (Sprint 3)
│   └── ModelSelector.kt        Device RAM → model selection
│
├── tts/
│   ├── AndroidTtsEngine.kt     Android TextToSpeech + atomic drain detection
│   └── TtsSynthesizer.kt       Token stream → sentence chunks → TTS pipeline
│
└── ui/
    ├── Screen.kt               Route definitions (Home, Chat)
    ├── NavGraph.kt             NavHost: Home → Chat
    ├── home/
    │   ├── HomeScreen.kt       Model locator UI, hero, status card, CTA button
    │   └── HomeViewModel.kt    URI resolution, ModelRepository bridge
    ├── chat/
    │   ├── ChatScreen.kt       Full chat UI: bubbles, waveform, mic, keyboard
    │   └── ChatViewModel.kt    Voice+text pipeline, state machine
    ├── download/
    │   ├── DownloadScreen.kt   Download progress UI (unused in current nav)
    │   └── DownloadViewModel.kt Pass-through to ModelRepository
    ├── onboarding/
    │   ├── OnboardingScreen.kt Stub
    │   └── OnboardingViewModel.kt Stub
    └── theme/
        ├── Color.kt            Brand palette (BrandPurple, BrandCyan, backgrounds)
        ├── Theme.kt            Material 3 dark theme
        └── Type.kt             Typography scale
```

---

## Design Decisions & Rationale

| Decision | Rationale |
|---|---|
| Android TTS instead of Kokoro ONNX | Kokoro added 200–400ms per sentence; Android TTS starts in ~30ms |
| Idle-token watchdog instead of flow completion | `sendMessageAsync` never emits completion; watchdog is the only reliable termination signal |
| `replay=512` on shared token flow | Two collectors start at slightly different times; replay prevents early-token loss |
| `firstTokenReceived` guard on watchdog | Audio preprocessing delays first token by seconds; without the guard, watchdog fires before generation starts |
| `startMs` reset on first token | Audio preprocessing time diluted tok/s by 3–10×; resetting gives accurate generation throughput |
| Adaptive VAD threshold | Fixed thresholds fail in varied environments; per-session calibration (500ms) adapts automatically |
| Single persistent `Conversation` object | LiteRT-LM maintains KV cache across turns; re-creating it loses history and wastes init time |
| `finalizeAllStreamingMessages()` at turn start | If a previous turn's TTS `onDone` never fired (e.g., user interrupted), old messages stay stuck in streaming state; defensive cleanup prevents cursor accumulation |
| 10-minute inactivity timer | Gemma 4 E2B occupies ~3 GB; freeing it after inactivity prevents OOM on other apps |
| Single-activity Compose navigation | Avoids Fragment back-stack complexity; Compose handles all transitions |
| `largeHeap="true"` in manifest | Required to load 2.58 GB model into process heap |

---

## Known Limitations

1. **Token stream never terminates** — Any new collector on `sendMessageAsync` must implement its own termination logic (watchdog, external cancellation, or time limit).
2. **No multi-turn voice context** — Each voice turn sends only the current audio clip. Prior voice turns are not re-injected as audio.
3. **TTS voice quality** — Depends on the device's installed TTS engine (Google TTS if present). No control over voice personality.
4. **VAD watchdog tuning** — `TOKEN_IDLE_MS=1200ms` works for mid-range devices. Very fast models may benefit from a shorter window; very slow ones may get cut off.
5. **Single model** — `ModelSelector` always returns `GEMMA_4_E2B`; low-RAM warning deferred.
6. **No persistent history** — Chat history exists only in memory and in LiteRT-LM's KV cache. Process kill or `resetConversation()` clears it.
