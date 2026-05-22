package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeepSeekMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class DeepSeekChatRequest(
    @Json(name = "model") val model: String = "deepseek-chat",
    @Json(name = "messages") val messages: List<DeepSeekMessage>,
    @Json(name = "temperature") val temperature: Double = 0.5,
    @Json(name = "max_tokens") val maxTokens: Int = 500,
    @Json(name = "stream") val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class DeepSeekChoice(
    @Json(name = "index") val index: Int? = 0,
    @Json(name = "message") val message: DeepSeekMessage?,
    @Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class DeepSeekUsage(
    @Json(name = "prompt_tokens") val promptTokens: Int? = 0,
    @Json(name = "completion_tokens") val completionTokens: Int? = 0,
    @Json(name = "total_tokens") val totalTokens: Int? = 0
)

@JsonClass(generateAdapter = true)
data class DeepSeekChatResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "object") val chatObject: String? = null,
    @Json(name = "created") val created: Long? = 0L,
    @Json(name = "model") val model: String? = null,
    @Json(name = "choices") val choices: List<DeepSeekChoice>?,
    @Json(name = "usage") val usage: DeepSeekUsage? = null
)
