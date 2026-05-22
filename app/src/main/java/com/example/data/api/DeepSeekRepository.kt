package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.pref.KeyboardPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeepSeekRepository(
    private val defaultApiService: DeepSeekApiService,
    private val preferences: KeyboardPreferences
) {

    private val tag = "DeepSeekRepository"
    private var dynamicApiService: DeepSeekApiService? = null
    private var currentUrl: String? = null

    private fun getApiService(): DeepSeekApiService {
        val targetUrl = if (preferences.usePersonalKey) {
            preferences.customBaseUrl
        } else {
            when (preferences.apiProvider) {
                KeyboardPreferences.ApiProvider.NVIDIA_MISTRAL -> "https://integrate.api.nvidia.com/v1/"
                else -> "https://api.deepseek.com/"
            }
        }

        if (dynamicApiService == null || currentUrl != targetUrl) {
            currentUrl = targetUrl
            dynamicApiService = DeepSeekApiService.create(targetUrl)
        }
        return dynamicApiService!!
    }

    /**
     * Resolves the active API key. First checks if the user has enabled their
     * personal key in options. Otherwise falls back to provider defaults or BuildConfig.
     */
    private fun getApiKey(): String {
        if (preferences.usePersonalKey && preferences.deepSeekApiKey.isNotBlank()) {
            return preferences.deepSeekApiKey.trim()
        }
        if (preferences.apiProvider == KeyboardPreferences.ApiProvider.NVIDIA_MISTRAL) {
            return "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"
        }
        // Fallback to build configuration (from secrets panel/.env)
        // Check if injected value is a placeholder
        val globalKey = BuildConfig.DEEPSEEK_API_KEY
        if (globalKey.isNotBlank() && globalKey != "MY_DEEPSEEK_API_KEY") {
            return globalKey
        }
        return ""
    }

    suspend fun executeAiAction(
        featureType: String,
        text: String,
        additionalContext: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("API Key is missing. Please add your key in settings or configuration panel."))
        }

        if (!preferences.consumeSearchToken()) {
            return@withContext Result.failure(Exception("Out of trial requests (Limits exhausted). Please add your premium API key in Settings."))
        }

        val prompt = buildPrompt(featureType, text, additionalContext)
        val messages = listOf(
            DeepSeekMessage(role = "system", content = "You are an expert real-time writing assistant running inside a keyboard. Complete tasks concisely, returning ONLY the final updated text itself. Do not include chat greetings, double quotes, explaining sentences, or labels unless required. Preserve paragraphs if relevant."),
            DeepSeekMessage(role = "user", content = prompt)
        )

        try {
            val service = getApiService()
            val requestModel = if (preferences.usePersonalKey) {
                preferences.customModel
            } else {
                when (preferences.apiProvider) {
                    KeyboardPreferences.ApiProvider.NVIDIA_MISTRAL -> "mistralai/mistral-medium-3.5-128b"
                    else -> "deepseek-chat"
                }
            }
            val response = service.createChatCompletion(
                authorizationHeader = "Bearer $apiKey",
                request = DeepSeekChatRequest(
                    model = requestModel,
                    messages = messages,
                    temperature = 0.7,
                    maxTokens = 2048
                )
            )

            val reply = response.choices?.firstOrNull()?.message?.content?.trim()
                ?: return@withContext Result.failure(Exception("No suggestion returned from AI model."))

            // Sanitise surrounding quotes
            var sanitised = reply
            if (sanitised.startsWith("\"") && sanitised.endsWith("\"")) {
                sanitised = sanitised.substring(1, sanitised.length - 1)
            }

            Result.success(sanitised)
        } catch (e: Exception) {
            Log.e(tag, "Error invoking DeepSeek API: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildPrompt(featureType: String, text: String, context: String): String {
        return when (featureType) {
            "REWRITE" -> {
                val toneStr = if (context.isNotBlank()) "in a $context tone" else "beautifully and clearly"
                "Rewrite the following text $toneStr. Maintain original meaning but improve vocabulary, fluidity, and flow:\n\n$text"
            }
            "GRAMMAR" -> {
                "Correct any grammatical mistakes, typos, spelling issues, and punctuation errors in this text. Make it grammatically flawless. Keep original tone and style intact:\n\n$text"
            }
            "SUMMARIZE" -> {
                "Summarize the following text concisely, capturing key points in 1-2 brief sentences suitable for swift typing:\n\n$text"
            }
            "TRANSLATE" -> {
                "Translate the following text into $context. Maintain original meaning, flow, and intent perfectly, outputting ONLY the translated text:\n\n$text"
            }
            "REPLY" -> {
                "Draft a short context-appropriate email/social reply responding to this text. Keep it tailored, professional, and matching the requested intent ($context):\n\n$text"
            }
            "GENERATE" -> {
                "Draft a short text (e.g., $context) based on this prompt or instructions. Make it engaging, compact, and ready to send:\n\n$text"
            }
            "AUTOCOMPLETE" -> {
                "Based on the following context, complete the sentence or suggest the next 3-5 words that logically follow. Do not repeat the prompt. Context:\n\n$text"
            }
            else -> "Improve the following text:\n\n$text"
        }
    }
}
