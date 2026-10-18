package com.triguide.ai.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Short spoken alerts for the research session.
 * The gate decides when a cue may be spoken. It does not talk to the synthesizer.
 */
enum class VoiceAlertKind {
    OBSTACLE_DETECTED,
    TURN_SUGGESTION,
    NAVIGATION_UNCERTAIN,
    CAMERA_UNAVAILABLE,
    SIMULATION_PAUSED,
}

enum class SpeechPriority(val rank: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3),
}

enum class VoiceDelivery {
    /** Present on later ticks, so it can wait out an utterance already in progress. */
    CONTINUOUS,

    /** Raised once. A later tick will not repeat it. */
    ONCE,
}

enum class VoiceLanguagePreference {
    DEVICE,
    ENGLISH,
    ARABIC,
}

enum class ResolvedVoiceLanguage {
    ENGLISH,
    ARABIC,
}

data class VoiceSettings(
    val speechEnabled: Boolean = true,
    val quietMode: Boolean = false,
    val speechRate: Float = VoiceConfig.RATE_DEFAULT,
    val language: VoiceLanguagePreference = VoiceLanguagePreference.DEVICE,
) {
    fun sanitized(): VoiceSettings = copy(speechRate = VoiceConfig.clampRate(speechRate))

    fun summary(): String {
        val enabled = if (speechEnabled) "On" else "Off"
        val quiet = if (quietMode) "Quiet mode on" else "Quiet mode off"
        val rate = String.format(java.util.Locale.US, "%.1f", sanitized().speechRate)
        val languageLabel = when (language) {
            VoiceLanguagePreference.DEVICE -> "Device language"
            VoiceLanguagePreference.ENGLISH -> "English"
            VoiceLanguagePreference.ARABIC -> "Arabic"
        }
        return "$enabled. Rate $rate. $quiet. $languageLabel"
    }
}

object VoiceConfig {
    const val RATE_MIN = 0.6f
    const val RATE_MAX = 1.6f
    const val RATE_DEFAULT = 1.0f

    /** Same critical cue may repeat, but not on every frame. */
    const val COOLDOWN_CRITICAL_MS = 6_000L
    const val COOLDOWN_HIGH_MS = 10_000L
    const val COOLDOWN_NORMAL_MS = 12_000L
    const val COOLDOWN_PAUSED_MS = 4_000L

    /** Assumed length of one utterance if the engine never reports completion. */
    const val UTTERANCE_WINDOW_MS = 2_500L

    /** Gap between non-critical cues so a flicker does not become a second sentence. */
    const val MIN_GAP_MS = 1_500L

    /** Backoff after the synthesizer rejects a cue, so a missing voice is not retried every frame. */
    const val UNAVAILABLE_RETRY_MS = 5_000L

    /** Startup frames are not announced as a missing camera. */
    const val CAMERA_START_GRACE_MS = 2_000L

    fun clampRate(rate: Float): Float {
        if (!rate.isFinite()) return RATE_DEFAULT
        return rate.coerceIn(RATE_MIN, RATE_MAX)
    }

    fun priorityOf(kind: VoiceAlertKind): SpeechPriority = when (kind) {
        VoiceAlertKind.OBSTACLE_DETECTED,
        VoiceAlertKind.CAMERA_UNAVAILABLE,
        -> SpeechPriority.CRITICAL
        VoiceAlertKind.NAVIGATION_UNCERTAIN -> SpeechPriority.HIGH
        VoiceAlertKind.TURN_SUGGESTION -> SpeechPriority.NORMAL
        VoiceAlertKind.SIMULATION_PAUSED -> SpeechPriority.LOW
    }

    fun deliveryOf(kind: VoiceAlertKind): VoiceDelivery = when (kind) {
        VoiceAlertKind.SIMULATION_PAUSED -> VoiceDelivery.ONCE
        else -> VoiceDelivery.CONTINUOUS
    }

    fun cooldownMs(kind: VoiceAlertKind): Long = when (kind) {
        VoiceAlertKind.OBSTACLE_DETECTED,
        VoiceAlertKind.CAMERA_UNAVAILABLE,
        -> COOLDOWN_CRITICAL_MS
        VoiceAlertKind.NAVIGATION_UNCERTAIN -> COOLDOWN_HIGH_MS
        VoiceAlertKind.TURN_SUGGESTION -> COOLDOWN_NORMAL_MS
        VoiceAlertKind.SIMULATION_PAUSED -> COOLDOWN_PAUSED_MS
    }

    /** Quiet mode keeps the two alerts that mean stop or look at the camera. */
    fun quietModeAllows(kind: VoiceAlertKind): Boolean {
        return priorityOf(kind) == SpeechPriority.CRITICAL
    }
}

