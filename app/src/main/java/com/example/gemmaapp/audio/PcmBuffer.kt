package com.example.gemmaapp.audio

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PcmBuffer @Inject constructor() {
    // Sprint 2: ring buffer that emits 16kHz float PCM chunks
    private val _chunks = MutableSharedFlow<FloatArray>(extraBufferCapacity = 64)
    val chunks: SharedFlow<FloatArray> = _chunks

    suspend fun write(chunk: FloatArray) { _chunks.emit(chunk) }
}
