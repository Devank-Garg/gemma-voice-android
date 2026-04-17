package com.example.gemmaapp.tts

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KokoroEngine @Inject constructor() {
    // Sprint 4: ONNX Runtime session for Kokoro v1.0 TTS (~82MB)
    // text → PCM FloatArray at target sample rate
    fun synthesize(text: String): FloatArray {
        TODO("Sprint 4")
    }
    fun close() { TODO("Sprint 4") }
}
