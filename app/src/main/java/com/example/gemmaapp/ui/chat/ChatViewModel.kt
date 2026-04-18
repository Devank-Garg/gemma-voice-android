package com.example.gemmaapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemmaapp.audio.AudioCaptureManager
import com.example.gemmaapp.audio.VadEvent
import com.example.gemmaapp.audio.VoiceActivityDetector
import com.example.gemmaapp.data.model.ChatMessage
import com.example.gemmaapp.data.model.DownloadState
import com.example.gemmaapp.data.repository.ModelRepository
import com.example.gemmaapp.inference.LiteRtLmEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VoiceState { IDLE, LISTENING, RECORDING, PROCESSING, SPEAKING, ERROR }

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val engine: LiteRtLmEngine,
    private val modelRepository: ModelRepository,
    private val audioCaptureManager: AudioCaptureManager,
    private val vad: VoiceActivityDetector,
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
                _uiState.update { it.copy(engineState = EngineState.Ready, backendLabel = engine.activeBackend) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(engineState = EngineState.Error(e.message ?: "Engine init failed"))
                }
            }
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank() || _uiState.value.engineState !is EngineState.Ready) return

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

    fun startVoiceCapture() {
        _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
        audioCaptureManager.startCapture(viewModelScope)
        viewModelScope.launch {
            vad.detect(audioCaptureManager.pcmChunks()).collect { event ->
                when (event) {
                    VadEvent.SpeechStart -> _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
                    is VadEvent.SpeechEnd -> {
                        audioCaptureManager.stopCapture()
                        // Sprint 3: pass event.pcm to AudioTokenizer → engine
                        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
                    }
                    VadEvent.Timeout -> {
                        audioCaptureManager.stopCapture()
                        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
                    }
                }
            }
        }
    }

    fun stopVoiceCapture() {
        audioCaptureManager.stopCapture()
        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
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

    override fun onCleared() {
        super.onCleared()
        audioCaptureManager.stopCapture()
        engine.close()
    }
}