data class VoiceCue(
    val kind: VoiceAlertKind,
    val dedupeKey: String,
    val english: String,
    val arabic: String,
) {
    val priority: SpeechPriority get() = VoiceConfig.priorityOf(kind)
    val delivery: VoiceDelivery get() = VoiceConfig.deliveryOf(kind)

    fun text(language: ResolvedVoiceLanguage): String = when (language) {
        ResolvedVoiceLanguage.ENGLISH -> english
        ResolvedVoiceLanguage.ARABIC -> arabic
    }
}

object VoiceCues {
    fun obstacle(): VoiceCue = VoiceCue(
        kind = VoiceAlertKind.OBSTACLE_DETECTED,
        dedupeKey = "obstacle",
        english = "Obstacle detected. Hold.",
        arabic = "عقبة مكتشفة. توقف.",
    )

    fun turn(action: GuidanceAction): VoiceCue {
        val phrase = when (action) {
            GuidanceAction.LEFT -> Phrase("Turn left.", "انعطف يساراً.", "turn-left")
            GuidanceAction.SLIGHT_LEFT -> Phrase("Slight left.", "يسار خفيف.", "turn-slight-left")
            GuidanceAction.RIGHT -> Phrase("Turn right.", "انعطف يميناً.", "turn-right")
            GuidanceAction.SLIGHT_RIGHT -> Phrase("Slight right.", "يمين خفيف.", "turn-slight-right")
            GuidanceAction.STRAIGHT -> Phrase("Straight ahead.", "للأمام.", "turn-straight")
            GuidanceAction.HOLD, GuidanceAction.UNKNOWN -> Phrase(
                "Navigation uncertain.",
                "الملاحة غير مؤكدة.",
                "uncertain",
            )
        }
        val kind = if (action == GuidanceAction.HOLD || action == GuidanceAction.UNKNOWN) {
            VoiceAlertKind.NAVIGATION_UNCERTAIN
        } else {
            VoiceAlertKind.TURN_SUGGESTION
        }
        return VoiceCue(kind, phrase.key, phrase.english, phrase.arabic)
    }

    fun uncertain(): VoiceCue = VoiceCue(
        kind = VoiceAlertKind.NAVIGATION_UNCERTAIN,
        dedupeKey = "uncertain",
        english = "Navigation uncertain.",
        arabic = "الملاحة غير مؤكدة.",
    )

    fun cameraUnavailable(): VoiceCue = VoiceCue(
        kind = VoiceAlertKind.CAMERA_UNAVAILABLE,
        dedupeKey = "camera",
        english = "Camera unavailable.",
        arabic = "الكاميرا غير متاحة.",
    )

    fun paused(): VoiceCue = VoiceCue(
        kind = VoiceAlertKind.SIMULATION_PAUSED,
        dedupeKey = "paused",
        english = "Simulation paused.",
        arabic = "المحاكاة متوقفة.",
    )

    fun sample(language: ResolvedVoiceLanguage): String = when (language) {
        ResolvedVoiceLanguage.ENGLISH ->
            "Research and simulation. Obstacle detected. This voice is not a safety proof."
        ResolvedVoiceLanguage.ARABIC ->
            "بحث ومحاكاة. عقبة مكتشفة. هذا الصوت ليس دليل أمان."
    }

    /**
     * Maps a published decision to one stable cue.
     * The key ignores detector scores and timestamps so a new frame is not a new sentence.
     */
    fun fromDecision(decision: NavigationDecision): VoiceCue? {
        if (decision.perceptionLabel == NavigationConfig.MANUAL_LABEL) return null
        return when (decision.action) {
            GuidanceAction.HOLD, GuidanceAction.UNKNOWN -> when {
                isSmootherWarmup(decision.reason) -> null
                isCameraUnavailable(decision.reason) -> cameraUnavailable()
                isObstacle(decision.reason) -> obstacle()
                else -> uncertain()
            }
            else -> turn(decision.action)
        }
    }

    /** The first frames of a new direction are a vote, not a new situation to announce. */
    private fun isSmootherWarmup(reason: String): Boolean {
        return reason.contains("Holding until the direction stays")
    }

    private fun isCameraUnavailable(reason: String): Boolean {
        return reason.contains("No real camera frame") ||
            reason.contains("frame is stale") ||
            reason.contains("no timestamp")
    }

    private fun isObstacle(reason: String): Boolean {
        return reason.contains("center of the image") ||
            reason.contains("both sides") ||
            reason.contains("forward axis")
    }

    private data class Phrase(val english: String, val arabic: String, val key: String)
}

data class VoiceGateInput(
    val cue: VoiceCue?,
    val settings: VoiceSettings,
    val nowElapsedMs: Long,
    val resolvedLanguage: ResolvedVoiceLanguage,
)

sealed class SpeechDirective {
    data class Speak(
        val text: String,
        val interrupt: Boolean,
        val speechRate: Float,
        val kind: VoiceAlertKind,
        val language: ResolvedVoiceLanguage,
    ) : SpeechDirective()

