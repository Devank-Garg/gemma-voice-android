package com.example.gemmaapp.tts

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsSynthesizer @Inject constructor(
    private val engine: KokoroEngine
) {
    // Sprint 4: splits token stream at sentence boundaries (. ? ! or ~80 tokens)
    // synthesizes each sentence and plays via AudioTrack — first chunk before LLM finishes
    fun synthesizeStream(textTokens: Flow<String>): Flow<FloatArray> {
        TODO("Sprint 4")
    }
}
