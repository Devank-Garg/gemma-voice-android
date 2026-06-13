package com.example.gemmaapp.tts

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsSynthesizer @Inject constructor(
    private val engine: AndroidTtsEngine
) {
    suspend fun initializeEngine() = engine.initialize()

    fun closeEngine() = engine.close()

    fun stop() = engine.stop()

    // Speaks a single system message immediately, interrupting any current speech.
    fun announce(text: String) {
        engine.stop()
        engine.speak(text)
        engine.sealQueue { }
    }

    // Collects the LLM token flow and pipes sentence chunks to Android TTS for immediate playback.
    // onDone fires after the last synthesized sentence finishes playing through the speaker.
    // sendMessageAsync never closes its flow, so a watchdog exits after TOKEN_IDLE_MS of silence.
    suspend fun synthesizeAndPlay(textTokens: Flow<String>, onDone: () -> Unit) {
        val buf = StringBuilder()
        var firstSent = false
        var anySent = false
        var lastTokenMs = 0L          // 0 = no token yet; watchdog ignores until first token
        var firstTokenReceived = false

        fun queue(text: String) {
            if (engine.speak(text)) anySent = true
        }

        coroutineScope {
            val collectJob = launch {
                textTokens.collect { token ->
                    lastTokenMs = System.currentTimeMillis()
                    firstTokenReceived = true
                    buf.append(token)

                    if (!firstSent) {
                        val enoughWords = buf.count { it == ' ' } >= 2
                        val hasBoundary = buf.any { it == '.' || it == '!' || it == '?' || it == '\n' }
                        if (enoughWords || hasBoundary) {
                            queue(buf.toString().trim())
                            buf.clear()
                            firstSent = true
                        }
                    } else {
                        for (s in flushSentences(buf)) queue(s)
                    }
                }
            }

            // Wait for first token before counting idle time (LLM may take seconds to start).
            // Then exit once TOKEN_IDLE_MS passes with no new token — model stopped generating.
            while (!firstTokenReceived || System.currentTimeMillis() - lastTokenMs < TOKEN_IDLE_MS) {
                delay(150)
            }
            collectJob.cancel()
        }

        val tail = buf.toString().trim()
        if (tail.isNotEmpty()) queue(tail)

        if (anySent) engine.sealQueue(onDone) else onDone()
    }

    companion object {
        private const val TOKEN_IDLE_MS = 1200L
    }

    private fun flushSentences(buf: StringBuilder): List<String> {
        val text = buf.toString()
        val results = mutableListOf<String>()
        var last = 0

        for (i in text.indices) {
            val ch = text[i]
            val hard = ch == '.' || ch == '!' || ch == '?' || ch == '\n'
            val soft = (ch == ',' || ch == ';' || ch == ':') && (i - last >= 20)
            if (hard || soft) {
                val s = text.substring(last, i + 1).trim()
                if (s.isNotEmpty()) results.add(s)
                last = i + 1
            }
        }

        buf.clear()
        if (last < text.length) buf.append(text.substring(last))
        return results
    }
}
