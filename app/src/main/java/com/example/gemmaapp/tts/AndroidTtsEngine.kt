package com.example.gemmaapp.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AndroidTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AndroidTtsEngine"
        private const val UTTERANCE_PREFIX = "utt_"
    }

    private var tts: TextToSpeech? = null

    // Drain tracking: pendingCount counts utterances queued but not yet done.
    // sealed flips to true after the last sentence is queued (no more coming).
    // When sealed=true AND pendingCount==0, drainCallback fires exactly once.
    private val pendingCount = AtomicInteger(0)
    private val sealed = AtomicBoolean(false)
    private val drainCallback = AtomicReference<(() -> Unit)?>(null)

    val isReady: Boolean get() = tts != null

    suspend fun initialize(): Unit = suspendCancellableCoroutine { cont ->
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine!!.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "US English not supported (result=$result)")
                    cont.resumeWithException(IllegalStateException("Android TTS: US English not supported"))
                } else {
                    tts = engine
                    setupProgressListener()
                    Log.i(TAG, "Android TTS ready")
                    cont.resume(Unit)
                }
            } else {
                Log.e(TAG, "Android TTS init failed (status=$status)")
                cont.resumeWithException(IllegalStateException("Android TTS init failed: status=$status"))
            }
        }
        cont.invokeOnCancellation { engine?.shutdown() }
    }

    // Queues text for immediate playback. Returns true if successfully queued.
    // Android TTS manages its own audio focus internally.
    fun speak(text: String): Boolean {
        val t = tts ?: run {
            Log.w(TAG, "speak() called before initialize()")
            return false
        }
        if (text.isBlank()) return false

        val utteranceId = "$UTTERANCE_PREFIX${System.nanoTime()}"
        pendingCount.incrementAndGet()
        t.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        Log.d(TAG, "queued: \"${text.take(60)}\"")
        return true
    }

    // Call after queuing the last sentence for a turn. Fires callback once the
    // TTS queue drains. Safe to call even if all utterances have already finished —
    // in that case callback fires immediately (pendingCount is already 0).
    fun sealQueue(callback: () -> Unit) {
        drainCallback.set(callback)
        sealed.set(true)
        // If all utterances already finished before we sealed, fire now.
        if (pendingCount.get() == 0) {
            drainCallback.getAndSet(null)?.invoke()
        }
    }

    fun stop() {
        drainCallback.set(null)
        sealed.set(false)
        pendingCount.set(0)
        tts?.stop()
        Log.d(TAG, "stopped")
    }

    fun close() {
        stop()
        tts?.shutdown()
        tts = null
        Log.i(TAG, "shut down")
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                val remaining = pendingCount.decrementAndGet()
                if (remaining == 0 && sealed.get()) {
                    drainCallback.getAndSet(null)?.invoke()
                }
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error on utterance: $utteranceId")
                // Decrement so we don't block the drain on a failed utterance.
                val remaining = pendingCount.decrementAndGet()
                if (remaining == 0 && sealed.get()) {
                    drainCallback.getAndSet(null)?.invoke()
                }
            }
        })
    }
}
