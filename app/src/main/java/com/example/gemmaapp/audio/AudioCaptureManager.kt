package com.example.gemmaapp.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buffer: PcmBuffer
) {
    private val sampleRate = 16_000
    private val chunkSamples = 1_024   // ~64 ms per chunk at 16 kHz

    private val minBufferBytes = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_FLOAT
    ).coerceAtLeast(chunkSamples * Float.SIZE_BYTES * 4)

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope) {
        if (captureJob?.isActive == true) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            minBufferBytes
        ).also { it.startRecording() }

        captureJob = scope.launch(Dispatchers.IO) {
            val chunk = FloatArray(chunkSamples)
            while (isActive) {
                val read = audioRecord?.read(chunk, 0, chunkSamples, AudioRecord.READ_BLOCKING) ?: -1
                if (read > 0) buffer.write(chunk.copyOf(read))
            }
        }
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun pcmChunks() = buffer.chunks

    val isRecording: Boolean get() = captureJob?.isActive == true
}
