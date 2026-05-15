package com.example.gemmaapp.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsSynthesizer @Inject constructor(
    private val engine: KokoroEngine
) {
    suspend fun initializeEngine() = engine.initialize()
    fun closeEngine() = engine.close()

    fun synthesizeStream(textTokens: Flow<String>): Flow<FloatArray> = channelFlow {
        val sentences = Channel<String>(capacity = Channel.BUFFERED)

        val synthJob = launch(Dispatchers.IO) {
            for (sentence in sentences) {
                val pcm = engine.synthesize(sentence)
                if (pcm.isNotEmpty()) send(pcm)
            }
        }

        val buf = StringBuilder()
        var firstSent = false

        textTokens.collect { token ->
            buf.append(token)

            if (!firstSent) {
                // Fire the first synthesis call after 3 words, regardless of punctuation,
                // so audio starts within the first ~300ms of LLM output.
                val enoughWords = buf.count { it == ' ' } >= 2   // ≥ 3 words
                val hasBoundary = buf.any { it == '.' || it == '!' || it == '?' || it == '\n' }
                if (enoughWords || hasBoundary) {
                    sentences.send(buf.toString().trim())
                    buf.clear()
                    firstSent = true
                }
            } else {
                for (s in flushSentences(buf)) sentences.send(s)
            }
        }

        val tail = buf.toString().trim()
        if (tail.isNotEmpty()) sentences.send(tail)
        sentences.close()
        synthJob.join()
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
