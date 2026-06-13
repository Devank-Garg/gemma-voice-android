package com.example.gemmaapp.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class VoiceActivityDetector @Inject constructor() {

    companion object {
        private const val CALIBRATION_MS = 500L    // sample ambient noise before listening
        private const val SILENCE_MS     = 900L    // consecutive silence this long → end of speech
        private const val MIN_SPEECH_MS  = 200L    // discard clips shorter than this
        private const val MAX_SPEECH_MS  = 30_000L // Gemma 4 audio limit
        private const val MIN_THRESHOLD  = 0.01f   // floor so truly silent rooms don't false-trigger
        private const val SPEECH_RATIO   = 3.5f    // speech must be this many × louder than noise floor
    }

    fun detect(pcmChunks: Flow<FloatArray>): Flow<VadEvent> = flow {
        // ── Calibration phase ─────────────────────────────────────────────────
        // Collect ~500ms of audio before listening so we can measure the ambient
        // noise floor and derive a per-session adaptive energy threshold.
        var threshold = MIN_THRESHOLD
        var calibrated = false
        var calibrationEndMs = 0L
        val calibrationSamples = mutableListOf<Float>()

        // ── Detection state ───────────────────────────────────────────────────
        var speaking = false
        var speechStartMs = 0L
        var lastVoiceMs = 0L
        val accumulated = mutableListOf<FloatArray>()

        pcmChunks.collect { chunk ->
            val now = System.currentTimeMillis()

            // Start the calibration window on the very first chunk.
            if (calibrationEndMs == 0L) calibrationEndMs = now + CALIBRATION_MS

            if (!calibrated) {
                if (now < calibrationEndMs) {
                    chunk.forEach { calibrationSamples.add(it) }
                    return@collect
                }
                // Calibration window elapsed — compute adaptive threshold.
                if (calibrationSamples.isNotEmpty()) {
                    val ambientRms = sqrt(
                        calibrationSamples.fold(0.0) { acc, s -> acc + s * s }.toFloat()
                            / calibrationSamples.size
                    )
                    threshold = maxOf(MIN_THRESHOLD, ambientRms * SPEECH_RATIO)
                    calibrationSamples.clear()
                }
                calibrated = true
                // Fall through — process this chunk with the newly computed threshold.
            }

            // ── Detection ─────────────────────────────────────────────────────
            val energy = rms(chunk)

            if (energy > threshold) {
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

                if (now - speechStartMs >= MAX_SPEECH_MS) {
                    emit(VadEvent.SpeechEnd(flatten(accumulated)))
                    speaking = false
                    accumulated.clear()
                }
            } else if (speaking) {
                accumulated.add(chunk) // keep silence chunks so the audio is continuous
                if (now - lastVoiceMs >= SILENCE_MS) {
                    speaking = false
                    val duration = now - speechStartMs
                    if (duration >= MIN_SPEECH_MS) {
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
