package com.example.gemmaapp.inference

import android.content.Context
import com.google.ai.edge.litertlm.Backend
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
            cacheDir = context.cacheDir.path
        )
        engine = Engine(config).also { it.initialize() }
        conversation = engine!!.createConversation(buildConversationConfig())
    }

    fun sendMessage(text: String): Flow<String> {
        val conv = checkNotNull(conversation) { "Engine not initialized" }
        return conv.sendMessageAsync(text).map { it.toString() }
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
            "You are Gemma, a helpful voice assistant running fully on-device. " +
            "Keep answers concise and conversational."
        ),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8)
    )
}
