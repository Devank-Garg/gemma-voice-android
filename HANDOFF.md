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

### 6. Male TTS Voice
`AndroidTtsEngine.kt` — Added `selectMaleVoice()` called after `setLanguage`:
- Prefers offline voices
- Matches Google TTS male patterns: `-iom-`, `-iog-`, `-iob-`, `-iol-`
- Matches Samsung TTS male pattern: `SMTm`

### 7. Tool Calling — 5 JARVIS Tools
New file `inference/JarvisToolSet.kt` implementing `ToolSet` with `@Tool`/`@ToolParam` annotations:

| Tool | Trigger phrase | What it does |
|---|---|---|
| `webSearch` | current events, news, weather | Brave Search API, returns top 3 snippets |
| `getCurrentDateTime` | what time is it | `LocalDateTime.now()` formatted |
| `getBatteryLevel` | battery status | `BatteryManager` level + charging state |
| `openApp` | open WhatsApp / Spotify etc. | Queries launcher activities, starts by package |
| `setAlarm` | set alarm for 7am | `AlarmClock.ACTION_SET_ALARM` intent |

`LiteRtLmEngine.kt`:
- Injects `OkHttpClient` via Hilt
- `_activeTool: MutableStateFlow<String?>` — set by `onToolActive` callback during tool execution
- `automaticToolCalling = true` in `ConversationConfig`

`app/build.gradle.kts`:
- `buildConfig = true` enabled
- `BRAVE_API_KEY` read from `local.properties` and embedded as `BuildConfig.BRAVE_API_KEY`

`AndroidManifest.xml` additions:
- `com.android.alarm.permission.SET_ALARM`
- `android.permission.QUERY_ALL_PACKAGES` — required on Android 11+ to see all installed apps in `queryIntentActivities`

### 8. Tool Use UI — ToolPill
`VoiceModeScreen.kt` — `ToolPill` composable shown when `activeTool != null`:
- `AnimatedVisibility` with slide-up + fade-in/out
- Pulsing cyan dot (infinite alpha animation)
- Tool name text in BrandCyan

`ChatViewModel.kt` — `activeTool: String?` added to `UiState`, collected from `engine.activeTool` in `init`.

`ChatScreen.kt` — passes `activeTool = uiState.activeTool` down to `VoiceModeScreen`.

### 9. openApp Fix — Package Visibility
**Bug:** `getInstalledApplications(0)` didn't return user-installed apps on Android 11+ due to package visibility restrictions.

**Fix:**
- Switched to `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER, 0)` — queries the exact same set as the app drawer
- Added `QUERY_ALL_PACKAGES` permission to manifest — lifts Android 11+ visibility filter

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
| Male TTS voice | ✅ Working |
| Tool calling (5 tools) | ✅ Working |
| Tool use UI (ToolPill overlay) | ✅ Working |
| openApp — user-installed apps discoverable | ✅ Fixed |
| Voice-activated barge-in | ❌ Removed (echo issue) |

---

## Files Changed This Session

| File | Change |
|---|---|
| `ui/chat/ChatViewModel.kt` | `interruptAndListen()`, `processingJob`, `autoListen` param, SPEAKING transition fix, `activeTool` in UiState |
| `ui/chat/ChatScreen.kt` | Orb tap routes SPEAKING → `interruptAndListen()`, passes `activeTool` to VoiceModeScreen |
| `audio/AudioCaptureManager.kt` | VOICE_COMMUNICATION source + AEC |
| `ui/voice/VoiceModeScreen.kt` | Full animation overhaul + ToolPill composable |
| `tts/AndroidTtsEngine.kt` | Male voice selection |
| `inference/LiteRtLmEngine.kt` | OkHttpClient injection, activeTool StateFlow, tool registration |
| `inference/JarvisToolSet.kt` | NEW — 5 tools with onToolActive callbacks |
| `app/build.gradle.kts` | buildConfig=true, BRAVE_API_KEY from local.properties |
| `app/src/main/AndroidManifest.xml` | SET_ALARM + QUERY_ALL_PACKAGES permissions |

---

## Next Candidates

### Barge-in (voice interrupt without tap)
The reliable way to implement this on Android without hardware echo cancellation issues:
- Use `AudioEffect` with `NoiseSuppressor` + `AcousticEchoCanceler` on the AudioRecord
- Or: detect barge-in using **energy delta** — only trigger if mic energy is significantly above the known TTS playback level (requires measuring TTS output level as a reference)
- Or: use Android's `MediaRecorder.AudioSource.VOICE_COMMUNICATION` with `MODE_IN_COMMUNICATION` audio mode set on `AudioManager` — this enables the full hardware voice processing stack including echo reference

### More Tools
- `sendWhatsAppMessage(contact, message)` — `Intent(Intent.ACTION_SEND)` with WhatsApp package
- `getCalendarEvents()` — `CalendarContract.Events` query
- `controlMedia(action)` — `AudioManager` or `MediaSessionManager` for play/pause/skip
- `setVolume(level)` — `AudioManager.setStreamVolume`

### Settings Screen
- TTS speed/pitch control
- VAD sensitivity slider
- Max response length

### Error Recovery
- Handle LiteRT-LM OOM gracefully (show message, offer to reload)
- Network-offline detection for model download
