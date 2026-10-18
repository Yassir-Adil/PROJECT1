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

    /** Start, pause, permission, and the once-per-session disclaimer. Spoken outside the frame gate. */
    SESSION_NOTICE,

    /** A short report of several boxes. Observation only. It does not suggest a direction. */
    SCENE_BRIEF,
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
    const val RATE_DEFAULT = 0.90f
    const val PITCH_DEFAULT = 1.0f
    const val PITCH_HOLD = 0.96f
    const val PITCH_MIN = 0.85f
    const val PITCH_MAX = 1.15f

    /** Same critical cue may repeat, but not on every frame. */
    const val COOLDOWN_CRITICAL_MS = 6_000L
    const val COOLDOWN_HIGH_MS = 10_000L
    const val COOLDOWN_NORMAL_MS = 8_000L
    const val COOLDOWN_PAUSED_MS = 4_000L
    const val COOLDOWN_BRIEF_MS = 8_000L

    /** Assumed length of one utterance if the engine never reports completion. */
    const val UTTERANCE_WINDOW_MS = 4_000L

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

    fun clampPitch(pitch: Float): Float {
        if (!pitch.isFinite()) return PITCH_DEFAULT
        return pitch.coerceIn(PITCH_MIN, PITCH_MAX)
    }

    fun pitchFor(kind: VoiceAlertKind): Float = when (kind) {
        VoiceAlertKind.OBSTACLE_DETECTED,
        VoiceAlertKind.CAMERA_UNAVAILABLE,
        -> PITCH_HOLD
        else -> PITCH_DEFAULT
    }

    fun priorityOf(kind: VoiceAlertKind): SpeechPriority = when (kind) {
        VoiceAlertKind.OBSTACLE_DETECTED,
        VoiceAlertKind.CAMERA_UNAVAILABLE,
        -> SpeechPriority.CRITICAL
        VoiceAlertKind.NAVIGATION_UNCERTAIN -> SpeechPriority.HIGH
        VoiceAlertKind.TURN_SUGGESTION -> SpeechPriority.NORMAL
        VoiceAlertKind.SIMULATION_PAUSED,
        VoiceAlertKind.SCENE_BRIEF,
        -> SpeechPriority.LOW
        VoiceAlertKind.SESSION_NOTICE -> SpeechPriority.HIGH
    }

    fun deliveryOf(kind: VoiceAlertKind): VoiceDelivery = when (kind) {
        VoiceAlertKind.SIMULATION_PAUSED,
        VoiceAlertKind.SESSION_NOTICE,
        -> VoiceDelivery.ONCE
        else -> VoiceDelivery.CONTINUOUS
    }

    fun cooldownMs(kind: VoiceAlertKind): Long = when (kind) {
        VoiceAlertKind.OBSTACLE_DETECTED,
        VoiceAlertKind.CAMERA_UNAVAILABLE,
        -> COOLDOWN_CRITICAL_MS
        VoiceAlertKind.NAVIGATION_UNCERTAIN -> COOLDOWN_HIGH_MS
        VoiceAlertKind.TURN_SUGGESTION -> COOLDOWN_NORMAL_MS
        VoiceAlertKind.SIMULATION_PAUSED -> COOLDOWN_PAUSED_MS
        VoiceAlertKind.SESSION_NOTICE -> COOLDOWN_PAUSED_MS
        VoiceAlertKind.SCENE_BRIEF -> COOLDOWN_BRIEF_MS
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

    fun turn(action: GuidanceAction, label: String = "", side: String = ""): VoiceCue {
        val named = label.isNotBlank() && side.isNotBlank()
        val phrase = if (named) namedTurn(action, label, side) else genericTurn(action)
        val kind = if (action == GuidanceAction.HOLD || action == GuidanceAction.UNKNOWN) {
            VoiceAlertKind.NAVIGATION_UNCERTAIN
        } else {
            VoiceAlertKind.TURN_SUGGESTION
        }
        return VoiceCue(kind, phrase.key, phrase.english, phrase.arabic)
    }

    fun sceneBrief(english: String, arabic: String): VoiceCue = VoiceCue(
        kind = VoiceAlertKind.SCENE_BRIEF,
        dedupeKey = "scene-brief",
        english = english,
        arabic = arabic,
    )

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
            "Research and simulation. Person on the left. Turn slightly right. This voice is not a safety proof."
        ResolvedVoiceLanguage.ARABIC ->
            "بحث ومحاكاة. شخص على اليسار. انعطف يميناً قليلاً. هذا الصوت ليس دليل أمان."
    }

    /**
     * Maps a published decision to one stable cue.
     * The key ignores detector scores and timestamps so a new frame is not a new sentence.
     * A camera turn names the box and its side. It does not claim the other side is open.
     */
    fun fromDecision(decision: NavigationDecision): VoiceCue? {
        if (decision.perceptionLabel == NavigationConfig.MANUAL_LABEL) return null
        return when (decision.action) {
            GuidanceAction.HOLD, GuidanceAction.UNKNOWN -> holdCue(decision)
            else -> {
                val camera = decision.perceptionLabel == NavigationConfig.CAMERA_LABEL
                if (camera && decision.subjectLabel.isNotBlank() && decision.subjectSide.isNotBlank()) {
                    turn(decision.action, decision.subjectLabel, decision.subjectSide)
                } else {
                    turn(decision.action)
                }
            }
        }
    }

    private fun holdCue(decision: NavigationDecision): VoiceCue? {
        if (isSmootherWarmup(decision.reason)) return null
        if (isCameraUnavailable(decision.reason)) return cameraUnavailable()
        val camera = decision.perceptionLabel == NavigationConfig.CAMERA_LABEL
        if (camera && decision.subjectSide == "center" && decision.subjectLabel.isNotBlank()) {
            val englishName = SpokenLabels.english(decision.subjectLabel)
            val arabicName = SpokenLabels.arabic(decision.subjectLabel)
            return VoiceCue(
                kind = VoiceAlertKind.OBSTACLE_DETECTED,
                dedupeKey = "obstacle",
                english = "$englishName in the center. Hold.",
                arabic = "$arabicName في الوسط. توقف.",
            )
        }
        if (camera && decision.subjectSide == "both") {
            return VoiceCue(
                kind = VoiceAlertKind.OBSTACLE_DETECTED,
                dedupeKey = "obstacle",
                english = "Objects on both sides. Hold.",
                arabic = "أجسام على الجهتين. توقف.",
            )
        }
        if (isObstacle(decision.reason)) return obstacle()
        if (camera && decision.reason.contains("No confirmed passage")) {
            return VoiceCue(
                kind = VoiceAlertKind.NAVIGATION_UNCERTAIN,
                dedupeKey = "uncertain",
                english = "No reported object. Hold. An empty picture is not a clear path.",
                arabic = "لا جسم مبلغ عنه. توقف. الصورة الفارغة ليست طريقاً واضحاً.",
            )
        }
        if (decision.action == GuidanceAction.UNKNOWN && decision.reason.contains("contradictory")) {
            return VoiceCue(
                kind = VoiceAlertKind.NAVIGATION_UNCERTAIN,
                dedupeKey = "uncertain",
                english = "The picture is unclear. No direction.",
                arabic = "الصورة غير واضحة. لا اتجاه.",
            )
        }
        return uncertain()
    }

    private fun genericTurn(action: GuidanceAction): Phrase {
        return when (action) {
            GuidanceAction.LEFT -> Phrase("Turn left.", "انعطف يساراً.", "turn-left")
            GuidanceAction.SLIGHT_LEFT -> Phrase("Turn slightly left.", "انعطف يساراً قليلاً.", "turn-slight-left")
            GuidanceAction.RIGHT -> Phrase("Turn right.", "انعطف يميناً.", "turn-right")
            GuidanceAction.SLIGHT_RIGHT -> Phrase("Turn slightly right.", "انعطف يميناً قليلاً.", "turn-slight-right")
            GuidanceAction.STRAIGHT -> Phrase("Straight ahead.", "للأمام.", "turn-straight")
            GuidanceAction.HOLD, GuidanceAction.UNKNOWN -> Phrase(
                "Navigation uncertain.",
                "الملاحة غير مؤكدة.",
                "uncertain",
            )
        }
    }

    private fun namedTurn(action: GuidanceAction, label: String, side: String): Phrase {
        val englishName = SpokenLabels.english(label)
        val arabicName = SpokenLabels.arabic(label)
        val generic = genericTurn(action)
        val phrase = when (action) {
            GuidanceAction.SLIGHT_RIGHT -> sidePhrase(
                english = "$englishName on the left. Turn slightly right. Not a measured gap.",
                arabic = "$arabicName على اليسار. انعطف يميناً قليلاً. ليس فراغاً مقاساً.",
                key = generic.key,
            )
            GuidanceAction.RIGHT -> sidePhrase(
                english = "$englishName on the left. Turn right. Not a measured gap.",
                arabic = "$arabicName على اليسار. انعطف يميناً. ليس فراغاً مقاساً.",
                key = generic.key,
            )
            GuidanceAction.SLIGHT_LEFT -> sidePhrase(
                english = "$englishName on the right. Turn slightly left. Not a measured gap.",
                arabic = "$arabicName على اليمين. انعطف يساراً قليلاً. ليس فراغاً مقاساً.",
                key = generic.key,
            )
            GuidanceAction.LEFT -> sidePhrase(
                english = "$englishName on the right. Turn left. Not a measured gap.",
                arabic = "$arabicName على اليمين. انعطف يساراً. ليس فراغاً مقاساً.",
                key = generic.key,
            )
            else -> generic
        }
        return if (side == "left" || side == "right") phrase else generic
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

    private fun sidePhrase(english: String, arabic: String, key: String): Phrase {
        return Phrase(english, arabic, key)
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
        val pitch: Float = VoiceConfig.PITCH_DEFAULT,
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

    /** A committed turn that could not be spoken yet. Spoken once the cooldown and gap allow it. */
    private var queuedTurn: VoiceCue? = null

    private val lastSpokenState = MutableStateFlow("none")
    val lastSpokenText: StateFlow<String> = lastSpokenState.asStateFlow()

    fun consider(input: VoiceGateInput): SpeechDirective = synchronized(lock) {
        val settings = input.settings.sanitized()
        val cue = input.cue
        if (!settings.speechEnabled) {
            queuedTurn = null
            return@synchronized SpeechDirective.Silent
        }
        if (input.nowElapsedMs < unavailableUntilMs) return@synchronized SpeechDirective.Silent
        if (settings.quietMode) queuedTurn = null
        if (cue != null && cue.kind == VoiceAlertKind.TURN_SUGGESTION) {
            if (settings.quietMode && !VoiceConfig.quietModeAllows(cue.kind)) {
                queuedTurn = null
            } else if (!coolingDown(cue, input.nowElapsedMs)) {
                queuedTurn = cue
            }
        }
        val chosen = choose(cue, settings, input.nowElapsedMs) ?: return@synchronized SpeechDirective.Silent
        if (chosen.kind == VoiceAlertKind.TURN_SUGGESTION) {
            queuedTurn = null
        }
        emit(chosen, settings, input.nowElapsedMs, input.resolvedLanguage)
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
        queuedTurn = null
        lastSpokenState.value = "none"
    }

    /**
     * Speaks the current cue when the gate allows it.
     * A turn that arrived during Hold is kept and spoken once, after the gap, even if the next cue is Hold again.
     */
    private fun choose(cue: VoiceCue?, settings: VoiceSettings, now: Long): VoiceCue? {
        if (cue != null && settings.quietMode && !VoiceConfig.quietModeAllows(cue.kind)) {
            return null
        }
        if (cue != null && canEmit(cue, now)) return cue
        val turn = queuedTurn ?: return null
        if (settings.quietMode) return null
        val speaking = activePriority != null && now < speakingUntilMs
        if (cue != null && cue.priority == SpeechPriority.CRITICAL && speaking && !canEmit(cue, now)) {
            return null
        }
        return if (canEmit(turn, now)) turn else null
    }

    private fun canEmit(cue: VoiceCue, now: Long): Boolean {
        if (coolingDown(cue, now)) return false
        val speaking = activePriority != null && now < speakingUntilMs
        val interrupt = speaking && shouldInterrupt(cue)
        if (speaking && !interrupt && cue.delivery == VoiceDelivery.CONTINUOUS) return false
        if (!interrupt && cue.priority != SpeechPriority.CRITICAL && lastSpokenKey != null) {
            val sinceLast = now - lastSpokenAtMs
            if (sinceLast < VoiceConfig.MIN_GAP_MS && cue.dedupeKey != lastSpokenKey &&
                cue.delivery == VoiceDelivery.CONTINUOUS
            ) {
                return false
            }
        }
        return true
    }

    private fun coolingDown(cue: VoiceCue, now: Long): Boolean {
        return cue.dedupeKey == lastSpokenKey && now - lastSpokenAtMs < VoiceConfig.cooldownMs(cue.kind)
    }

    private fun emit(
        cue: VoiceCue,
        settings: VoiceSettings,
        now: Long,
        language: ResolvedVoiceLanguage,
    ): SpeechDirective {
        val speaking = activePriority != null && now < speakingUntilMs
        val interrupt = speaking && shouldInterrupt(cue)
        snapshotKey = lastSpokenKey
        snapshotAtMs = lastSpokenAtMs
        snapshotUntilMs = speakingUntilMs
        snapshotPriority = activePriority
        snapshotText = lastSpokenState.value
        lastSpokenKey = cue.dedupeKey
        lastSpokenAtMs = now
        activePriority = cue.priority
        speakingUntilMs = now + VoiceConfig.UTTERANCE_WINDOW_MS
        val text = cue.text(language)
        lastSpokenState.value = text
        return SpeechDirective.Speak(
            text = text,
            interrupt = interrupt,
            speechRate = settings.speechRate,
            kind = cue.kind,
            language = language,
            pitch = VoiceConfig.pitchFor(cue.kind),
        )
    }

    private fun shouldInterrupt(cue: VoiceCue): Boolean {
        val active = activePriority ?: return false
        if (cue.priority.rank > active.rank) return true
        return cue.priority == SpeechPriority.CRITICAL && cue.dedupeKey != lastSpokenKey
    }
}
