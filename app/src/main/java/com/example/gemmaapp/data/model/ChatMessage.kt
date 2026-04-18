package com.example.gemmaapp.data.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val tokensPerSecond: Float = 0f,
    val isStreaming: Boolean = false,
) {
    enum class Role { USER, ASSISTANT }
}
