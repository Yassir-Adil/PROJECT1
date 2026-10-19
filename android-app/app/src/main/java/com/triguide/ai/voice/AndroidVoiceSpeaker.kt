package com.triguide.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.triguide.ai.domain.ResolvedVoiceLanguage
import com.triguide.ai.domain.SpeechDirective
import com.triguide.ai.domain.VoiceLanguagePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Android speech engine. Speaks only the cues the gate has already allowed.
 * Selects an installed offline [Voice] via [OfflineVoiceSelector] and [TextToSpeech.setVoice].
 * This build does not request internet access and does not fall back to network voices.
 */
class AndroidVoiceSpeaker(
    context: Context,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val engine = TextToSpeech(appContext, this::onInit)
    private val readyState = MutableStateFlow(false)
    private val statusState = MutableStateFlow("Starting voice.")
    private val activeUtterance = AtomicReference<String?>(null)
    private var onFinished: (() -> Unit)? = null
    private var englishOffline = false
    private var arabicOffline = false
    private var lastSelectedVoiceId: String? = null

    val status: StateFlow<String> = statusState.asStateFlow()
    val ready: StateFlow<Boolean> = readyState.asStateFlow()

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = finishIfCurrent(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finishIfCurrent(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finishIfCurrent(utteranceId)
        })
    }

    fun resolve(preference: VoiceLanguagePreference): ResolvedVoiceLanguage {
        val preferArabic = when (preference) {
            VoiceLanguagePreference.ARABIC -> true
            VoiceLanguagePreference.ENGLISH -> false
            VoiceLanguagePreference.DEVICE -> Locale.getDefault().language.equals("ar", ignoreCase = true)
        }
        return if (preferArabic && arabicOffline) {
            ResolvedVoiceLanguage.ARABIC
        } else {
            ResolvedVoiceLanguage.ENGLISH
        }
    }

    fun speak(directive: SpeechDirective.Speak, whenFinished: () -> Unit): Boolean {
        if (!readyState.value) {
            Log.i(TAG, "not ready; skipped ${directive.kind}")
            return false
        }
        if (!englishOffline && !arabicOffline) {
            refreshVoices()
        }
        val haveVoice = when (directive.language) {
            ResolvedVoiceLanguage.ARABIC -> arabicOffline
            ResolvedVoiceLanguage.ENGLISH -> englishOffline
        }
        if (!haveVoice) {
            Log.i(TAG, "no offline voice for ${directive.language}; skipped ${directive.kind}")
            return false
        }
        if (!selectOfflineVoice(directive.language)) {
            statusState.value = "Offline voice selection failed. Spoken cues stay on screen."
            Log.i(TAG, "setVoice failed for all offline candidates; skipped ${directive.kind}")
            return false
        }
        engine.setSpeechRate(directive.speechRate)
        engine.setPitch(com.triguide.ai.domain.VoiceConfig.clampPitch(directive.pitch))
        onFinished = whenFinished
        val utteranceId = "triguide-${directive.kind.name}-${android.os.SystemClock.elapsedRealtime()}"
        activeUtterance.set(utteranceId)
        val queueMode = if (directive.interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(directive.text, queueMode, null, utteranceId)
        Log.i(
            TAG,
            "speak result=$result queue=$queueMode kind=${directive.kind} " +
                "lang=${directive.language} voice=${lastSelectedVoiceId} rate=${directive.speechRate} " +
                "pitch=${com.triguide.ai.domain.VoiceConfig.clampPitch(directive.pitch)} " +
                "text=${directive.text}",
        )
        if (result != TextToSpeech.SUCCESS) {
            activeUtterance.compareAndSet(utteranceId, null)
            return false
        }
        return true
    }

    override fun close() {
        activeUtterance.set(null)
        onFinished = null
        engine.stop()
        engine.shutdown()
        readyState.value = false
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            readyState.value = false
            statusState.value = "Voice engine failed to start. Cues stay on screen."
            Log.i(TAG, "init failed status=$status")
            return
        }
        refreshVoices()
        readyState.value = true
    }

    private fun refreshVoices() {
        val candidates = voiceCandidates()
        englishOffline = OfflineVoiceSelector.hasOffline(candidates, LANG_EN)
        arabicOffline = OfflineVoiceSelector.hasOffline(candidates, LANG_AR)
        statusState.value = when {
            englishOffline && arabicOffline -> "Offline English and Arabic voices are installed."
            englishOffline -> "Offline English voice is installed. Arabic voice is not installed."
            arabicOffline -> "Offline Arabic voice is installed. English voice is not installed."
            else -> "No offline voice is installed. Spoken cues stay on screen."
        }
        val engineNames = runCatching {
            engine.engines.joinToString { engineInfo ->
                "${engineInfo.label} (${engineInfo.name})"
            }
        }.getOrDefault("unavailable")
        val defaultEngine = runCatching {
            android.provider.Settings.Secure.getString(
                appContext.contentResolver,
                android.provider.Settings.Secure.TTS_DEFAULT_SYNTH,
            )
        }.getOrNull()
        Log.i(
            TAG,
            "ready en=$englishOffline ar=$arabicOffline offlineEligible=" +
                "${OfflineVoiceSelector.eligible(candidates, LANG_EN).size}+" +
                "${OfflineVoiceSelector.eligible(candidates, LANG_AR).size} " +
                "installed=${candidates.size} defaultEngine=$defaultEngine engines=$engineNames " +
                "status=${statusState.value}",
        )
        candidates.take(MAX_VOICES_LOGGED).forEach { voice ->
            Log.i(
                TAG,
                "voice name=${voice.id} locale=${voice.localeTag} quality=${voice.quality} " +
                    "latency=${voice.latency} network=${voice.requiresNetwork} installed=${voice.installed}",
            )
        }
    }

    /**
     * Picks an offline [Voice] with [TextToSpeech.setVoice]. Tries next ranked candidate on
     * failure. Does not call [TextToSpeech.setLanguage] (that can override the offline choice).
     */
    private fun selectOfflineVoice(language: ResolvedVoiceLanguage): Boolean {
        val languageCode = when (language) {
            ResolvedVoiceLanguage.ARABIC -> LANG_AR
            ResolvedVoiceLanguage.ENGLISH -> LANG_EN
        }
        val preferredLocale = when (language) {
            ResolvedVoiceLanguage.ARABIC -> "ar"
            ResolvedVoiceLanguage.ENGLISH -> "en-US"
        }
        val ranked = OfflineVoiceSelector.ranked(
            candidates = voiceCandidates(),
            languageCode = languageCode,
            preferredLocaleTag = preferredLocale,
        )
        if (ranked.isEmpty()) return false
        val byName = engine.voices.orEmpty().associateBy { it.name }
        for (candidate in ranked) {
            val voice = byName[candidate.id] ?: continue
            val result = runCatching { engine.setVoice(voice) }.getOrDefault(TextToSpeech.ERROR)
            if (result == TextToSpeech.SUCCESS) {
                if (lastSelectedVoiceId != candidate.id) {
                    lastSelectedVoiceId = candidate.id
                    Log.i(
                        TAG,
                        "selected offline voice id=${candidate.id} locale=${candidate.localeTag} " +
                            "quality=${candidate.quality} latency=${candidate.latency} lang=$languageCode",
                    )
                }
                return true
            }
            Log.i(TAG, "setVoice failed id=${candidate.id} result=$result; trying next offline")
        }
        lastSelectedVoiceId = null
        return false
    }

    private fun voiceCandidates(): List<OfflineVoiceCandidate> =
        engine.voices.orEmpty().map { voice -> voice.toCandidate() }

    private fun finishIfCurrent(utteranceId: String?) {
        if (utteranceId == null || !activeUtterance.compareAndSet(utteranceId, null)) return
        onFinished?.invoke()
    }

    private companion object {
        const val TAG = "TriGuideVoice"
        const val MAX_VOICES_LOGGED = 24
        const val LANG_EN = "en"
        const val LANG_AR = "ar"

        fun Voice.toCandidate(): OfflineVoiceCandidate = OfflineVoiceCandidate(
            id = name,
            language = locale.language,
            localeTag = locale.toLanguageTag(),
            quality = quality,
            latency = latency,
            requiresNetwork = isNetworkConnectionRequired,
            // Android's Voice.getFeatures() may return null on error. Unknown features are
            // not evidence of a downloaded voice; setVoice/speak still need device testing.
            installed = features?.contains(FEATURE_NOT_INSTALLED) == false,
        )

        /** Matches TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED (API 21+). */
        const val FEATURE_NOT_INSTALLED = "notInstalled"
    }
}
