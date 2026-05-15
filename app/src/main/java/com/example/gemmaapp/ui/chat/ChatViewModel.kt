package com.example.gemmaapp.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemmaapp.audio.AudioCaptureManager
import com.example.gemmaapp.data.model.ChatMessage
import com.example.gemmaapp.data.model.DownloadState
import com.example.gemmaapp.data.repository.ModelRepository
import com.example.gemmaapp.inference.LiteRtLmEngine
import com.example.gemmaapp.tts.AudioPlayer
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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

enum class VoiceState { IDLE, LISTENING, RECORDING, PROCESSING, SPEAKING, ERROR }

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val engine: LiteRtLmEngine,
    private val modelRepository: ModelRepository,
    private val audioCaptureManager: AudioCaptureManager,
    private val ttsSynthesizer: TtsSynthesizer,
    private val audioPlayer: AudioPlayer,
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
        val debugStatus: String = "",
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var inactivityJob: Job? = null
    private var vadJob: Job? = null

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
                // Kokoro init is fast (~200ms) — run alongside LLM init
                try { ttsSynthesizer.initializeEngine() } catch (e: Exception) {
                    android.util.Log.w("ChatViewModel", "Kokoro init failed: ${e.message}")
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

    fun startVoiceCapture() {
        vadJob?.cancel()
        resetInactivityTimer()
        _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
        audioCaptureManager.startCapture(viewModelScope)
        vadJob = viewModelScope.launch {
            // Fixed 2-second capture window — bypasses VAD for reliable pipeline testing.
            // Swap back to VAD-based detect() once the full pipeline is confirmed working.
            val chunks = mutableListOf<FloatArray>()
            val collectJob = launch {
                audioCaptureManager.pcmChunks().collect { chunk -> chunks.add(chunk) }
            }
            delay(CAPTURE_DURATION_MS)
            collectJob.cancel()
            audioCaptureManager.stopCapture()

            val pcm = chunks.flatMap { it.toList() }.toFloatArray()
            android.util.Log.i("ChatVM", "Timed capture done: ${pcm.size} samples (${pcm.size / 16000f}s)")
            if (pcm.isNotEmpty()) processVoiceInput(pcm)
            else _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
        }
    }

    fun stopVoiceCapture() {
        vadJob?.cancel()
        vadJob = null
        audioCaptureManager.stopCapture()
        audioPlayer.stop()
        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
    }

    private fun processVoiceInput(pcm: FloatArray) {
        if (_uiState.value.engineState !is EngineState.Ready) {
            android.util.Log.w("ChatVM", "processVoiceInput: engine not ready, skipping")
            return
        }
        resetInactivityTimer()
        android.util.Log.i("ChatVM", "processVoiceInput: ${pcm.size} samples (${pcm.size / 16000f}s)")

        val placeholder = ChatMessage(role = ChatMessage.Role.ASSISTANT, text = "", isStreaming = true)
        _uiState.update { it.copy(messages = it.messages, voiceState = VoiceState.PROCESSING) }

        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            var tokenCount = 0
            var accumulated = ""

            try {
                // Share the token flow so TTS and UI can both consume it.
                // replay = 512 ensures the UI collector sees tokens emitted before it subscribes.
                val tokenFlow = engine.sendAudio(pcm)
                    .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 512)

                _uiState.update { it.copy(messages = it.messages + placeholder, voiceState = VoiceState.SPEAKING) }

                // Start TTS streaming playback
                audioPlayer.play(
                    pcmChunks = ttsSynthesizer.synthesizeStream(tokenFlow),
                    scope = viewModelScope,
                    onDone = { _uiState.update { it.copy(voiceState = VoiceState.IDLE) } }
                )

                // Simultaneously update chat UI with streaming text
                tokenFlow.collect { chunk ->
                    accumulated += chunk
                    tokenCount++
                    val tps = tokenCount / ((System.currentTimeMillis() - startMs) / 1000f).coerceAtLeast(0.001f)
                    patchStreamingMessage(accumulated, tokenCount, tps)
                }

                finalizeAssistantMessage(accumulated, tokenCount, startMs)
                android.util.Log.i("ChatVM", "Voice response done: $tokenCount tokens")
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

    fun recordDebugAudio() {
        if (_uiState.value.debugStatus == "Recording…") return
        val hasPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            androidx.core.content.ContextCompat.checkSelfPermission(
                appContext, android.Manifest.permission.RECORD_AUDIO
            )
        if (!hasPermission) {
            _uiState.update { it.copy(debugStatus = "Need mic permission first") }
            return
        }
        _uiState.update { it.copy(debugStatus = "Recording…") }
        viewModelScope.launch {
            try {
                audioCaptureManager.startCapture(viewModelScope)
                // Collect ~5 s of audio (5000 ms / 64 ms per chunk ≈ 78 chunks)
                val chunks = mutableListOf<FloatArray>()
                audioCaptureManager.pcmChunks()
                    .take(78)
                    .collect { chunk -> chunks.add(chunk) }
                audioCaptureManager.stopCapture()

                val allSamples = chunks.flatMap { it.toList() }.toFloatArray()
                val outFile = appContext.getExternalFilesDir(null)?.resolve("debug_audio.wav")
                if (outFile == null) {
                    _uiState.update { it.copy(debugStatus = "ERROR: no storage") }
                    return@launch
                }
                withContext(Dispatchers.IO) { writeWav(outFile, allSamples, sampleRate = 16_000) }
                _uiState.update { it.copy(debugStatus = "Saved (${allSamples.size} samples)") }
                android.util.Log.i("DebugAudio", "Saved ${allSamples.size} samples → ${outFile.absolutePath}")
            } catch (e: Exception) {
                audioCaptureManager.stopCapture()
                _uiState.update { it.copy(debugStatus = "ERROR: ${e.message}") }
                android.util.Log.e("DebugAudio", "Recording failed", e)
            }
        }
    }

    private fun writeWav(file: File, samples: FloatArray, sampleRate: Int) {
        val dataBytes = samples.size * 4
        val totalBytes = 44 + dataBytes
        val buf = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF header
        buf.put("RIFF".toByteArray())
        buf.putInt(totalBytes - 8)
        buf.put("WAVE".toByteArray())
        // fmt chunk (IEEE float PCM)
        buf.put("fmt ".toByteArray())
        buf.putInt(16)          // chunk size
        buf.putShort(3)         // format = IEEE_FLOAT
        buf.putShort(1)         // channels
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 4) // byte rate
        buf.putShort(4)         // block align
        buf.putShort(32)        // bits per sample
        // data chunk
        buf.put("data".toByteArray())
        buf.putInt(dataBytes)
        samples.forEach { buf.putFloat(it) }
        file.writeBytes(buf.array())
    }

    override fun onCleared() {
        super.onCleared()
        vadJob?.cancel()
        inactivityJob?.cancel()
        audioCaptureManager.stopCapture()
        audioPlayer.stop()
        engine.close()
        ttsSynthesizer.closeEngine()
    }

    companion object {
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
        private const val CAPTURE_DURATION_MS = 2_000L             // fixed recording window
    }
}
