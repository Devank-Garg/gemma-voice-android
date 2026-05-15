# Gemma Voice — Session Handoff (2026-05-15)

## What Was Done This Session

### 1. Voice Pipeline Crash Fixed
**Root cause:** `PcmBuffer.chunks` is a `SharedFlow` that never completes. After `SpeechEnd`, `stopCapture()` stopped new chunks arriving but the VAD coroutine stayed alive, still subscribed. A second mic tap created a second VAD coroutine — both received the same PCM chunks, both called `processVoiceInput()` concurrently → two simultaneous `conv.sendMessageAsync()` calls on one `Conversation` object → crash.

**Fix:** `ChatViewModel` now tracks `private var vadJob: Job?`. Every `startVoiceCapture()` call cancels the previous job before launching a new one.

### 2. VAD End-of-Speech Replaced with Fixed 2-Second Window
The VAD energy/silence detector was not reliably firing `SpeechEnd` on the test device (ambient noise kept resetting the silence timer). Replaced with a fixed 2-second capture window that fires `processVoiceInput()` automatically. The VAD code (`VoiceActivityDetector.kt`) is intact — restore by swapping back to `vad.detect()` inside `vadJob` in `startVoiceCapture()`.

### 3. Waveform UI Fixed
`WaveformBars` was active during both `LISTENING` and `SPEAKING`, so the waveform kept animating while the model was talking. Changed to active during `LISTENING` only (line 695 in `ChatScreen.kt`).

### 4. TTS Inter-Sentence Gaps Fixed
`TtsSynthesizer.synthesizeStream()` previously ran synthesis and playback sequentially in the same coroutine — Kokoro had to finish synthesizing sentence N+1 before AudioTrack could play it. Rewrote using `channelFlow` + a dedicated synthesis coroutine on `Dispatchers.IO`. Now sentence N+1 synthesizes in parallel while sentence N plays.

### 5. TTS Startup Latency Reduced
First audio chunk now fires after 3 words accumulate (space count ≥ 2), regardless of punctuation. Previously waited for first sentence-ending `.!?\n`. Subsequent chunks still flush at sentence and soft (`,;:`) boundaries.

### 6. Pronunciation Quality — CMU Dictionary
`EnglishPhonemizer` replaced its ~250-word hardcoded dictionary with the full CMU Pronouncing Dictionary (134k words) bundled as `app/src/main/assets/cmudict.txt`. Loaded once at Kokoro init on `Dispatchers.IO`. Lookup chain: CMU dict → small fallback dict → rule-based.

### 7. Acronym Pronunciation Fixed
All-caps words ≥ 2 letters (AI, GPU, LLM, API, CPU, etc.) are detected as acronyms and spelled out letter-by-letter using a dedicated `LETTER_NAMES` map (e.g. AI → "ay-eye", GPU → "gee-pee-you"). CMU dict cannot be used here because its first entry for "a" is the article pronunciation `AH0`, not the letter name `EY1`.

---

## Current Working State

| Feature | Status |
|---|---|
| Mic tap → 2s recording → LLM inference | ✅ Working |
| LLM text streaming to chat UI | ✅ Working |
| Kokoro TTS with CMU dict phonemization | ✅ Working |
| Acronym pronunciation (AI, GPU, etc.) | ✅ Working |
| AudioTrack playback (24 kHz float) | ✅ Working |
| Inter-sentence parallel synthesis | ✅ Working |
| Waveform animates during recording only | ✅ Working |
| VAD end-of-speech detection | ⚠️ Bypassed (2s window used instead) |

---

## Files Changed This Session

| File | Change |
|---|---|
| `ui/chat/ChatViewModel.kt` | vadJob tracking, 2s timed capture, replay=512, wad removed |
| `ui/chat/ChatScreen.kt` | Waveform active only during LISTENING |
| `tts/TtsSynthesizer.kt` | channelFlow parallel synthesis, 3-word early flush |
| `tts/EnglishPhonemizer.kt` | CMU dict + LETTER_NAMES for acronyms |
| `tts/KokoroEngine.kt` | loadDict() call in initialize() |
| `app/src/main/assets/cmudict.txt` | CMU Pronouncing Dictionary 0.7b (new file, 3.5 MB) |
| `CHANGELOG.md` | v0.5.0 entry |

---

## Known Issues / Next Session

1. **VAD restoration** — the 2-second fixed window works but is not a natural UX. Need to tune `VoiceActivityDetector` thresholds for the test device (try lowering `energyThreshold` from `0.005f` or reducing `silenceMs` from `800ms`). Once tuned, restore `vad.detect()` in `startVoiceCapture()` and re-add `VoiceActivityDetector` to the `ChatViewModel` constructor.

2. **TTS startup latency** — audio starts after ~3 words + Kokoro synthesis time (~200-400ms). The floor is bounded by Kokoro's per-sentence inference speed. Options to explore: smaller synthesis chunks (1-2 words), or a streaming Kokoro approach if the ONNX model supports it.

3. **Kokoro model placement** — still manual ADB push. No in-app download for Kokoro model files yet.

4. **No multi-turn voice context** — each voice turn is independent (conversation history is maintained by LiteRT-LM's `Conversation` object for text, but voice turns don't include prior turns in the audio prompt).

5. **AudioFocus** — no `AudioManager.requestAudioFocus()` during TTS playback, so notifications and media can interrupt the voice response.
