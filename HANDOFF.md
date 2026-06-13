# J.A.R.V.I.S — Session Handoff (2026-06-13)

## What Was Done This Session

### 1. VoiceModeScreen — Full-Screen Arc-Reactor UI
New file: `ui/voice/VoiceModeScreen.kt`

Replaces the chat-bubble layout when `isKeyboardMode = false`. Designed from a Claude Design mockup (4-state artboard matching Gemini Live / ChatGPT Voice UX).

**4 animated states (all using `rememberInfiniteTransition`):**

| State | Orb animation | Ring decoration |
|---|---|---|
| IDLE | Scale 1→1.045 (4s) | Static purple ring + slow drifting cyan arc (9s) |
| LISTENING | Scale 1→1.07 (2.6s) | Rotating purple→cyan gradient arc (3.6s) + waveform bars |
| PROCESSING | Scale 1→1.045 (4s) | Dual counter-rotating arcs (2.1s / 1.5s) + 2 orbiting particles |
| SPEAKING | Scale 1→1.07 (2.6s) | 3 staggered ripple rings (2.6s, delays 0 / 870ms / 1730ms via Animatable) |

**Arc reactor core** (Canvas): coil segments (30 alternating lit/dim arcs), radial gradient fill (purple-dominant or cyan-dominant for SPEAKING), outer glow bloom, two structural rings, bright center highlight.

**Engine loading guard:** `engineReady: Boolean` parameter. While `EngineState != Ready`, orb tap is disabled and label shows "INITIALIZING…" — prevents silent failures where VAD captures audio but `processVoiceInput` drops it.

**Navigation:** keyboard icon (top-left) → `toggleKeyboardMode()` → switches to text/history mode. Power button → `onEndSession` → navigates back to home.

**Transcript removed** — was showing previous response text during state transitions, creating visual bleed-through. Removed entirely for clean voice-only experience.

### 2. ChatScreen — Compose Anti-Pattern Fix
The original integration used `if (!isKeyboardMode) { VoiceModeScreen(...); return }`. Early `return` in a `@Composable` is an anti-pattern: Compose's slot table tracks composable calls positionally, and an early return changes the tree shape between recompositions, breaking state observation. This caused `voiceState` changes in the ViewModel to never trigger recomposition — the UI froze on LISTENING.

**Fix:** Changed to `if-else` so both branches are always "declared" and the slot table stays consistent.

### 3. System Prompt — TTS Pronunciation
`J.A.R.V.I.S.` renamed to `JARVIS` in the system prompt. Android TTS was spelling out individual letters due to the dots. UI labels remain `J.A.R.V.I.S` for the Iron Man aesthetic.

### 4. System Prompt — "Sir" Overuse
Added explicit constraint: *"Use 'sir' sparingly — only at the very start of a reply when it feels natural, never mid-sentence or repeatedly within the same response. Most replies should have no 'sir' at all."* Previously the model used "sir" in nearly every sentence.

---

## Current Working State

| Feature | Status |
|---|---|
| Full-screen voice orb (4 animated states) | ✅ Working |
| LISTENING → IDLE timeout (8s no-speech) | ✅ Fixed (if-else branch) |
| Engine loading guard ("INITIALIZING…") | ✅ Working |
| Keyboard toggle → text/history mode | ✅ Working |
| TTS pronounces "JARVIS" correctly | ✅ Fixed |
| "Sir" used sparingly | ✅ Fixed |
| Interrupt-to-speak mid-TTS | ❌ Not yet implemented |

---

## Files Changed This Session

| File | Change |
|---|---|
| `ui/voice/VoiceModeScreen.kt` | **New** — full-screen arc-reactor voice UI |
| `ui/chat/ChatScreen.kt` | if-else branch for voice/text mode; pass engineReady |
| `inference/LiteRtLmEngine.kt` | JARVIS pronunciation + "sir" constraint in system prompt |
| `CHANGELOG.md` | v0.7.0 entry |

---

## Next: Interrupt-to-Speak

**Goal:** User taps orb (or mic) while JARVIS is speaking → immediately stop TTS, start new voice capture.

**Implementation plan:**

In `VoiceModeScreen`, the orb tap when `voiceState == SPEAKING` currently calls `viewModel.stopVoiceCapture()`. That stops the VAD but doesn't stop TTS.

The correct flow:
1. User taps orb during SPEAKING
2. `ttsSynthesizer.stop()` — cuts audio immediately
3. `finalizeAllStreamingMessages()` — unstick any streaming message
4. `voiceState = IDLE` (or go straight to LISTENING)
5. Start new voice capture

In `ChatViewModel`, add a new method (or modify `stopVoiceCapture`):

```kotlin
fun interruptAndListen() {
    // Cancel any in-progress TTS and token collection
    ttsSynthesizer.stop()
    vadJob?.cancel()
    inactivityJob?.cancel()
    finalizeAllStreamingMessages()
    _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
    // Optionally start capture immediately:
    startVoiceCapture()
}
```

In `VoiceModeScreen` / `ChatScreen` orb tap handler:
```kotlin
VoiceState.SPEAKING -> viewModel.interruptAndListen()
```

**Edge case:** The token collection loop (`tokenFlow.collect`) in `processVoiceInput` runs until the ViewModel is cleared. When interrupt fires, the tokenFlow is still collecting and will try to call `patchStreamingMessage`. `finalizeAllStreamingMessages()` unsticks the bubble, but the collect loop should also be cancelled. Currently `processVoiceInput` doesn't track its coroutine — consider adding a `processingJob: Job?` field so it can be cancelled on interrupt.

**LLM context:** Decide whether to `engine.resetConversation()` on interrupt. If not, the partial response stays in the model's KV cache and the next turn may be confused. Safest: reset on interrupt. Trade-off: loses conversation history.
