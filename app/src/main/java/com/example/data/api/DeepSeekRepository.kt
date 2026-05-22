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
            "https://integrate.api.nvidia.com/v1/"
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
        if (preferences.usePersonalKey) {
            if (preferences.deepSeekApiKey.isNotBlank()) {
                return preferences.deepSeekApiKey.trim()
            }
            // Fallback to build configuration
            val globalKey = BuildConfig.DEEPSEEK_API_KEY
            if (globalKey.isNotBlank() && globalKey != "MY_DEEPSEEK_API_KEY") {
                return globalKey
            }
            return ""
        }
        // Load the default key securely from BuildConfig (injected via untracked .env file)
        val defaultKey = BuildConfig.DEFAULT_API_KEY
        if (defaultKey.isNotBlank() && defaultKey != "MY_DEFAULT_API_KEY") {
            return defaultKey
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
            return@withContext Result.failure(Exception("API Key is missing. Please add your key in settings."))
        }

        if (!preferences.consumeSearchToken()) {
            val errorMsg = if (preferences.usePersonalKey) {
                "API Key is missing. Please add your key in settings."
            } else {
                "Your 14-day free trial of default AI has expired. Please configure your own API key in Settings to continue using the keyboard for free."
            }
            return@withContext Result.failure(Exception(errorMsg))
        }

        val prompt = buildPrompt(featureType, text, additionalContext)
        val messages = listOf(
            DeepSeekMessage(
                role = "system",
                content = "You are an expert real-time writing assistant running inside a keyboard. Complete tasks concisely, returning ONLY the final updated text itself. Do not include chat greetings, double quotes, explaining sentences, or labels unless required. Preserve paragraphs if relevant. CRITICAL: You must correct any spelling mistakes, typos, and grammatical errors in the text. Ensure the output is grammatically flawless, correct, and has zero typos or spelling errors. Do not introduce any new errors."
            ),
            DeepSeekMessage(role = "user", content = prompt)
        )

        try {
            val service = getApiService()
            val requestModel = if (preferences.usePersonalKey) {
                preferences.customModel
            } else {
                "meta/llama-3.3-70b-instruct"
            }
            val response = service.createChatCompletion(
                authorizationHeader = "Bearer $apiKey",
                request = DeepSeekChatRequest(
                    model = requestModel,
                    messages = messages,
                    temperature = 0.2, // As requested in the user prompt snippet
                    maxTokens = 1024
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
                "Rewrite the following text $toneStr. Correct all spelling, grammar, and typos, improve vocabulary and flow, but keep original meaning intact. Ensure no typos remain:\n\n$text"
            }
            "GRAMMAR" -> {
                "Correct any grammatical mistakes, typos, spelling issues, and punctuation errors in this text. Make it grammatically flawless. Keep original tone and style intact:\n\n$text"
            }
            "SUMMARIZE" -> {
                "Summarize the following text concisely, capturing key points in 1-2 brief sentences suitable for swift typing. Correct all spelling and typos:\n\n$text"
            }
            "TRANSLATE" -> {
                "Translate the following text into $context. Maintain original meaning, flow, and intent perfectly, outputting ONLY the translated text. Correct all spelling and typos in the final translation:\n\n$text"
            }
            "REPLY" -> {
                "Draft a short context-appropriate email/social reply responding to this text. Keep it tailored, professional, correct, and matching the requested intent ($context). Ensure perfect spelling and grammar:\n\n$text"
            }
            "GENERATE" -> {
                "Draft a short text (e.g., $context) based on this prompt or instructions. Make it engaging, compact, correct, and ready to send. Ensure perfect spelling and grammar:\n\n$text"
            }
            "AUTOCOMPLETE" -> {
                "Based on the following context, complete the sentence or suggest the next 3-5 words that logically follow. Correct any typos present in the preceding context. Do not repeat the prompt. Context:\n\n$text"
            }
            else -> "Improve the following text. Correct all spelling, grammar, and typos:\n\n$text"
        }
    }
}
