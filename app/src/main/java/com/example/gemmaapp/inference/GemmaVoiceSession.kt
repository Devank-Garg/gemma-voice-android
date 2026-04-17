package com.example.gemmaapp.inference

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaVoiceSession @Inject constructor(
    private val engine: LiteRtLmEngine,
    private val tokenizer: AudioTokenizer
) {
    // Sprint 3: takes a VAD-captured PCM clip, returns streaming text tokens
    fun process(pcm: FloatArray): Flow<String> {
        TODO("Sprint 3")
    }
}
