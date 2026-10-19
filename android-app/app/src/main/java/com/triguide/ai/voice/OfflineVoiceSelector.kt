package com.triguide.ai.voice

/**
 * Pure offline TTS ranking. JVM-testable without Android [android.speech.tts.Voice].
 *
 * Eligible voices are installed, non-network, and match the requested language code.
 * Rank: higher quality, then lower latency, then preferred-locale match, then name.
 */
data class OfflineVoiceCandidate(
    val id: String,
    val language: String,
    val localeTag: String,
    val quality: Int,
    val latency: Int,
    val requiresNetwork: Boolean,
    val installed: Boolean = true,
)

object OfflineVoiceSelector {
    fun eligible(
        candidates: Collection<OfflineVoiceCandidate>,
        languageCode: String,
    ): List<OfflineVoiceCandidate> {
        val lang = languageCode.lowercase()
        return candidates.filter { candidate ->
            candidate.installed &&
                !candidate.requiresNetwork &&
                candidate.language.equals(lang, ignoreCase = true)
        }
    }

    /**
     * Best-first order for [android.speech.tts.TextToSpeech.setVoice] attempts.
     * Empty when no offline installed voice matches [languageCode].
     */
    fun ranked(
        candidates: Collection<OfflineVoiceCandidate>,
        languageCode: String,
        preferredLocaleTag: String? = null,
    ): List<OfflineVoiceCandidate> {
        val preferred = preferredLocaleTag?.lowercase()
        return eligible(candidates, languageCode).sortedWith(
            compareByDescending<OfflineVoiceCandidate> { it.quality }
                .thenBy { it.latency }
                .thenByDescending { candidate ->
                    preferred != null && candidate.localeTag.lowercase() == preferred
                }
                .thenBy { it.id }
        )
    }

    fun select(
        candidates: Collection<OfflineVoiceCandidate>,
        languageCode: String,
        preferredLocaleTag: String? = null,
    ): OfflineVoiceCandidate? =
        ranked(candidates, languageCode, preferredLocaleTag).firstOrNull()

    fun hasOffline(
        candidates: Collection<OfflineVoiceCandidate>,
        languageCode: String,
    ): Boolean = eligible(candidates, languageCode).isNotEmpty()
}
