package com.example.gemmaapp.inference

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtLmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    var activeBackend: String = "CPU"
        private set

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        val backend = try {
            Backend.GPU().also {
                activeBackend = "GPU"
                android.util.Log.i("LiteRtLm", "Backend: GPU")
            }
        } catch (e: Exception) {
            android.util.Log.w("LiteRtLm", "GPU failed (${e.message}), using CPU")
            activeBackend = "CPU"
            Backend.CPU()
        }
        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            audioBackend = Backend.CPU(),
            cacheDir = context.cacheDir.path
        )
        engine = Engine(config).also { it.initialize() }
        conversation = engine!!.createConversation(buildConversationConfig())
    }

    fun sendMessage(text: String): Flow<String> {
        val conv = checkNotNull(conversation) { "Engine not initialized" }
        return conv.sendMessageAsync(text).map { it.toString() }
    }

    fun sendAudio(pcm: FloatArray): Flow<String> {
        val conv = checkNotNull(conversation) { "Engine not initialized" }
        val wavBytes = buildWav(pcm, sampleRate = 16_000)
        android.util.Log.i("LiteRtLm", "sendAudio: ${pcm.size} samples → ${wavBytes.size} WAV bytes")
        val contents = Contents.of(
            Content.AudioBytes(wavBytes),
            Content.Text("Please respond to what was said.")
        )
        return conv.sendMessageAsync(contents).map { it.toString() }
    }

    // Wraps float32 PCM in a standard 16-bit PCM WAV container that LiteRT-LM understands
    private fun buildWav(samples: FloatArray, sampleRate: Int): ByteArray {
        val dataBytes = samples.size * 2   // int16 = 2 bytes per sample
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)                      // fmt chunk size
        buf.putShort(1)                     // PCM
        buf.putShort(1)                     // mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)          // byteRate
        buf.putShort(2)                     // blockAlign
        buf.putShort(16)                    // bitsPerSample
        buf.put("data".toByteArray())
        buf.putInt(dataBytes)
        samples.forEach { s ->
            buf.putShort((s * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        return buf.array()
    }

    fun resetConversation() {
        conversation?.close()
        conversation = engine?.createConversation(buildConversationConfig())
    }

    val isReady: Boolean get() = conversation != null

    fun close() {
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }

    private fun buildConversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(
            "You are J.A.R.V.I.S. (Just A Rather Very Intelligent System), a highly advanced " +
            "on-device AI assistant inspired by Iron Man. You are precise, articulate, and " +
            "occasionally witty. Address the user as 'sir' unless told otherwise. " +
            "Your responses are spoken aloud, so keep them concise and conversational — " +
            "no bullet points, no markdown, no long essays. You run entirely on-device " +
            "with no cloud connectivity. If asked who you are, identify yourself as J.A.R.V.I.S."
        ),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8)
    )
}
