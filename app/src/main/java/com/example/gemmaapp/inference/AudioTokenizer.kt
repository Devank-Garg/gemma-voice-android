package com.example.gemmaapp.inference

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioTokenizer @Inject constructor() {

    // Gemma 4 audio spec: 16 kHz, float32 LE, mono, max 480 000 samples (30 s)
    fun toAudioBytes(pcm: FloatArray): ByteArray =
        ByteBuffer.allocate(pcm.size * 4).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { putFloat(it) }
        }.array()
}
