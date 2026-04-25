package com.limpu.hitax.agent.llm

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
)

data class ChatMessage(
    val role: String,
    val content: String,
)

data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
) {
    data class Choice(
        val index: Int = 0,
        val message: ChatMessage? = null,
        @SerializedName("finish_reason")
        val finishReason: String? = null,
    )

    data class Usage(
        @SerializedName("prompt_tokens")
        val promptTokens: Int = 0,
        @SerializedName("completion_tokens")
        val completionTokens: Int = 0,
        @SerializedName("total_tokens")
        val totalTokens: Int = 0,
    )
}
