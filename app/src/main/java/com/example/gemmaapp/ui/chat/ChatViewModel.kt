package com.example.gemmaapp.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemmaapp.audio.AudioCaptureManager
import com.example.gemmaapp.audio.VadEvent
import com.example.gemmaapp.audio.VoiceActivityDetector
import com.example.gemmaapp.data.model.ChatMessage
import com.example.gemmaapp.data.model.DownloadState
import com.example.gemmaapp.data.repository.ModelRepository
import com.example.gemmaapp.inference.LiteRtLmEngine
import com.example.gemmaapp.tts.TtsSynthesizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VoiceState { IDLE, LISTENING, RECORDING, PROCESSING, SPEAKING, ERROR }

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val engine: LiteRtLmEngine,
    private val modelRepository: ModelRepository,
    private val audioCaptureManager: AudioCaptureManager,
    private val vad: VoiceActivityDetector,
    private val ttsSynthesizer: TtsSynthesizer,
) : ViewModel() {

    sealed class EngineState {
        object Idle : EngineState()
        object Loading : EngineState()
        object Ready : EngineState()
        data class Error(val message: String) : EngineState()
    }

    data class UiState(
        val messages: List<ChatMessage> = emptyList(),
        val engineState: EngineState = EngineState.Idle,
        val voiceState: VoiceState = VoiceState.IDLE,
        val inputText: String = "",
        val isKeyboardMode: Boolean = false,
        val backendLabel: String = "",
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var inactivityJob: Job? = null
    private var vadJob: Job? = null
    private var processingJob: Job? = null

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            engine.close()
            _uiState.update { it.copy(engineState = EngineState.Idle, backendLabel = "") }
        }
    }

    init {
        viewModelScope.launch {
            val path = modelRepository.getModelPath()
            if (path != null) {
                loadEngine(path)
            } else {
                modelRepository.observeDownloadState().collect { state ->
                    if (state is DownloadState.Complete) {
                        modelRepository.getModelPath()?.let { loadEngine(it) }
                    }
                }
            }
        }
    }

    private fun loadEngine(modelPath: String) {
        val current = _uiState.value.engineState
        if (current is EngineState.Loading || current is EngineState.Ready) return
        viewModelScope.launch {
            _uiState.update { it.copy(engineState = EngineState.Loading) }
            try {
                engine.initialize(modelPath)
                // Android TTS init — fast, runs alongside LLM init
                try { ttsSynthesizer.initializeEngine() } catch (e: Exception) {
                    android.util.Log.w("ChatViewModel", "TTS init failed: ${e.message}")
                }
                _uiState.update { it.copy(engineState = EngineState.Ready, backendLabel = engine.activeBackend) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(engineState = EngineState.Error(e.message ?: "Engine init failed"))
                }
            }
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        // Model was unloaded due to inactivity — reload it; user retries after Ready
        if (_uiState.value.engineState is EngineState.Idle) {
            viewModelScope.launch { modelRepository.getModelPath()?.let { loadEngine(it) } }
            return
        }
        if (_uiState.value.engineState !is EngineState.Ready) return
        resetInactivityTimer()

        val userMsg = ChatMessage(role = ChatMessage.Role.USER, text = text.trim())
        val placeholder = ChatMessage(role = ChatMessage.Role.ASSISTANT, text = "", isStreaming = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + placeholder,
                inputText = "",
                voiceState = VoiceState.PROCESSING,
            )
        }

        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            var tokenCount = 0
            var accumulated = ""

            engine.sendMessage(text.trim())
                .catch {
                    finalizeAssistantMessage(accumulated, tokenCount, startMs)
                    _uiState.update { it.copy(voiceState = VoiceState.ERROR) }
                }
                .collect { chunk ->
                    accumulated += chunk
                    tokenCount++
                    val tps = tokenCount / ((System.currentTimeMillis() - startMs) / 1000f).coerceAtLeast(0.001f)
                    patchStreamingMessage(accumulated, tokenCount, tps)
                }

            finalizeAssistantMessage(accumulated, tokenCount, startMs)
            _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
        }
    }

    fun startVoiceCapture(autoListen: Boolean = false) {
        vadJob?.cancel()
        resetInactivityTimer()
        // Show LISTENING immediately so the waveform appears while VAD calibrates.
        _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
        audioCaptureManager.startCapture(viewModelScope)
        vadJob = viewModelScope.launch {
            var speechDetected = false

            val noSpeechJob = launch {
                delay(NO_SPEECH_TIMEOUT_MS)
                if (!speechDetected) {
                    audioCaptureManager.stopCapture()
                    if (autoListen) {
                        // Silently go idle — user didn't speak, no need to narrate it
                        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
                    } else {
                        val text = "I didn't catch that — could you tap the mic and try again?"
                        val msg = ChatMessage(role = ChatMessage.Role.ASSISTANT, text = text, isStreaming = false)
                        _uiState.update { it.copy(messages = it.messages + msg, voiceState = VoiceState.IDLE) }
                        ttsSynthesizer.announce(text)
                    }
                    vadJob?.cancel()
                }
            }

            try {
                vad.detect(audioCaptureManager.pcmChunks()).collect { event ->
                    when (event) {
                        is VadEvent.SpeechStart -> {
                            speechDetected = true
                            noSpeechJob.cancel()
                            android.util.Log.i("ChatVM", "VAD: speech started")
                        }
                        is VadEvent.SpeechEnd -> {
                            noSpeechJob.cancel()
                            audioCaptureManager.stopCapture()
                            android.util.Log.i("ChatVM", "VAD: speech end, ${event.pcm.size} samples (${event.pcm.size / 16000f}s)")
                            vadJob?.cancel()
                            processVoiceInput(event.pcm)
                        }
                        is VadEvent.Timeout -> {
                            noSpeechJob.cancel()
                            audioCaptureManager.stopCapture()
                            android.util.Log.i("ChatVM", "VAD: clip too short, returning to idle")
                            _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
                        }
                    }
                }
            } finally {
                noSpeechJob.cancel()
            }
        }
    }

    fun stopVoiceCapture() {
        vadJob?.cancel()
        vadJob = null
        audioCaptureManager.stopCapture()
        ttsSynthesizer.stop()
        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
    }

    fun interruptAndListen() {
        ttsSynthesizer.stop()
        processingJob?.cancel()
        processingJob = null
        vadJob?.cancel()
        vadJob = null
        audioCaptureManager.stopCapture()
        finalizeAllStreamingMessages()
        startVoiceCapture()
    }

    private fun processVoiceInput(pcm: FloatArray) {
        if (_uiState.value.engineState !is EngineState.Ready) {
            android.util.Log.w("ChatVM", "processVoiceInput: engine not ready, skipping")
            return
        }
        // Defensive: finalize any previous turn's message that got stuck streaming
        // (LiteRT-LM sendMessageAsync is a hot stream that never emits completion,
        // so the tokenFlow.collect loop below won't return — finalization must happen
        // via the TTS onDone callback instead).
        finalizeAllStreamingMessages()

        resetInactivityTimer()
        android.util.Log.i("ChatVM", "processVoiceInput: ${pcm.size} samples (${pcm.size / 16000f}s)")

        val placeholder = ChatMessage(role = ChatMessage.Role.ASSISTANT, text = "", isStreaming = true)
        _uiState.update { it.copy(messages = it.messages + placeholder, voiceState = VoiceState.PROCESSING) }

        processingJob = viewModelScope.launch {
            var startMs = 0L   // reset on first token so tok/s excludes audio-processing latency
            var tokenCount = 0
            var accumulated = ""

            try {
                val tokenFlow = engine.sendAudio(pcm)
                    .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 512)

                // Pipe tokens → Android TTS. onDone is the reliable completion signal
                // because sendMessageAsync never terminates the flow. We finalize the
                // message here, after all tokens have been queued for TTS playback.
                launch {
                    ttsSynthesizer.synthesizeAndPlay(tokenFlow) {
                        finalizeAssistantMessage(accumulated, tokenCount, startMs)
                        startVoiceCapture(autoListen = true) // auto-listen after each response
                    }
                }

                // Flip to SPEAKING on first token — stays PROCESSING until LLM actually
                // starts generating, so the user sees the right state during TTFT latency.
                tokenFlow.collect { chunk ->
                    if (startMs == 0L) {
                        startMs = System.currentTimeMillis()
                        _uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
                    }
                    accumulated += chunk
                    tokenCount++
                    val tps = tokenCount / ((System.currentTimeMillis() - startMs) / 1000f).coerceAtLeast(0.001f)
                    patchStreamingMessage(accumulated, tokenCount, tps)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatVM", "processVoiceInput failed", e)
                finalizeAssistantMessage(accumulated.ifEmpty { "[Error: ${e.message}]" }, tokenCount, startMs)
                _uiState.update { it.copy(voiceState = VoiceState.ERROR) }
            }
        }
    }

    fun updateInput(text: String) = _uiState.update { it.copy(inputText = text) }

    fun toggleKeyboardMode() = _uiState.update { it.copy(isKeyboardMode = !it.isKeyboardMode) }

    fun clearConversation() {
        engine.resetConversation()
        _uiState.update { it.copy(messages = emptyList(), voiceState = VoiceState.IDLE) }
    }

    private fun patchStreamingMessage(text: String, tokens: Int, tps: Float) {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val idx = msgs.indexOfLast { it.role == ChatMessage.Role.ASSISTANT && it.isStreaming }
            if (idx >= 0) msgs[idx] = msgs[idx].copy(text = text, tokenCount = tokens, tokensPerSecond = tps)
            state.copy(messages = msgs)
        }
    }

    private fun finalizeAssistantMessage(text: String, tokens: Int, startMs: Long) {
        val tps = tokens / ((System.currentTimeMillis() - startMs) / 1000f).coerceAtLeast(0.001f)
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val idx = msgs.indexOfLast { it.role == ChatMessage.Role.ASSISTANT }
            if (idx >= 0) msgs[idx] = msgs[idx].copy(
                text = text, isStreaming = false, tokenCount = tokens, tokensPerSecond = tps
            )
            state.copy(messages = msgs)
        }
    }

    // Clears isStreaming on every assistant message. Called at the start of a new
    // voice turn to unstick any message whose finalization was skipped because
    // the LiteRT-LM flow never emitted a completion signal.
    private fun finalizeAllStreamingMessages() {
        _uiState.update { state ->
            val msgs = state.messages.map { msg ->
                if (msg.role == ChatMessage.Role.ASSISTANT && msg.isStreaming)
                    msg.copy(isStreaming = false)
                else msg
            }
            state.copy(messages = msgs)
        }
    }


    override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
        vadJob?.cancel()
        inactivityJob?.cancel()
        audioCaptureManager.stopCapture()
        ttsSynthesizer.stop()
        engine.close()
        ttsSynthesizer.closeEngine()
    }

    companion object {
        private const val INACTIVITY_TIMEOUT_MS  = 10 * 60 * 1000L // 10 minutes
        private const val NO_SPEECH_TIMEOUT_MS   = 8_000L          // give up listening after 8 s of silence
    }
}
