package com.example.gemmaapp.audio

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceActivityDetector @Inject constructor() {
    // Sprint 2: energy + zero-crossing rate detector
    // Emits SpeechStart, SpeechEnd(pcm clip), or Timeout
    fun detect(pcmChunks: Flow<FloatArray>): Flow<VadEvent> {
        TODO("Sprint 2")
    }
}
