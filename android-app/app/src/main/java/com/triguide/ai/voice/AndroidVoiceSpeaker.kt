package com.triguide.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
 * Prefers an offline voice. This build does not request internet access.
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
        val locale = when (directive.language) {
            ResolvedVoiceLanguage.ARABIC -> Locale.forLanguageTag("ar")
            ResolvedVoiceLanguage.ENGLISH -> Locale.US
        }
        val languageResult = engine.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.i(TAG, "setLanguage ${locale.toLanguageTag()} result=$languageResult; skipped ${directive.kind}")
            return false
        }
        engine.setSpeechRate(directive.speechRate)
        onFinished = whenFinished
        val utteranceId = "triguide-${directive.kind.name}-${android.os.SystemClock.elapsedRealtime()}"
        activeUtterance.set(utteranceId)
        val queueMode = if (directive.interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(directive.text, queueMode, null, utteranceId)
        Log.i(
            TAG,
            "speak result=$result queue=$queueMode kind=${directive.kind} " +
                "lang=${directive.language} rate=${directive.speechRate} text=${directive.text}",
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
        val installed = engine.voices.orEmpty()
        val offline = installed.filter { voice -> !voice.isNetworkConnectionRequired }
        englishOffline = offline.any { voice -> voice.locale.language.equals("en", ignoreCase = true) }
        arabicOffline = offline.any { voice -> voice.locale.language.equals("ar", ignoreCase = true) }
        statusState.value = when {
            englishOffline && arabicOffline -> "Offline English and Arabic voices are installed."
            englishOffline -> "Offline English voice is installed. Arabic voice is not installed."
            arabicOffline -> "Offline Arabic voice is installed. English voice is not installed."
            else -> "No offline voice is installed. Spoken cues stay on screen."
        }
        val engineNames = runCatching { engine.engines.joinToString { engineInfo -> engineInfo.label } }
            .getOrDefault("unavailable")
        Log.i(
            TAG,
            "ready en=$englishOffline ar=$arabicOffline offline=${offline.size} " +
                "installed=${installed.size} engines=$engineNames status=${statusState.value}",
        )
        offline.take(MAX_VOICES_LOGGED).forEach { voice ->
            Log.i(TAG, "voice name=${voice.name} locale=${voice.locale}")
        }
    }

    private fun finishIfCurrent(utteranceId: String?) {
        if (utteranceId == null || !activeUtterance.compareAndSet(utteranceId, null)) return
        onFinished?.invoke()
    }

    private companion object {
        const val TAG = "TriGuideVoice"
        const val MAX_VOICES_LOGGED = 12
    }
}