    data object Silent : SpeechDirective()
}

/**
 * Allows a cue through only when speech is on, quiet mode permits it,
 * the same cue is outside its cooldown, and a lower cue is not talking over a higher one.
 * A critical cue with a new key interrupts the utterance already in progress.
 */
class VoiceGuidanceGate {
    private val lock = Any()
    private var lastSpokenKey: String? = null
    private var lastSpokenAtMs: Long = 0L
    private var speakingUntilMs: Long = 0L
    private var activePriority: SpeechPriority? = null
    private var unavailableUntilMs: Long = 0L

    private var snapshotKey: String? = null
    private var snapshotAtMs: Long = 0L
    private var snapshotUntilMs: Long = 0L
    private var snapshotPriority: SpeechPriority? = null
    private var snapshotText: String = "none"

    private val lastSpokenState = MutableStateFlow("none")
    val lastSpokenText: StateFlow<String> = lastSpokenState.asStateFlow()

    fun consider(input: VoiceGateInput): SpeechDirective = synchronized(lock) {
        val settings = input.settings.sanitized()
        val cue = input.cue ?: return@synchronized SpeechDirective.Silent
        if (!settings.speechEnabled) return@synchronized SpeechDirective.Silent
        if (input.nowElapsedMs < unavailableUntilMs) return@synchronized SpeechDirective.Silent
        if (settings.quietMode && !VoiceConfig.quietModeAllows(cue.kind)) {
            return@synchronized SpeechDirective.Silent
        }
        if (cue.dedupeKey == lastSpokenKey &&
            input.nowElapsedMs - lastSpokenAtMs < VoiceConfig.cooldownMs(cue.kind)
        ) {
            return@synchronized SpeechDirective.Silent
        }

        val speaking = activePriority != null && input.nowElapsedMs < speakingUntilMs
        val interrupt = speaking && shouldInterrupt(cue)
        if (speaking && !interrupt && cue.delivery == VoiceDelivery.CONTINUOUS) {
            return@synchronized SpeechDirective.Silent
        }
        if (!interrupt && cue.priority != SpeechPriority.CRITICAL && lastSpokenKey != null) {
            val sinceLast = input.nowElapsedMs - lastSpokenAtMs
            if (sinceLast < VoiceConfig.MIN_GAP_MS && cue.dedupeKey != lastSpokenKey &&
                cue.delivery == VoiceDelivery.CONTINUOUS
            ) {
                return@synchronized SpeechDirective.Silent
            }
        }

        snapshotKey = lastSpokenKey
        snapshotAtMs = lastSpokenAtMs
        snapshotUntilMs = speakingUntilMs
        snapshotPriority = activePriority
        snapshotText = lastSpokenState.value

        lastSpokenKey = cue.dedupeKey
        lastSpokenAtMs = input.nowElapsedMs
        activePriority = cue.priority
        speakingUntilMs = input.nowElapsedMs + VoiceConfig.UTTERANCE_WINDOW_MS
        val text = cue.text(input.resolvedLanguage)
        lastSpokenState.value = text
        SpeechDirective.Speak(
            text = text,
            interrupt = interrupt,
            speechRate = settings.speechRate,
            kind = cue.kind,
            language = input.resolvedLanguage,
        )
    }

    fun onUtteranceFinished(nowElapsedMs: Long) = synchronized(lock) {
        if (activePriority != null && nowElapsedMs < speakingUntilMs) {
            speakingUntilMs = nowElapsedMs
        }
        activePriority = null
    }

    /**
     * The synthesizer rejected the cue just emitted.
     * Restore the previous cue and wait before trying again.
     */
    fun abandonLastUtterance(nowElapsedMs: Long) = synchronized(lock) {
        lastSpokenKey = snapshotKey
        lastSpokenAtMs = snapshotAtMs
        speakingUntilMs = snapshotUntilMs
        activePriority = snapshotPriority
        unavailableUntilMs = nowElapsedMs + VoiceConfig.UNAVAILABLE_RETRY_MS
        lastSpokenState.value = snapshotText
    }

    fun reset() = synchronized(lock) {
        lastSpokenKey = null
        lastSpokenAtMs = 0L
        speakingUntilMs = 0L
        activePriority = null
        unavailableUntilMs = 0L
        snapshotKey = null
        snapshotAtMs = 0L
        snapshotUntilMs = 0L
        snapshotPriority = null
        snapshotText = "none"
        lastSpokenState.value = "none"
    }

    private fun shouldInterrupt(cue: VoiceCue): Boolean {
        val active = activePriority ?: return false
        if (cue.priority.rank > active.rank) return true
        return cue.priority == SpeechPriority.CRITICAL && cue.dedupeKey != lastSpokenKey
    }
}
