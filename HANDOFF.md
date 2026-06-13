# J.A.R.V.I.S — Session Handoff (2026-06-13)

## What Was Done This Session

### 1. TTS Replaced: Kokoro ONNX → Android TextToSpeech
Kokoro was causing 200–400ms synthesis latency per sentence. Replaced with Android's built-in `TextToSpeech` API via a new `AndroidTtsEngine.kt` wrapper. First audio now starts within ~30ms of tokens arriving. No model files needed — works out of the box on any Android device.

**Key design:** `AndroidTtsEngine` uses an `AtomicInteger pendingCount` and an `AtomicBoolean sealed` flag. `speak()` increments the counter; each `onDone` callback decrements it. `sealQueue(callback)` marks the queue closed — when `pendingCount` hits 0 and the queue is sealed, the drain callback fires. This is reliable regardless of whether TTS drains before or after `sealQueue` is called.

### 2. Idle-Token Watchdog in TtsSynthesizer
`sendMessageAsync` returns a hot `SharedFlow` that **never closes**. The previous `textTokens.collect {}` in `synthesizeAndPlay` therefore blocked forever — `sealQueue(onDone)` was unreachable, so the cursor and "JARVIS RESPONDING" label never cleared.

**Fix:** `coroutineScope` runs a `collectJob` (collecting tokens) and a watchdog loop concurrently. Watchdog polls every 150ms; once 1200ms passes with no new token, it cancels `collectJob` and execution continues to `sealQueue(onDone)`. Critically, the countdown only starts after `firstTokenReceived = true` — otherwise it would fire immediately during audio preprocessing (which takes several seconds before first token).

### 3. tok/s Now Measures Generation Speed Only
`startMs` in `processVoiceInput()` was set at function entry, including audio-preprocessing wait time (several seconds). Changed to `var startMs = 0L`, reset to `currentTimeMillis()` on the first token. Displayed tok/s now matches the visual streaming speed.

### 4. VAD Restored with Adaptive Noise Floor
Replaced the fixed 2-second capture window with a proper VAD:
- 500ms ambient calibration on mic open → `threshold = max(0.01f, ambientRms × 3.5f)`
- `SILENCE_MS = 900`, `MIN_SPEECH_MS = 200`
- Emits `SpeechStart`, `SpeechEnd(pcm)`, `Timeout`

### 5. 8-Second No-Speech Timeout
`startVoiceCapture()` launches a `noSpeechJob` that fires after 8s if `speechDetected` is still false. Stops capture and has JARVIS say (and display) "I didn't catch that — could you tap the mic and try again?"

### 6. App Rebranded as J.A.R.V.I.S
- `strings.xml`: `app_name` → "J.A.R.V.I.S"
- `LiteRtLmEngine` system prompt: full JARVIS persona (Iron Man), concise spoken responses, addresses user as "sir"
- `HomeScreen`: title "J.A.R.V.I.S", tagline "Just A Rather Very Intelligent System", updated description
- `ChatScreen`: app bar, bubble labels, status strings, empty state all updated

### 7. Chat UI Improvements
- `⋮` menu replaced with `+` (Add) icon → "New Thread" confirmation `AlertDialog`
- Empty state greeting is time-based: Good morning/afternoon/evening/night, Sir
- Debug "REC 5s" button removed

### 8. Dead Code Removed
Deleted: `KokoroEngine.kt`, `EnglishPhonemizer.kt`, `AudioPlayer.kt`, `assets/cmudict.txt`

---

## Current Working State

| Feature | Status |
|---|---|
| Mic tap → VAD → LLM inference | ✅ Working |
| LLM text streaming to chat UI | ✅ Working |
| Android TTS playback | ✅ Working |
| Cursor clears after response | ✅ Fixed |
| "JARVIS RESPONDING" clears after done | ✅ Fixed |
| tok/s reflects actual generation speed | ✅ Fixed |
| 8s no-speech timeout with spoken message | ✅ Working |
| Adaptive VAD with noise floor calibration | ✅ Working |
| JARVIS branding throughout | ✅ Done |
| New Thread confirmation dialog | ✅ Working |
| Time-based greeting in empty state | ✅ Working |

---

## Files Changed This Session

| File | Change |
|---|---|
| `tts/AndroidTtsEngine.kt` | **New** — Android TTS wrapper with atomic drain detection |
| `tts/TtsSynthesizer.kt` | Rewrote around AndroidTtsEngine; idle-token watchdog; firstTokenReceived guard |
| `tts/KokoroEngine.kt` | **Deleted** |
| `tts/EnglishPhonemizer.kt` | **Deleted** |
| `tts/AudioPlayer.kt` | **Deleted** |
| `assets/cmudict.txt` | **Deleted** |
| `ui/chat/ChatViewModel.kt` | VAD restore, noSpeechTimeout, finalizeAllStreamingMessages, startMs on first token |
| `ui/chat/ChatScreen.kt` | JARVIS branding, + icon, AlertDialog, time greeting, remove debug button |
| `inference/LiteRtLmEngine.kt` | JARVIS system prompt |
| `ui/home/HomeScreen.kt` | JARVIS title/tagline/description |
| `res/values/strings.xml` | app_name → J.A.R.V.I.S |
| `audio/VoiceActivityDetector.kt` | Adaptive noise floor, unified loop |
| `CHANGELOG.md` | v0.6.0 entry |

---

## Known Issues / Next Session

1. **TTS voice quality** — Android TTS uses the device's default voice engine (Google TTS if installed). No control over speaking rate, pitch, or voice personality beyond `TextToSpeech.setLanguage()`. If more natural speech is needed, consider a streaming TTS API or a lighter on-device model.

2. **No multi-turn voice context** — LiteRT-LM's `Conversation` object maintains text history, but each voice turn sends only the current audio clip. Prior voice turns are not re-injected as audio context.

3. **Watchdog timing** — `TOKEN_IDLE_MS = 1200L` works for typical responses. Very fast models or very slow token streams may need tuning. If JARVIS cuts off mid-sentence, increase this value.

4. **AudioFocus** — Android TTS manages its own audio focus. No explicit `AudioManager.requestAudioFocus()` needed, but notifications may still interrupt mid-response on some devices.
