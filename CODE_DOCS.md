# J.A.R.V.I.S — Code Documentation

Full API reference for every class in the codebase. For system-level data flows and design rationale see `ARCHITECTURE.md`.

---

## Table of Contents
1. [Application Entry Points](#1-application-entry-points)
2. [Dependency Injection](#2-dependency-injection)
3. [Data Models](#3-data-models)
4. [Repositories](#4-repositories)
5. [Download & Verification](#5-download--verification)
6. [Audio Pipeline](#6-audio-pipeline)
7. [Inference Layer](#7-inference-layer)
8. [Text-to-Speech](#8-text-to-speech)
9. [UI — ViewModels](#9-ui--viewmodels)
10. [UI — Screens](#10-ui--screens)
11. [Theme System](#11-theme-system)
12. [Navigation](#12-navigation)

---

## 1. Application Entry Points

### `GemmaVoiceApp`
`com.example.gemmaapp.GemmaVoiceApp`  
`class GemmaVoiceApp : Application(), Configuration.Provider`

Application subclass. Declared in `AndroidManifest.xml`. Performs two jobs at startup: wires Hilt's `HiltWorkerFactory` into WorkManager (required for `@HiltWorker` annotation to work), and creates the notification channel used by the model download foreground service.

```kotlin
// Injected by Hilt
@Inject lateinit var workerFactory: HiltWorkerFactory

// Configuration.Provider implementation — required for Hilt + WorkManager
override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

// Creates "model_download" notification channel (Android O+)
private fun createNotificationChannels()
```

**Constants**
- `DOWNLOAD_CHANNEL_ID = "model_download"` — notification channel ID used by `ModelDownloadWorker`

---

### `MainActivity`
`com.example.gemmaapp.MainActivity`  
`class MainActivity : ComponentActivity()` + `@AndroidEntryPoint`

Single activity for the entire app. Sets edge-to-edge layout and renders `NavGraph` wrapped in `GemmaAPPTheme`. No business logic lives here.

---

## 2. Dependency Injection

### `AppModule`
`com.example.gemmaapp.di.AppModule`  
`@Module @InstallIn(SingletonComponent::class)`

Provides infrastructure singletons that cannot use `@Inject constructor` (WorkManager, OkHttpClient).

```kotlin
@Provides @Singleton
fun provideWorkManager(@ApplicationContext context: Context): WorkManager

@Provides @Singleton
fun provideOkHttpClient(): OkHttpClient
// connect timeout: 30s, read timeout: 60s
```

---

## 3. Data Models

### `ModelInfo`
`com.example.gemmaapp.data.model.ModelInfo`

```kotlin
data class ModelInfo(
    val name: String,           // Display name, e.g. "Gemma 4 E2B"
    val fileName: String,       // Filename on disk, e.g. "gemma-4-E2B-it.litertlm"
    val sizeBytes: Long,        // Expected file size for progress calculation
    val sha256: String,         // Expected hash; empty string = skip verification
    val huggingFaceRepo: String,
    val downloadUrl: String
)
```

**Singleton instance**
```kotlin
val GEMMA_4_E2B: ModelInfo  // Defined in ModelInfo.kt, imported across the app
```

---

### `DownloadState`
`com.example.gemmaapp.data.model.DownloadState`

Sealed class representing all states of the model download lifecycle.

```kotlin
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Float,           // 0.0–1.0
        val bytesDownloaded: Long,
        val totalBytes: Long           // 0 if Content-Length header absent
    ) : DownloadState()
    object Verifying : DownloadState()
    object Complete : DownloadState()
    data class Error(val message: String) : DownloadState()
}
```

---

### `ChatMessage`
`com.example.gemmaapp.data.model.ChatMessage`

Immutable snapshot of a single chat turn. `isStreaming = true` means the assistant is still generating; the `text` field updates in place via `copy()`.

```kotlin
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val tokensPerSecond: Float = 0f,
    val isStreaming: Boolean = false
) {
    enum class Role { USER, ASSISTANT }
}
```

---

## 4. Repositories

### `ModelRepository`
`com.example.gemmaapp.data.repository.ModelRepository`  
`@Singleton class ModelRepository @Inject constructor(...)`

Single source of truth for model availability. Bridges WorkManager download state, filesystem checks, and DataStore persistence.

**Dependencies**
- `@ApplicationContext Context`
- `WorkManager`
- `SettingsRepository`

**API**
```kotlin
// Emits the current download/model state as a Flow.
// Sources: WorkManager work info → local file check → DataStore custom path.
fun observeDownloadState(): Flow<DownloadState>

// Returns true if the model file exists in the default external files location.
fun isModelDownloaded(): Boolean

// Returns the absolute filesystem path to the model, or null if not found.
// Priority: DataStore custom path → default ADB/external path.
suspend fun getModelPath(): String?

// Persists a user-supplied model path (from the file picker) to DataStore.
suspend fun saveModelPath(path: String)
```

**WorkManager work name:** `"model_download"`  
**Default model location:** `Context.getExternalFilesDir(null)/models/gemma-4-E2B-it.litertlm`

---

### `SettingsRepository`
`com.example.gemmaapp.data.repository.SettingsRepository`  
`@Singleton class SettingsRepository @Inject constructor(...)`

Thin DataStore wrapper for app-level preferences.

**Dependencies**
- `@ApplicationContext Context`

**DataStore keys**
- `KEY_MODEL_PATH: Preferences.Key<String>`
- `KEY_ONBOARDING_DONE: Preferences.Key<Boolean>`

**API**
```kotlin
val modelPath: Flow<String?>         // null if never set
val isOnboardingDone: Flow<Boolean>  // false if never set

suspend fun setModelPath(path: String)
suspend fun setOnboardingDone()
```

---

## 5. Download & Verification

### `ModelDownloadWorker`
`com.example.gemmaapp.data.download.ModelDownloadWorker`  
`@HiltWorker class ModelDownloadWorker @AssistedInject constructor(...)`  
Extends `CoroutineWorker`.

Resumable HTTP download worker. Reads progress key from `WorkInfo` via `setProgress()`. Uses `Range` header to resume interrupted downloads from an existing `.part` file.

**Input data keys**
```kotlin
KEY_DOWNLOAD_URL     // String — full HTTPS URL
KEY_FILE_NAME        // String — target filename (e.g. "gemma-4-E2B-it.litertlm")
KEY_EXPECTED_SHA256  // String — expected hash, empty = skip
```

**Progress data keys** (set via `setProgress`)
```kotlin
KEY_PROGRESS          // Float — 0.0–1.0 during download; -1.0 during verification
KEY_BYTES_DOWNLOADED  // Long
KEY_TOTAL_BYTES       // Long
KEY_ERROR             // String — error message on failure
```

**Download sequence**
1. Validate input keys
2. Create `models/` directory in external files dir
3. Check for existing `.part` file → set `Range: bytes=N-` header
4. Stream HTTP body to file, emit progress every 500ms via `setProgress`
5. Rename `.part` → final file name
6. Verify SHA-256 (unless `expectedSha256` is empty)
7. Return `Result.success()`

**Foreground notification**
- Shown automatically as a foreground service notification
- Channel: `DOWNLOAD_CHANNEL_ID`

---

### `ChecksumVerifier`
`com.example.gemmaapp.data.download.ChecksumVerifier`  
`@Singleton class ChecksumVerifier @Inject constructor()`

```kotlin
// Returns true if the file's SHA-256 matches expectedSha256 (case-insensitive).
// Returns true immediately if expectedSha256 is empty (verification disabled).
fun verify(file: File, expectedSha256: String): Boolean
```

---

## 6. Audio Pipeline

### `PcmBuffer`
`com.example.gemmaapp.audio.PcmBuffer`  
`@Singleton class PcmBuffer @Inject constructor()`

```kotlin
// Hot SharedFlow of 16kHz float32 PCM chunks.
// extraBufferCapacity=64 prevents back-pressure drop during bursty capture.
val chunks: SharedFlow<FloatArray>

// Emits a chunk to all collectors. Suspend if buffer is full.
suspend fun write(chunk: FloatArray)
```

---

### `AudioCaptureManager`
`com.example.gemmaapp.audio.AudioCaptureManager`  
`@Singleton class AudioCaptureManager @Inject constructor(...)`

**Dependencies**
- `@ApplicationContext Context`
- `PcmBuffer`

**Audio configuration**
- Source: `MediaRecorder.AudioSource.MIC`
- Sample rate: 16,000 Hz
- Channel: `CHANNEL_IN_MONO`
- Format: `ENCODING_PCM_FLOAT`
- Chunk size: 1,024 samples (~64ms per chunk)
- Buffer size: `4 × AudioRecord.getMinBufferSize()`

**API**
```kotlin
// Starts a new capture coroutine on Dispatchers.IO.
// Safe to call while already capturing (no-op if isRecording).
fun startCapture(scope: CoroutineScope)

// Cancels the capture coroutine and releases AudioRecord.
fun stopCapture()

// Returns the underlying SharedFlow for VAD and other consumers.
fun pcmChunks(): SharedFlow<FloatArray>

val isRecording: Boolean
```

---

### `VoiceActivityDetector`
`com.example.gemmaapp.audio.VoiceActivityDetector`  
`@Singleton class VoiceActivityDetector @Inject constructor()`

Energy-based VAD with adaptive noise floor calibration. Designed to work without manual threshold tuning across different acoustic environments.

**Parameters**

| Constant | Value | Purpose |
|---|---|---|
| `CALIBRATION_MS` | 500 | Ambient noise sampling window |
| `SILENCE_MS` | 900 | Consecutive silence before end-of-speech |
| `MIN_SPEECH_MS` | 200 | Reject clips shorter than this |
| `MAX_SPEECH_MS` | 30,000 | Hard cap (Gemma 4 audio limit) |
| `MIN_THRESHOLD` | 0.01f | Floor for very quiet rooms |
| `SPEECH_RATIO` | 3.5f | Speech must be 3.5× louder than ambient |

**API**
```kotlin
// Returns a Flow of VadEvents from the given PCM chunk stream.
// Calibrates for the first CALIBRATION_MS, then detects speech.
// The returned flow completes after emitting SpeechEnd or Timeout.
fun detect(pcmChunks: Flow<FloatArray>): Flow<VadEvent>
```

**Internal algorithm**
```
Calibrate: accumulate chunks for 500ms → compute RMS → threshold = max(0.01, rms × 3.5)

Detect loop:
  chunk.rms() > threshold → speechDetected = true, accumulate chunk
  speechDetected && chunk.rms() < threshold → increment silenceMs
  silenceMs >= SILENCE_MS && accumulatedMs >= MIN_SPEECH_MS → emit SpeechEnd(pcm)
  silenceMs >= SILENCE_MS && accumulatedMs < MIN_SPEECH_MS → emit Timeout
  accumulatedMs >= MAX_SPEECH_MS → emit SpeechEnd(pcm)
```

---

### `VadEvent`
`com.example.gemmaapp.audio.VadEvent`

```kotlin
sealed class VadEvent {
    // Emitted when energy exceeds threshold after calibration
    object SpeechStart : VadEvent()

    // Emitted when 900ms of silence follows detected speech (≥ 200ms duration)
    data class SpeechEnd(val pcm: FloatArray) : VadEvent()

    // Emitted when speech was detected but was too short (< 200ms)
    // Also emitted if nothing is heard within the VAD session
    object Timeout : VadEvent()
}
```

---

## 7. Inference Layer

### `LiteRtLmEngine`
`com.example.gemmaapp.inference.LiteRtLmEngine`  
`@Singleton class LiteRtLmEngine @Inject constructor(...)`

Wraps LiteRT-LM `Engine` and `Conversation`. Maintains a single persistent conversation so the model sees full multi-turn history.

**Dependencies**
- `@ApplicationContext Context`

**State**
```kotlin
private var engine: Engine?           // null until initialize()
private var conversation: Conversation? // null until initialize()
var activeBackend: String             // "GPU" or "CPU" (set during initialize)
val isReady: Boolean                  // conversation != null
```

**API**
```kotlin
// Initializes the LLM engine. Must be called before any sendMessage/sendAudio calls.
// Tries GPU backend first; falls back to CPU on exception.
// Takes 5–10s on mid-range devices. Run on a background dispatcher.
suspend fun initialize(modelPath: String)

// Sends a text message to the current conversation.
// Returns a Flow<String> of token chunks. The flow completes when generation ends.
fun sendMessage(text: String): Flow<String>

// Sends audio (float32 PCM) to the current conversation.
// Internally wraps PCM in a 16-bit WAV container before passing to LiteRT-LM.
// Returns a Flow<String> of token chunks that NEVER completes — callers must
// implement their own termination logic.
fun sendAudio(pcm: FloatArray): Flow<String>

// Clears conversation history without reloading the model.
fun resetConversation()

// Releases all LiteRT-LM resources. Engine must be re-initialized after calling this.
fun close()
```

**WAV encoding (internal)**
`buildWav(samples: FloatArray, sampleRate: Int): ByteArray`  
Produces a standard 44-byte RIFF/WAVE/fmt/data header + 16-bit PCM samples. Float32 values are clamped to [-32768, 32767] before conversion.

**Conversation config**
```kotlin
ConversationConfig(
    systemInstruction = Contents.of("You are J.A.R.V.I.S. ..."),
    samplerConfig = SamplerConfig(topK=40, topP=0.95f, temperature=0.8f)
)
```

---

### `AudioTokenizer`
`com.example.gemmaapp.inference.AudioTokenizer`  
`@Singleton class AudioTokenizer @Inject constructor()`

```kotlin
// Encodes float32 PCM as little-endian bytes for direct use with LiteRT-LM audio APIs.
// Gemma 4 spec: 16kHz, float32 LE, mono, max 480,000 samples (30s).
fun toAudioBytes(pcm: FloatArray): ByteArray
```

---

### `ModelSelector`
`com.example.gemmaapp.inference.ModelSelector`  
`@Singleton class ModelSelector @Inject constructor(...)`

```kotlin
// Returns the appropriate ModelInfo for the current device.
// Currently always returns GEMMA_4_E2B (RAM-based selection deferred to Sprint 3).
fun select(): ModelInfo
```

---

### `GemmaVoiceSession` *(stub)*
`com.example.gemmaapp.inference.GemmaVoiceSession`  
`@Singleton class GemmaVoiceSession @Inject constructor(...)`

Not yet implemented. Intended for Sprint 3 audio preprocessing pipeline.

```kotlin
fun process(pcm: FloatArray): Flow<String>  // TODO("Sprint 3")
```

---

## 8. Text-to-Speech

### `AndroidTtsEngine`
`com.example.gemmaapp.tts.AndroidTtsEngine`  
`@Singleton class AndroidTtsEngine @Inject constructor(...)`

Android `TextToSpeech` wrapper with atomic, race-condition-free drain detection. The core problem it solves: there is no built-in "all utterances finished" signal in Android TTS. This class implements one using two atomics.

**Dependencies**
- `@ApplicationContext Context`

**Atomic state**
```kotlin
private val pendingCount: AtomicInteger   // Number of utterances currently in the TTS queue
private val sealed: AtomicBoolean         // True after sealQueue() — no more utterances coming
private val drainCallback: AtomicReference<(() -> Unit)?>  // Fires when pendingCount==0 && sealed
```

**API**
```kotlin
// Initializes Android TextToSpeech engine. Sets locale to US English.
// Suspends until TTS engine is ready. Throws if initialization fails.
suspend fun initialize()

val isReady: Boolean

// Queues text for speech synthesis (QUEUE_ADD — does not interrupt current speech).
// Returns false if the engine is not initialized or text is blank.
// Increments pendingCount.
fun speak(text: String): Boolean

// Marks the queue as sealed and registers a drain callback.
// The callback fires once all previously queued utterances finish playing.
// Safe to call after all utterances have already finished (fires immediately).
// Only fires the callback once even if called multiple times.
fun sealQueue(callback: () -> Unit)

// Stops all current and queued speech immediately.
// Resets pendingCount, sealed flag, and drain callback.
fun stop()

// stop() + TTS engine shutdown. Engine cannot be used after this.
fun close()
```

**UtteranceProgressListener callbacks**
- `onDone(utteranceId)` — Decrements `pendingCount`. If `pendingCount == 0` and `sealed == true`, fires and clears `drainCallback`.
- `onError(utteranceId)` — Same as `onDone` (error counts as completion to prevent deadlock).

---

### `TtsSynthesizer`
`com.example.gemmaapp.tts.TtsSynthesizer`  
`@Singleton class TtsSynthesizer @Inject constructor(...)`

Bridges the LLM token stream to Android TTS. Handles sentence chunking, early-first-chunk strategy, and reliable termination despite a non-closing source flow.

**Dependencies**
- `AndroidTtsEngine`

**Constants**
```kotlin
TOKEN_IDLE_MS = 1200L  // Watchdog exits after this many ms with no new token
```

**API**
```kotlin
suspend fun initializeEngine()   // Delegates to AndroidTtsEngine.initialize()
fun closeEngine()                // Delegates to AndroidTtsEngine.close()
fun stop()                       // Delegates to AndroidTtsEngine.stop()

// Speaks text immediately, interrupting any current speech.
// Uses stop() + speak() + sealQueue{} pattern.
fun announce(text: String)

// Streams tokens to Android TTS as sentences, calling onDone after the last
// sentence finishes playing.
//
// Termination: sendMessageAsync never closes its flow, so this function uses
// a coroutineScope with two concurrent jobs:
//   - collectJob: collects tokens, buffers them, queues sentences to TTS
//   - watchdog: polls every 150ms; exits when no token has arrived for TOKEN_IDLE_MS
//               AND at least one token has been received (firstTokenReceived guard
//               prevents premature exit during audio preprocessing latency)
//
// After the watchdog exits: flush remaining buffer, call engine.sealQueue(onDone).
// If no sentences were queued: call onDone() directly.
suspend fun synthesizeAndPlay(textTokens: Flow<String>, onDone: () -> Unit)
```

**Chunking strategy**
- **First chunk:** Queued after `buf.count { it == ' ' } >= 2` OR any of `.!?\n` — starts audio fast
- **Subsequent chunks:** Sentence boundaries (`.!?\n`) OR soft boundaries (`,;:` after ≥ 20 chars since last split)
- **Tail flush:** Any remaining buffer content is queued after the watchdog exits

---

## 9. UI — ViewModels

### `ChatViewModel`
`com.example.gemmaapp.ui.chat.ChatViewModel`  
`@HiltViewModel class ChatViewModel @Inject constructor(...)`

Primary state machine for the chat screen. Coordinates the audio → inference → TTS pipeline. All state is exposed as a single `StateFlow<UiState>`.

**Dependencies**
- `@ApplicationContext Context`
- `LiteRtLmEngine`
- `ModelRepository`
- `AudioCaptureManager`
- `VoiceActivityDetector`
- `TtsSynthesizer`

**Nested types**
```kotlin
sealed class EngineState {
    object Idle : EngineState()       // Model not loaded (initial or after timeout)
    object Loading : EngineState()    // Initializing (shows spinner overlay)
    object Ready : EngineState()      // Ready to receive input
    data class Error(val message: String) : EngineState()
}

enum class VoiceState {
    IDLE,        // Waiting for mic tap
    LISTENING,   // Mic open, VAD calibrating/detecting
    RECORDING,   // (reserved — VAD uses LISTENING for both phases)
    PROCESSING,  // Audio sent to LLM, waiting for tokens
    SPEAKING,    // TTS is playing
    ERROR        // Pipeline exception
}

data class UiState(
    val messages: List<ChatMessage>,
    val engineState: EngineState,
    val voiceState: VoiceState,
    val inputText: String,
    val isKeyboardMode: Boolean,
    val backendLabel: String          // "GPU" or "CPU", shown in app bar
)
```

**Public API**
```kotlin
val uiState: StateFlow<UiState>

// Sends a text message through the LLM. Reloads engine if it was unloaded
// due to inactivity (user must wait for Ready state, then retry).
fun sendTextMessage(text: String)

// Opens the mic and starts VAD detection.
// Immediately sets voiceState = LISTENING so the waveform appears during calibration.
// Cancels any in-progress vadJob before launching a new one.
fun startVoiceCapture()

// Cancels VAD, stops AudioCaptureManager, stops TTS, resets voiceState to IDLE.
fun stopVoiceCapture()

// Updates the text input field content.
fun updateInput(text: String)

// Toggles between voice and keyboard input modes.
fun toggleKeyboardMode()

// Resets LiteRT-LM conversation history and clears the message list.
fun clearConversation()
```

**Private pipeline**
```kotlin
private fun loadEngine(modelPath: String)
// Guards against double-init (checks engineState before proceeding).
// Sets engineState = Loading → initializes engine + TTS → sets engineState = Ready.

private fun processVoiceInput(pcm: FloatArray)
// Entry point for a completed voice clip.
// 1. finalizeAllStreamingMessages()
// 2. Adds placeholder AssistantMessage (isStreaming=true)
// 3. Calls engine.sendAudio(pcm), shares flow with replay=512
// 4. Launches Job A: synthesizeAndPlay → finalizeAssistantMessage + IDLE in onDone
// 5. Launches Job B: collect tokens → patchStreamingMessage (runs until cancelled)
// startMs is 0 until first token arrives (accurate tok/s)

private fun patchStreamingMessage(text: String, tokens: Int, tps: Float)
// Finds the last isStreaming=true ASSISTANT message and updates its fields.

private fun finalizeAssistantMessage(text: String, tokens: Int, startMs: Long)
// Finds the last ASSISTANT message and sets isStreaming=false with final TPS.

private fun finalizeAllStreamingMessages()
// Sets isStreaming=false on every ASSISTANT message.
// Called at the start of each new voice turn as a defensive cleanup.

private fun resetInactivityTimer()
// Cancels and restarts a 10-minute coroutine that calls engine.close() + sets engineState=Idle.
```

**Timers**
- `INACTIVITY_TIMEOUT_MS = 600_000L` (10 minutes)
- `NO_SPEECH_TIMEOUT_MS = 8_000L` (8 seconds)

---

### `HomeViewModel`
`com.example.gemmaapp.ui.home.HomeViewModel`  
`@HiltViewModel class HomeViewModel @Inject constructor(...)`

**Dependencies**
- `ModelRepository`

**API**
```kotlin
val downloadState: StateFlow<DownloadState>  // Live model state
val locateError: StateFlow<String?>          // Non-null if URI resolution failed

// Resolves a content:// URI from the file picker to an absolute filesystem path.
// Saves the path via ModelRepository on success.
// Sets locateError on failure.
fun onModelLocated(uri: Uri, contentResolver: ContentResolver)

fun clearLocateError()
```

**URI resolution strategy**
1. `file://` scheme → use `uri.path` directly
2. `content://com.android.externalstorage.documents` → parse `primary:rel/path` or `UUID:rel/path`
3. Fallback → query `MediaStore.MediaColumns.DATA` column

---

### `DownloadViewModel`
`com.example.gemmaapp.ui.download.DownloadViewModel`  
`@HiltViewModel class DownloadViewModel @Inject constructor(...)`

Thin pass-through. Exposes `ModelRepository.observeDownloadState()` as a `StateFlow`. No business logic.

---

### `OnboardingViewModel`
`com.example.gemmaapp.ui.onboarding.OnboardingViewModel`  
Empty stub. Not yet implemented.

---

## 10. UI — Screens

### `ChatScreen`
`com.example.gemmaapp.ui.chat.ChatScreen`

Main conversational interface. Approximately 910 lines of Compose code.

**Composable tree**
```
ChatScreen(uiState, onBack, onMicClick, onStopClick, onSend, onInput, onToggleKeyboard, onClearConversation)
├── AmbientGlow               — static radial gradient background accents
├── ChatAppBar
│   ├── Back button
│   ├── Title: "J.A.R.V.I.S" + engine status dot (color: loading=yellow, ready=cyan, error=red)
│   ├── Backend label ("GPU"/"CPU") in cyan when active
│   └── New Thread (+) button → AlertDialog confirmation
├── LazyColumn (messages)
│   ├── EmptyState             — pulsing orb + time-based greeting (morning/afternoon/evening/night)
│   ├── DateDivider            — date label between messages from different days
│   ├── UserBubble             — gradient fill (BrandPurple→BrandCyan), right-aligned
│   ├── AssistantBubble        — glass card, Markdown rendering, streaming cursor, tok/s footer
│   └── ThinkingIndicator      — JARVIS avatar + animated bouncing dots (PROCESSING state)
└── BottomBar
    ├── Keyboard mode: TextField + send button
    └── Voice mode: 32-bar animated waveform + mic button
        ├── Mic button states: idle (mic icon) / listening (ripple rings) / speaking (stop icon)
        └── Status label: context-sensitive text below mic
```

**Voice status labels**

| `voiceState` | Label shown |
|---|---|
| `IDLE` | "TAP MIC TO SPEAK" |
| `LISTENING` | "LISTENING…" |
| `PROCESSING` | "PROCESSING…" |
| `SPEAKING` | "JARVIS RESPONDING" |
| `ERROR` | "ERROR — TAP TO RETRY" |

**Waveform**: 32 bars with random heights, animated via `InfiniteTransition`. Active only during `LISTENING`.

---

### `HomeScreen`
`com.example.gemmaapp.ui.home.HomeScreen`

Model locator and app entry point. Handles the file picker result via `rememberLauncherForActivityResult`.

**Composable tree**
```
HomeScreen(onStartChat, viewModel)
├── MicHero          — layered circular glow + mic icon
├── Title: "J.A.R.V.I.S"
├── Tagline: "Just A Rather Very Intelligent System"
├── Caption: "Fully on-device · No cloud · No data leaves your phone"
├── ModelCard        — switches on DownloadState:
│   ├── Idle/Error   → IdleModelRow: model name + "Locate" button (folder icon)
│   ├── Downloading  → DownloadingModelRow: progress bar + bytes/percent
│   ├── Verifying    → VerifyingModelRow: indeterminate progress bar
│   └── Complete     → ReadyModelRow: "Ready · INT4 · on-device" + check icon
├── locateError      — inline error text (red) if URI resolution fails
└── GradientButton   — "Start Conversation" (enabled only when Complete)
```

---

### `DownloadScreen` *(unused in current NavGraph)*
`com.example.gemmaapp.ui.download.DownloadScreen`

Download progress UI kept as a stub. Wired to `DownloadViewModel` but not registered in `NavGraph.kt`.

---

### `OnboardingScreen` *(stub)*
`com.example.gemmaapp.ui.onboarding.OnboardingScreen`

Simple card with "Get Started" button. Not wired into `NavGraph.kt`.

---

## 11. Theme System

### `Color.kt`
`com.example.gemmaapp.ui.theme`

All colors used in the app. **Never hardcode hex values in screens** — always import from here.

```kotlin
// Brand
val BrandPurple      = Color(0xFF7C3AED)
val BrandPurpleLight = Color(0xFF9F67F8)
val BrandCyan        = Color(0xFF06B6D4)
val BrandCyanLight   = Color(0xFF22D3EE)

// Backgrounds (dark-only; no light variant)
val BackgroundDark   = Color(0xFF080B14)   // Page background
val SurfaceDark      = Color(0xFF0F1326)   // Progress tracks, input fields
val CardDark         = Color(0xFF141830)   // Cards, bubbles
val BorderDark       = Color(0xFF252847)   // Subtle card borders

// Semantic
val SuccessGreen     = Color(0xFF10B981)
val ErrorRed         = Color(0xFFEF4444)

// Text
val TextPrimary      = Color(0xFFF1F5FF)   // Main labels, headings
val TextSecondary    = Color(0xFF8B92AD)   // Subtext, metadata
val TextMuted        = Color(0xFF555B78)   // Disabled, hints

// Standard gradient (use as a Brush, not a Color)
// Brush.horizontalGradient(listOf(BrandPurple, BrandCyan))
```

---

### `Theme.kt`
`com.example.gemmaapp.ui.theme.GemmaAPPTheme`

```kotlin
// Wraps content in a forced dark Material 3 theme.
// dynamicColor = false — always uses brand colors regardless of Android 12+ dynamic theming.
@Composable
fun GemmaAPPTheme(content: @Composable () -> Unit)
```

---

### `Type.kt`
`com.example.gemmaapp.ui.theme.Typography`

Standard Material 3 `Typography` object. `bodyLarge` set to 16sp / 24sp line height / 0.5sp letter spacing.

---

## 12. Navigation

### `Screen`
`com.example.gemmaapp.ui.Screen`

```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat")
}
```

---

### `NavGraph`
`com.example.gemmaapp.ui.NavGraph`

```kotlin
@Composable
fun NavGraph(navController: NavHostController)
```

**Routes**
- `Home` — start destination; `onStartChat` navigates to `Chat`
- `Chat` — `onBack` calls `navController.popBackStack()`

---

## Appendix: Critical Behaviours

### The hot stream problem
`LiteRtLmEngine.sendAudio()` wraps `Conversation.sendMessageAsync()` which returns a `SharedFlow` that never calls `onCompletion`. Any code that `collect`s this flow will suspend forever. Two mitigations are used:

1. **`TtsSynthesizer.synthesizeAndPlay`** — idle-token watchdog cancels the collect job after 1200ms idle
2. **`ChatViewModel.processVoiceInput`** — `tokenFlow.collect` in Job B runs until the ViewModel is cleared; finalization is handled by the TTS `onDone` callback, not flow completion

### The tok/s accuracy fix
`processVoiceInput` sets `var startMs = 0L` and updates it to `currentTimeMillis()` on the **first token**. Before this fix, `startMs` was set at function entry, including several seconds of audio preprocessing time, which made tok/s read 3–10× lower than the actual generation speed.

### The drain callback race
`AndroidTtsEngine.sealQueue()` may be called before or after all utterances finish playing. The `AtomicBoolean sealed` + `AtomicInteger pendingCount` pattern handles both orderings:
- If TTS drains first → `pendingCount` is already 0 when `sealQueue` is called → fires immediately
- If `sealQueue` is called first → `onDone` decrements to 0 after last utterance → fires then
