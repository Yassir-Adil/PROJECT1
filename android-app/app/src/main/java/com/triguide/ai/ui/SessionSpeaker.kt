package com.triguide.ai.ui

import com.triguide.ai.domain.ResolvedVoiceLanguage
import com.triguide.ai.domain.SpeechDirective
import com.triguide.ai.domain.SpokenLine
import com.triguide.ai.domain.VoiceAlertKind
import com.triguide.ai.domain.VoiceSettings
import com.triguide.ai.voice.AndroidVoiceSpeaker
import kotlinx.coroutines.delay

/**
 * Speaks a session line as soon as the engine is ready.
 * Speech off counts as delivered. Quiet mode does not block these lines:
 * they are how a blind user learns that guidance started, paused, or needs the camera.
 */
internal suspend fun speakWhenReady(
    speaker: AndroidVoiceSpeaker,
    settings: () -> VoiceSettings,
    line: SpokenLine,
    interrupt: Boolean,
    timeoutMs: Long = 5_000L,
): Boolean {
    val current = settings().sanitized()
    if (!current.speechEnabled) return true
    val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
    while (!speaker.ready.value && android.os.SystemClock.elapsedRealtime() < deadline) {
        delay(100)
    }
    if (!speaker.ready.value) return false
    val fresh = settings().sanitized()
    if (!fresh.speechEnabled) return true
    val language = speaker.resolve(fresh.language)
    val text = when (language) {
        ResolvedVoiceLanguage.ENGLISH -> line.english
        ResolvedVoiceLanguage.ARABIC -> line.arabic
    }
    return speaker.speak(
        SpeechDirective.Speak(
            text = text,
            interrupt = interrupt,
            speechRate = fresh.speechRate,
            kind = VoiceAlertKind.SESSION_NOTICE,
            language = language,
        ),
    ) {}
}

internal suspend fun speakLinesInOrder(
    speaker: AndroidVoiceSpeaker,
    settings: () -> VoiceSettings,
    lines: List<SpokenLine>,
): Boolean {
    for ((index, line) in lines.withIndex()) {
        val accepted = speakWhenReady(
            speaker = speaker,
            settings = settings,
            line = line,
            interrupt = index == 0,
            timeoutMs = if (index == 0) 5_000L else 1_500L,
        )
        if (!accepted) return false
    }
    return true
}
