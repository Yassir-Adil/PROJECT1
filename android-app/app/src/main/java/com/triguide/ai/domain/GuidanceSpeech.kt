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
    /** Short hold after the one startup question so it is not stepped on. Obstacles still interrupt. */
    const val FIRST_INTRO_SUPPRESS_MS = 2_500L
    const val RESTART_SUPPRESS_MS = 2_000L

    /** Shown on screen / in docs only — not spoken on open (owner UX 2026-10-18). */
    val disclaimer = SpokenLine(
        id = "disclaimer",
        english = "Research and Simulation Mode. TriGuide is not a certified mobility aid and does not replace a cane, a guide, or your own judgment.",
        arabic = "وضع البحث والمحاكاة. تراي جايد ليست أداة تنقل معتمدة، ولا تغني عن العصا أو المرافق أو حكمك الشخصي.",
    )

    /** The only automatic spoken line on a normal open. */
    val whatsAhead = SpokenLine(
        id = "whats-ahead",
        english = "What's ahead?",
        arabic = "قدامك في شنو؟",
    )

    val guidanceOn = SpokenLine(
        id = "guidance-on",
        english = "Camera started. Guidance on.",
        arabic = "بدأت الكاميرا. التوجيه يعمل.",
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

    /** Navigation cues wait until this time so the open question is not stepped on. */
    var suppressNavigationUntilMs: Long = 0L

    /** Exactly one short open line. Disclaimer / gesture stay off the speaker. */
    fun guidanceStartLines(): List<SpokenLine> {
        return listOf(GuidanceSpeech.whatsAhead)
    }

    fun suppressAfter(lines: List<SpokenLine>, nowElapsedMs: Long) {
        val hold = if (lines.any { it.id == GuidanceSpeech.whatsAhead.id }) {
            GuidanceSpeech.FIRST_INTRO_SUPPRESS_MS
        } else {
            GuidanceSpeech.RESTART_SUPPRESS_MS
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
 * Emits a camera cue only when the phase newly enters FAILED or PERMISSION_REQUIRED.
 * STARTING / STREAMING / IDLE never produce a voice cue here.
 */
class CameraSpeechGate {
    private var lastAnnounced: CameraLifecyclePhase? = null

    fun reset() {
        lastAnnounced = null
    }

    fun cueForTransition(phase: CameraLifecyclePhase): VoiceCue? {
        if (phase == lastAnnounced) return null
        lastAnnounced = phase
        return when (phase) {
            CameraLifecyclePhase.PERMISSION_REQUIRED -> VoiceCues.cameraUnavailable()
            CameraLifecyclePhase.FAILED -> VoiceCues.cameraUnavailable()
            CameraLifecyclePhase.IDLE,
            CameraLifecyclePhase.STARTING,
            CameraLifecyclePhase.STREAMING,
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
