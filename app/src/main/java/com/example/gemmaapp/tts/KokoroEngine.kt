package com.example.gemmaapp.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KokoroEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SAMPLE_RATE = AudioPlayer.SAMPLE_RATE  // 24 000 Hz
        private const val STYLE_DIM = 256
        private const val MAX_TOKENS = 510
        private const val MODEL_FILE = "kokoro-v1.0.onnx"
        private const val VOICE_FILE = "voices/af_heart.bin"
    }

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var voiceStyles: FloatArray? = null  // flat: numStyles * STYLE_DIM
    private var numStyles: Int = 0

    val isReady: Boolean get() = session != null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        EnglishPhonemizer.loadDict(context.assets)

        val modelsDir = context.getExternalFilesDir(null)?.resolve("models")
            ?: error("External storage unavailable")

        val modelFile = modelsDir.resolve(MODEL_FILE)
        check(modelFile.exists()) { "Kokoro model not found: ${modelFile.absolutePath}" }

        val voiceFile = modelsDir.resolve(VOICE_FILE)
        check(voiceFile.exists()) { "Voice file not found: ${voiceFile.absolutePath}" }

        env = OrtEnvironment.getEnvironment()
        session = env!!.createSession(
            modelFile.absolutePath,
            OrtSession.SessionOptions()
        )

        loadVoice(voiceFile)
    }

    // Returns PCM float[] at 24 kHz
    suspend fun synthesize(text: String, speed: Float = 1.0f): FloatArray = withContext(Dispatchers.IO) {
        val sess = checkNotNull(session) { "KokoroEngine not initialized" }
        val env  = checkNotNull(env)

        val tokens = EnglishPhonemizer.phonemize(text)
        if (tokens.isEmpty()) return@withContext FloatArray(0)

        // Clamp to model max
        val clipped = if (tokens.size > MAX_TOKENS) tokens.copyOf(MAX_TOKENS) else tokens

        // input_ids: [[0, t1, t2, ..., tn, 0]]  shape (1, len+2)
        val paddedLen = clipped.size + 2
        val inputIds = Array(1) { LongArray(paddedLen) }
        inputIds[0][0] = 0L
        clipped.copyInto(inputIds[0], destinationOffset = 1)
        inputIds[0][paddedLen - 1] = 0L

        // style: shape (1, 256) — pick by token length
        val styleVec = getStyleVector(clipped.size)
        val style = Array(1) { styleVec }

        // speed: shape (1,)
        val speedArr = floatArrayOf(speed)

        val inputTensor = OnnxTensor.createTensor(env, inputIds)
        val styleTensor = OnnxTensor.createTensor(env, style)
        val speedTensor = OnnxTensor.createTensor(env, speedArr)

        return@withContext inputTensor.use { inp ->
            styleTensor.use { st ->
                speedTensor.use { sp ->
                    val inputs = mapOf(
                        "input_ids" to inp,
                        "style"     to st,
                        "speed"     to sp,
                    )
                    sess.run(inputs).use { out ->
                        @Suppress("UNCHECKED_CAST")
                        (out[0].value as Array<FloatArray>)[0]
                    }
                }
            }
        }
    }

    fun close() {
        session?.close(); session = null
        env?.close();     env = null
        voiceStyles = null
    }

    private fun loadVoice(file: File) {
        val bytes = file.readBytes()
        numStyles = bytes.size / (STYLE_DIM * 4)
        voiceStyles = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .let { buf -> FloatArray(buf.remaining()).also { buf.get(it) } }
    }

    private fun getStyleVector(tokenLen: Int): FloatArray {
        val styles = checkNotNull(voiceStyles)
        val idx = minOf(tokenLen, numStyles - 1).coerceAtLeast(0)
        return styles.copyOfRange(idx * STYLE_DIM, idx * STYLE_DIM + STYLE_DIM)
    }
}
