package com.example.gemmaapp.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class VoiceActivityDetector @Inject constructor() {

    private val energyThreshold = 0.005f
    private val silenceMs = 800L       // silence this long → end of speech
    private val minSpeechMs = 150L     // discard clips shorter than this
    private val maxSpeechMs = 30_000L  // Gemma 4 audio limit

    fun detect(pcmChunks: Flow<FloatArray>): Flow<VadEvent> = flow {
        var speaking = false
        var speechStartMs = 0L
        var lastVoiceMs = 0L
        val accumulated = mutableListOf<FloatArray>()

        pcmChunks.collect { chunk ->
            val energy = rms(chunk)
            val now = System.currentTimeMillis()

            if (energy > energyThreshold) {
                if (!speaking) {
                    speaking = true
                    speechStartMs = now
                    lastVoiceMs = now
                    accumulated.clear()
                    emit(VadEvent.SpeechStart)
                } else {
                    lastVoiceMs = now
                }
                accumulated.add(chunk)

                if (now - speechStartMs >= maxSpeechMs) {
                    emit(VadEvent.SpeechEnd(flatten(accumulated)))
                    speaking = false
                    accumulated.clear()
                }
            } else if (speaking) {
                accumulated.add(chunk)
                if (now - lastVoiceMs >= silenceMs) {
                    speaking = false
                    if (now - speechStartMs >= minSpeechMs) {
                        emit(VadEvent.SpeechEnd(flatten(accumulated)))
                    } else {
                        emit(VadEvent.Timeout)
                    }
                    accumulated.clear()
                }
            }
        }
    }

    private fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        return sqrt(samples.fold(0.0) { acc, s -> acc + s * s }.toFloat() / samples.size)
    }

    private fun flatten(chunks: List<FloatArray>): FloatArray {
        val out = FloatArray(chunks.sumOf { it.size })
        var offset = 0
        chunks.forEach { chunk -> chunk.copyInto(out, offset); offset += chunk.size }
        return out
    }
}
