# J.A.R.V.I.S — Session Handoff (2026-06-14)

## What Was Done This Session

### 1. Interrupt-to-Speak (Tap Only)
Added `interruptAndListen()` to `ChatViewModel`. When user taps the orb during SPEAKING:
1. `ttsSynthesizer.stop()` — cuts audio immediately
2. `processingJob?.cancel()` — cancels token collection coroutine
3. `finalizeAllStreamingMessages()` — unsticks any streaming bubble
4. `startVoiceCapture()` — goes straight to LISTENING

`processingJob: Job?` field added to track the token-collection coroutine so it can be cancelled on interrupt.

`ChatScreen.kt` orb tap handler now routes `VoiceState.SPEAKING` specifically to `interruptAndListen()`.

**Barge-in (voice-activated interrupt) was attempted but removed** — self-triggering issue where JARVIS's TTS audio bled into the mic and caused false VAD triggers. Manual tap-to-interrupt is reliable. Can revisit with proper echo-cancellation pipeline later.

### 2. Auto-Listen After Each Response
After TTS `onDone` fires, instead of going to `VoiceState.IDLE`, the ViewModel calls `startVoiceCapture(autoListen = true)`.

`startVoiceCapture(autoListen: Boolean = false)` — new parameter:
- `autoListen = false` (explicit tap): 8s no-speech timeout shows "I didn't catch that" message
- `autoListen = true` (after response): 8s no-speech timeout silently falls back to IDLE, no message

**UX flow:** User taps orb once → speaks → JARVIS answers → automatically back to LISTENING → repeat. Tap orb to stop at any point.

### 3. Acoustic Echo Cancellation
`AudioCaptureManager.kt`:
- Changed `AudioSource.MIC` → `AudioSource.VOICE_COMMUNICATION` (enables hardware AEC/NS/AGC on S23)
- Added `AcousticEchoCanceler.create(ar.audioSessionId)?.enabled = true` after AudioRecord creation

### 4. PROCESSING → SPEAKING Transition Fix
Previously `voiceState = SPEAKING` was set immediately when `sendAudio` returned the flow — before any tokens existed. The orb showed SPEAKING while JARVIS was silently computing (500–600ms dead time).

**Fix:** Removed the early state update. `voiceState` stays `PROCESSING` until the first token arrives in `tokenFlow.collect`. Transition to SPEAKING now happens on first token — which coincides with TTS starting to queue audio.

```kotlin
tokenFlow.collect { chunk ->
    if (startMs == 0L) {
        startMs = System.currentTimeMillis()
        _uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
    }
    ...
}
```

### 5. VoiceModeScreen — Full Animation Overhaul
Replaced all per-state visuals in `VoiceModeScreen.kt`:

| State | Before | After |
|---|---|---|
| IDLE | 1 drifting arc | 3 breathing concentric rings (offset phases) + 5 orbiting particles at 3 radii |
| LISTENING | Rotating arc + bottom bar waveform | Thicker rotating arc (8dp) + 48-bar circular polar waveform (frame-clock `sin()`) |
| PROCESSING | 2 arcs + 2 particles | 3 arcs + 16-dot rotating ring + 5 particles at varied radii/speeds + pulsing coil segments |
| SPEAKING | 3 ripple rings | 5 ripple rings (alternating cyan/purple) + 32-bar radial pulse waveform |

`OrbCore` updated to accept `isListening`, `isProcessing`, `segmentPulse` — coil segments pulse during PROCESSING.

Bottom `WaveformBars` composable removed (replaced by in-orb circular waveform for LISTENING).

Frame-clock animation (`withFrameMillis`) drives smooth organic waveforms for LISTENING and SPEAKING states.

---

## Current Working State

| Feature | Status |
|---|---|
| Tap orb → LISTENING | ✅ Working |
| Speak → PROCESSING → SPEAKING → auto-LISTENING | ✅ Working |
| Tap orb during SPEAKING → interrupt + LISTENING | ✅ Working |
| PROCESSING stays until first token (no dead-time) | ✅ Fixed |
| AEC enabled on VOICE_COMMUNICATION source | ✅ Done |
| Full animated orb (4 distinct states) | ✅ Enhanced |
| Voice-activated barge-in | ❌ Removed (echo issue) |

---

## Files Changed This Session

| File | Change |
|---|---|
| `ui/chat/ChatViewModel.kt` | `interruptAndListen()`, `processingJob`, `autoListen` param, SPEAKING transition fix, barge-in removed |
| `ui/chat/ChatScreen.kt` | Orb tap routes SPEAKING → `interruptAndListen()` |
| `audio/AudioCaptureManager.kt` | VOICE_COMMUNICATION source + AEC |
| `ui/voice/VoiceModeScreen.kt` | Full animation overhaul — circular waveforms, particles, dotted rings |

---

## Next Candidates

### Barge-in (voice interrupt without tap)
The reliable way to implement this on Android without hardware echo cancellation issues:
- Use `AudioEffect` with `NoiseSuppressor` + `AcousticEchoCanceler` on the AudioRecord
- Or: detect barge-in using **energy delta** — only trigger if mic energy is significantly above the known TTS playback level (requires measuring TTS output level as a reference)
- Or: use Android's `MediaRecorder.AudioSource.VOICE_COMMUNICATION` with `MODE_IN_COMMUNICATION` audio mode set on `AudioManager` — this enables the full hardware voice processing stack including echo reference

### Settings Screen
- TTS speed/pitch control
- VAD sensitivity slider
- Max response length

### Error Recovery
- Handle LiteRT-LM OOM gracefully (show message, offer to reload)
- Network-offline detection for model download
