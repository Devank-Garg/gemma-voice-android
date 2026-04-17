package com.example.gemmaapp.ui.chat

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

enum class VoiceState { IDLE, LISTENING, RECORDING, PROCESSING, SPEAKING, ERROR }

// Sprint 5: orchestrates Audio → Inference → TTS state machine
@HiltViewModel
class ChatViewModel @Inject constructor() : ViewModel() {

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState
}
