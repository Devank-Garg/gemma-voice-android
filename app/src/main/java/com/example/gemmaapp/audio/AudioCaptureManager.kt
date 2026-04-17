package com.example.gemmaapp.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buffer: PcmBuffer
) {
    // Sprint 2: AudioRecord at 16kHz, ENCODING_PCM_FLOAT, mono
    // Writes chunks into PcmBuffer
    fun startCapture() { TODO("Sprint 2") }
    fun stopCapture() { TODO("Sprint 2") }
}
