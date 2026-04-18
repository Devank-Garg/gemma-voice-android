# LiteRT-LM Android — API Reference

Source: https://ai.google.dev/edge/litert-lm/android

---

## Gradle Dependency

```kotlin
// Android
implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

// JVM (Linux / macOS / Windows)
implementation("com.google.ai.edge.litertlm:litertlm-jvm:latest.release")
```

---

## Engine Initialization

### Key Classes
| Class | Role |
|---|---|
| `Engine` | Main entry point |
| `EngineConfig` | Configuration holder |
| `Backend` | Compute backend (CPU / GPU / NPU) |

### Basic Init (GPU)
```kotlin
val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.GPU(),
    cacheDir = context.cacheDir.path   // Android only
)
val engine = Engine(engineConfig)
engine.initialize()   // blocks ~5-10s — always call on a background thread
// ...
engine.close()        // release native resources
```

### AndroidManifest.xml — GPU Requirements
```xml
<uses-native-library android:name="libvndksupport.so" android:required="false"/>
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
```

### NPU Backend
```kotlin
backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
```

### CPU Backend
```kotlin
backend = Backend.CPU()
```

---

## Conversation Management

### Key Classes
| Class | Role |
|---|---|
| `Conversation` | Manages message exchanges (implements `Closeable`) |
| `ConversationConfig` | Customization options |
| `Message` | Wraps a single message |
| `Contents` | Builder for multi-part content |
| `SamplerConfig` | Temperature / TopK / TopP sampling |

### Creating a Conversation
```kotlin
val conversationConfig = ConversationConfig(
    systemInstruction = Contents.of("You are a helpful assistant."),
    initialMessages = listOf(
        Message.user("What is the capital of the United States?"),
        Message.model("Washington, D.C.")
    ),
    samplerConfig = SamplerConfig(topK = 10, topP = 0.95f, temperature = 0.8f)
)

engine.createConversation(conversationConfig).use { conversation ->
    // interact here
}
```

---

## Sending Messages

### 1 — Synchronous
```kotlin
val response: String = conversation.sendMessage("What is the capital of France?")
print(response)
```

### 2 — Async with Callback
```kotlin
val callback = object : MessageCallback {
    override fun onMessage(message: Message) { print(message) }
    override fun onDone() { }
    override fun onError(throwable: Throwable) { }
}
conversation.sendMessageAsync("What is the capital of France?", callback)
```

### 3 — Async with Flow (Coroutines) — preferred for Compose
```kotlin
conversation.sendMessageAsync("What is the capital of France?")
    .catch { e -> /* handle error */ }
    .collect { message -> print(message.toString()) }
```

---

## Multi-Modal Support

Requires a model with multi-modal capability (e.g., Gemma 3n, Gemma 4 with audio/vision).

### EngineConfig for Multi-Modal
```kotlin
val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.CPU(),
    visionBackend = Backend.GPU(),
    audioBackend = Backend.CPU()
)
```

### Sending Mixed Content
```kotlin
conversation.sendMessage(
    Contents.of(
        Content.ImageFile("/path/to/image.jpg"),
        Content.AudioBytes(audioBytes),
        Content.Text("Describe this image and audio.")
    )
)
```

### Content Types
| Type | Constructor |
|---|---|
| Text | `Content.Text(string)` |
| Image (bytes) | `Content.ImageBytes(byteArray)` |
| Image (file) | `Content.ImageFile(path)` |
| Audio (bytes) | `Content.AudioBytes(byteArray)` |
| Audio (file) | `Content.AudioFile(path)` |

---

## Tool / Function Calling

### Method 1 — Kotlin ToolSet (Recommended)

```kotlin
class SampleToolSet : ToolSet {

    @Tool(description = "Get the current weather for a city")
    fun getCurrentWeather(
        @ToolParam(description = "The city name") city: String,
        @ToolParam(description = "Optional country code") country: String? = null,
        @ToolParam(description = "Temperature unit") unit: String = "celsius"
    ): Map<String, Any> {
        return mapOf("temperature" to 25, "unit" to unit, "condition" to "Sunny")
    }

    @Tool(description = "Get the sum of a list of numbers.")
    fun sum(@ToolParam(description = "The numbers") numbers: List<Double>): Double {
        return numbers.sum()
    }
}
```

**Supported parameter types:** `String`, `Int`, `Boolean`, `Float`, `Double`, or `List<T>` of these.
Use nullable types (`String?`) for optional params.
**Return types:** any Kotlin type — `List` → JSON array, `Map` → JSON object, primitives → JSON primitives.

### Method 2 — OpenAPI Specification

```kotlin
class SampleOpenApiTool : OpenApiTool {

    override fun getToolDescriptionJsonString(): String = """
        {
          "name": "addition",
          "description": "Add all numbers.",
          "parameters": {
            "type": "object",
            "properties": {
              "numbers": { "type": "array", "items": { "type": "number" } }
            },
            "required": ["numbers"]
          }
        }
    """.trimIndent()

    override fun execute(paramsJsonString: String): String {
        // parse params, run logic, return JSON result string
        return """{"result": 42}"""
    }
}
```

### Registering Tools
```kotlin
val conversation = engine.createConversation(
    ConversationConfig(
        tools = listOf(
            tool(SampleToolSet()),
            tool(SampleOpenApiTool())
        )
    )
)
```

### Manual Tool Calling (automaticToolCalling = false)
```kotlin
val conversation = engine.createConversation(
    ConversationConfig(
        tools = listOf(tool(SampleOpenApiTool())),
        automaticToolCalling = false
    )
)

val responseMessage = conversation.sendMessage("What's the weather in London?")
if (responseMessage.toolCalls.isNotEmpty()) {
    val toolResponses = mutableListOf<Content.ToolResponse>()
    for (toolCall in responseMessage.toolCalls) {
        val toolResponseJson = executeTool(toolCall.name, toolCall.arguments)
        toolResponses.add(Content.ToolResponse(toolCall.name, toolResponseJson))
    }
    val finalMessage = conversation.sendMessage(Message.tool(Contents.of(toolResponses)))
}
```

---

## Error Handling

| Exception | Source |
|---|---|
| `LiteRtLmJniException` | Native (JNI) layer errors |
| `IllegalStateException` | Bad state (e.g. engine not initialized) |

- Async errors are reported via `MessageCallback.onError(throwable)`
- Wrap `engine.initialize()` and `sendMessage()` in try-catch

---

## Project-Specific Notes (Gemma Voice App)

- Model file: `gemma-4-E2B-it.litertlm` (~2.58 GB, INT4)
- Stored at: `Context.getExternalFilesDir(null)/models/`
- `engine.initialize()` must run on a background thread (takes 5–10s)
- Engine is created once and kept alive for the session
- Audio prompt format:
  `"<start_of_turn>user\n<audio>\n<end_of_turn>\n<start_of_turn>model\n"`
- Audio spec: 16kHz, mono, `AudioFormat.ENCODING_PCM_FLOAT`
- Build rule required: `androidResources { noCompress += listOf(".litertlm", ".onnx") }`
- LiteRT-LM dependency is currently **commented out** in `app/build.gradle.kts` — uncomment in Sprint 3

---

## Useful Links
- HuggingFace models: https://huggingface.co/litert-community
- Google AI Edge Gallery (sample app): https://github.com/google-ai-edge/gallery
- Official docs: https://ai.google.dev/edge/litert-lm/android
