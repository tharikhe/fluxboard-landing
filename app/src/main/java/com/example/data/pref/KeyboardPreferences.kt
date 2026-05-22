package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences

class KeyboardPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "deep_keyboard_preferences"

        private const val KEY_THEME = "keyboard_theme"
        private const val KEY_HAPTIC_FEEDBACK = "keyboard_haptic_feedback"
        private const val KEY_VIBRATION_DURATION = "keyboard_vibration_duration"
        private const val KEY_SOUND_FEEDBACK = "keyboard_sound_feedback"
        private const val KEY_AUTO_CAPITALIZATION = "keyboard_auto_capitalization"
        private const val KEY_INCOGNITO_MODE = "keyboard_incognito_mode"
        private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
        private const val KEY_USE_PERSONAL_KEY = "use_personal_key"
        private const val KEY_PREMIUM_USER = "premium_user"
        private const val KEY_FREE_TRIAL_COUNT = "free_trial_count"
        private const val KEY_API_PROVIDER = "api_provider"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_CUSTOM_MODEL = "custom_model"
        private const val MAX_FREE_TRIALS = 15
    }

    enum class KeyboardTheme {
        LIGHT, DARK, AMOLED, COSMIC_INDIGO
    }

    enum class ApiProvider {
        DEEPSEEK, OPENAI_COMPATIBLE, NVIDIA_MISTRAL
    }

    var apiProvider: ApiProvider
        get() {
            val name = prefs.getString(KEY_API_PROVIDER, ApiProvider.NVIDIA_MISTRAL.name) ?: ApiProvider.NVIDIA_MISTRAL.name
            return try { ApiProvider.valueOf(name) } catch (e: Exception) { ApiProvider.NVIDIA_MISTRAL }
        }
        set(value) {
            prefs.edit().putString(KEY_API_PROVIDER, value.name).apply()
        }

    var customBaseUrl: String
        get() = prefs.getString(KEY_CUSTOM_BASE_URL, "https://api.deepseek.com/") ?: "https://api.deepseek.com/"
        set(value) {
            var cleaned = value.trim()
            if (cleaned.endsWith("/chat/completions")) {
                cleaned = cleaned.substring(0, cleaned.length - "/chat/completions".length)
            } else if (cleaned.endsWith("/chat/completions/")) {
                cleaned = cleaned.substring(0, cleaned.length - "/chat/completions/".length)
            }
            if (cleaned.endsWith("/completions")) {
                cleaned = cleaned.substring(0, cleaned.length - "/completions".length)
            } else if (cleaned.endsWith("/completions/")) {
                cleaned = cleaned.substring(0, cleaned.length - "/completions/".length)
            }
            // Guarantee trailing slash for Retrofit compatibility
            val formatted = if (cleaned.isNotBlank() && !cleaned.endsWith("/")) "$cleaned/" else cleaned
            prefs.edit().putString(KEY_CUSTOM_BASE_URL, formatted).apply()
        }

    var customModel: String
        get() = prefs.getString(KEY_CUSTOM_MODEL, "deepseek-chat") ?: "deepseek-chat"
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_MODEL, value).apply()
        }

    var selectedTheme: KeyboardTheme
        get() {
            val name = prefs.getString(KEY_THEME, KeyboardTheme.DARK.name) ?: KeyboardTheme.DARK.name
            return try { KeyboardTheme.valueOf(name) } catch (e: Exception) { KeyboardTheme.DARK }
        }
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    var isHapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()

    var vibrationDurationMs: Int
        get() = prefs.getInt(KEY_VIBRATION_DURATION, 15)
        set(value) = prefs.edit().putInt(KEY_VIBRATION_DURATION, value).apply()

    var isSoundFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_FEEDBACK, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_FEEDBACK, value).apply()

    var isAutoCapitalizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAPITALIZATION, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CAPITALIZATION, value).apply()

    var isIncognitoModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_INCOGNITO_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_INCOGNITO_MODE, value).apply()

    var deepSeekApiKey: String
        get() = prefs.getString(KEY_DEEPSEEK_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEEPSEEK_API_KEY, value).apply()

    var usePersonalKey: Boolean
        get() = prefs.getBoolean(KEY_USE_PERSONAL_KEY, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_PERSONAL_KEY, value).apply()

    var isPremiumUser: Boolean
        get() = prefs.getBoolean(KEY_PREMIUM_USER, false)
        set(value) = prefs.edit().putBoolean(KEY_PREMIUM_USER, value).apply()

    var freeTrialCount: Int
        get() = prefs.getInt(KEY_FREE_TRIAL_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_FREE_TRIAL_COUNT, value).apply()

    val trialsLeft: Int
        get() = (MAX_FREE_TRIALS - freeTrialCount).coerceAtLeast(0)

    fun hasSearchTokensLeft(): Boolean {
        if (isPremiumUser) return true
        if (usePersonalKey && deepSeekApiKey.isNotBlank()) return true
        return freeTrialCount < MAX_FREE_TRIALS
    }

    fun consumeSearchToken(): Boolean {
        if (isPremiumUser) return true
        if (usePersonalKey && deepSeekApiKey.isNotBlank()) return true
        val current = freeTrialCount
        if (current < MAX_FREE_TRIALS) {
            freeTrialCount = current + 1
            return true
        }
        return false
    }
}
