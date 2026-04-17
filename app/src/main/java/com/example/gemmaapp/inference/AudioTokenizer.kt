package com.example.gemmaapp.inference

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioTokenizer @Inject constructor() {
    // Sprint 3: wraps PCM float[] into Gemma 4 multimodal audio prompt
    // Prompt format: "<start_of_turn>user\n<audio>\n<end_of_turn>\n<start_of_turn>model\n"
    // Audio spec: 16kHz, float32, mono, max 480_000 samples (30s)
    fun buildPrompt(systemPrompt: String = ""): String {
        TODO("Sprint 3")
    }
}
