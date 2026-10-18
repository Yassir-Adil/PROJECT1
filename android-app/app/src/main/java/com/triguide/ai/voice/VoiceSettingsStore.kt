package com.triguide.ai.voice

import android.content.Context
import com.triguide.ai.domain.VoiceConfig
import com.triguide.ai.domain.VoiceLanguagePreference
import com.triguide.ai.domain.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Four voice preferences. SharedPreferences is enough for this set and can be read on the control tick.
 */
class VoiceSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<VoiceSettings> = state.asStateFlow()

    fun current(): VoiceSettings = state.value

    fun setSpeechEnabled(enabled: Boolean) = update { it.copy(speechEnabled = enabled) }

    fun setQuietMode(enabled: Boolean) = update { it.copy(quietMode = enabled) }

    fun setSpeechRate(rate: Float) = update { it.copy(speechRate = rate) }

    fun setLanguage(language: VoiceLanguagePreference) = update { it.copy(language = language) }

    private fun update(transform: (VoiceSettings) -> VoiceSettings) {
        val next = transform(state.value).sanitized()
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.speechEnabled)
            .putBoolean(KEY_QUIET, next.quietMode)
            .putFloat(KEY_RATE, next.speechRate)
            .putString(KEY_LANGUAGE, next.language.name)
            .apply()
        state.value = next
    }

    private fun read(): VoiceSettings {
        val storedLanguage = prefs.getString(KEY_LANGUAGE, null)
        val language = storedLanguage
            ?.let { name -> runCatching { VoiceLanguagePreference.valueOf(name) }.getOrNull() }
            ?: VoiceLanguagePreference.DEVICE
        val tuning = prefs.getInt(KEY_TUNING, 0)
        val rate = if (tuning < RATE_TUNING) {
            VoiceConfig.RATE_DEFAULT
        } else {
            prefs.getFloat(KEY_RATE, VoiceConfig.RATE_DEFAULT)
        }
        if (tuning < RATE_TUNING) {
            prefs.edit()
                .putInt(KEY_TUNING, RATE_TUNING)
                .putFloat(KEY_RATE, VoiceConfig.RATE_DEFAULT)
                .apply()
        }
        return VoiceSettings(
            speechEnabled = prefs.getBoolean(KEY_ENABLED, true),
            quietMode = prefs.getBoolean(KEY_QUIET, false),
            speechRate = rate,
            language = language,
        ).sanitized()
    }

    private companion object {
        const val PREFS = "triguide_voice"
        const val KEY_ENABLED = "speech_enabled"
        const val KEY_QUIET = "quiet_mode"
        const val KEY_RATE = "speech_rate"
        const val KEY_LANGUAGE = "language"
        const val KEY_TUNING = "rate_tuning"
        const val RATE_TUNING = 2
    }
}
