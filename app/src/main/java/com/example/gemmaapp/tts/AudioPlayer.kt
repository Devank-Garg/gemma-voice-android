package com.example.gemmaapp.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayer @Inject constructor() {

    companion object {
        const val SAMPLE_RATE = 24000
    }

    private var track: AudioTrack? = null
    private var playJob: Job? = null

    fun play(pcmChunks: Flow<FloatArray>, scope: CoroutineScope, onDone: () -> Unit = {}) {
        stop()
        val bufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(8192)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }

        playJob = scope.launch(Dispatchers.IO) {
            try {
                pcmChunks.collect { chunk ->
                    track?.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                }
            } finally {
                track?.stop()
                onDone()
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        track?.pause()
        track?.flush()
        track?.release()
        track = null
    }
}
