package com.triguide.ai.domain

/**
 * Lines spoken once per situation, not on the camera tick.
 * The research disclaimer is the first line of a guidance session and is not repeated after that.
 */
data class SpokenLine(
    val id: String,
    val english: String,
    val arabic: String,
)

object GuidanceSpeech {
    /**
     * Short hold after the one-time open status so it is not stepped on by non-critical cues.
     * Obstacles still interrupt immediately (see [navigationCueForSpeech]).
     */
    const val FIRST_INTRO_SUPPRESS_MS = 800L
    const val RESTART_SUPPRESS_MS = 800L

    /** Shown on screen / in docs only — not spoken on open (owner UX 2026-10-18). */
    val disclaimer = SpokenLine(
        id = "disclaimer",
        english = "Research and Simulation Mode. TriGuide is not a certified mobility aid and does not replace a cane, a guide, or your own judgment.",
        arabic = "وضع البحث والمحاكاة. تراي جايد ليست أداة تنقل معتمدة، ولا تغني عن العصا أو المرافق أو حكمك الشخصي.",
    )

    /**
     * Optional rhetorical prompt kept for on-demand / future use.
     * Not the automatic open line — a real hazard should be able to lead.
     */
    val whatsAhead = SpokenLine(
        id = "whats-ahead",
        english = "What's ahead?",
        arabic = "قدامك في شنو؟",
    )

    /** One-time open status when speech is on and no session line has run yet. */
    val guidanceOn = SpokenLine(
        id = "guidance-on",
        english = "Guidance on.",
        arabic = "التوجيه يعمل.",
    )

    /** On-screen / log only — not spoken on open. */
    val gestureHint = SpokenLine(
        id = "gesture-hint",
        english = "The fingerprint button cannot control this app. Triple-tap the screen to restart guidance.",
        arabic = "زر البصمة لا يتحكم في هذا التطبيق. انقر ثلاث نقرات سريعة على الشاشة لإعادة التوجيه.",
    )

    val guidancePaused = SpokenLine(
        id = "guidance-paused",
        english = "Guidance paused.",
        arabic = "التوجيه متوقف.",
    )

    val permissionRequired = SpokenLine(
        id = "permission-required",
        english = "Camera permission is required. Allow the camera to start guidance.",
        arabic = "يلزم إذن الكاميرا. اسمح بالكاميرا ليبدأ التوجيه.",
    )

    val permissionDenied = SpokenLine(
        id = "permission-denied",
        english = "Camera permission was not granted. Allow the camera in the dialog to start guidance.",
        arabic = "لم يُمنح إذن الكاميرا. اسمح بالكاميرا في النافذة ليبدأ التوجيه.",
    )

    val openingSettings = SpokenLine(
        id = "opening-settings",
        english = "Opening settings. Allow Camera for TriGuide, then return to the app.",
        arabic = "فتح الإعدادات. اسمح بالكاميرا لتراي جايد، ثم عُد إلى التطبيق.",
    )

    const val GESTURE_ON_SCREEN =
        "Triple-tap the screen to restart guidance. The fingerprint button cannot control this app."
    const val GESTURE_DESCRIPTION =
        "Triple-tap anywhere on Live Vision to restart the camera and hear that guidance is on. The fingerprint reader is the power button. Android does not send those presses to this app. A single tap on More still opens the simulator, settings, and diagnostics."
}

class SessionSpeechTracker {
    /** True after the one open-line has been accepted (or speech is off). */
    var startupLineDelivered: Boolean = false

    /** Non-critical navigation cues wait until this time so the open status is not stepped on. */
    var suppressNavigationUntilMs: Long = 0L

    /**
     * Exactly one short open status. A confirmed hazard may still speak first/only via the
     * navigation gate (critical cues are not held back). Disclaimer / gesture stay off the speaker.
     */
    fun guidanceStartLines(): List<SpokenLine> {
        return listOf(GuidanceSpeech.guidanceOn)
    }

    /**
     * When a hazard is already confirmed at open, skip the status line so the warning can be alone.
     */
    fun markStartupSkippedForHazard(nowElapsedMs: Long) {
        startupLineDelivered = true
        suppressNavigationUntilMs = nowElapsedMs
    }

    fun suppressAfter(lines: List<SpokenLine>, nowElapsedMs: Long) {
        val hold = if (lines.isEmpty()) {
            0L
        } else {
            GuidanceSpeech.FIRST_INTRO_SUPPRESS_MS
        }
        suppressNavigationUntilMs = nowElapsedMs + hold
    }
}

/**
 * Camera lifecycle for speech. Startup and streaming never invent a spoken
 * "camera unavailable" from the navigation tick.
 */
enum class CameraLifecyclePhase {
    IDLE,
    STARTING,
    STREAMING,
    FAILED,
    PERMISSION_REQUIRED,
}

/**
 * Emits a camera cue once per unavailable episode, and once on meaningful recovery.
 * STARTING / IDLE never speak and do not end an unavailable episode. Only STREAMING
 * clears the latch (one "Camera ready"). Clean startup STREAMING stays silent.
 */
class CameraSpeechGate {
    private var unavailableEpisodeActive: Boolean = false
    private var unavailableAnnounced: Boolean = false

    fun reset() {
        unavailableEpisodeActive = false
        unavailableAnnounced = false
    }

    fun cueForTransition(phase: CameraLifecyclePhase): VoiceCue? {
        return when (phase) {
            CameraLifecyclePhase.PERMISSION_REQUIRED,
            CameraLifecyclePhase.FAILED,
            -> {
                unavailableEpisodeActive = true
                if (unavailableAnnounced) {
                    null
                } else {
                    unavailableAnnounced = true
                    VoiceCues.cameraUnavailable()
                }
            }
            CameraLifecyclePhase.STREAMING -> {
                if (!unavailableEpisodeActive) {
                    null
                } else {
                    unavailableEpisodeActive = false
                    unavailableAnnounced = false
                    VoiceCues.cameraRestored()
                }
            }
            CameraLifecyclePhase.IDLE,
            CameraLifecyclePhase.STARTING,
            -> null
        }
    }
}

/**
 * A committed turn is returned so the gate can enqueue it. It is not dropped because the introduction is still open.
 * An obstacle still passes. Other non-critical cues wait. "Camera unavailable" stays quiet during startup and that introduction.
 */
fun navigationCueForSpeech(
    cue: VoiceCue?,
    nowElapsedMs: Long,
    guidanceStartedAtMs: Long,
    suppressNavigationUntilMs: Long,
): VoiceCue? {
    if (cue == null) return null
    val introHolding = nowElapsedMs < suppressNavigationUntilMs
    // Camera-unavailable must not ride the navigation tick during startup.
    if (cue.kind == VoiceAlertKind.CAMERA_UNAVAILABLE) {
        return null
    }
    if (introHolding &&
        cue.kind != VoiceAlertKind.TURN_SUGGESTION &&
        cue.priority != SpeechPriority.CRITICAL
    ) {
        return null
    }
    return cue
}
