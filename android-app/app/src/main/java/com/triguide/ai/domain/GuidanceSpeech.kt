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
    const val FIRST_INTRO_SUPPRESS_MS = 16_000L
    const val RESTART_SUPPRESS_MS = 4_000L

    val disclaimer = SpokenLine(
        id = "disclaimer",
        english = "Research and Simulation Mode. TriGuide is not a certified mobility aid and does not replace a cane, a guide, or your own judgment.",
        arabic = "وضع البحث والمحاكاة. تراي جايد ليست أداة تنقل معتمدة، ولا تغني عن العصا أو المرافق أو حكمك الشخصي.",
    )

    val guidanceOn = SpokenLine(
        id = "guidance-on",
        english = "Camera started. Guidance on.",
        arabic = "بدأت الكاميرا. التوجيه يعمل.",
    )

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
    var disclaimerDelivered: Boolean = false

    /** Navigation cues wait until this time so the disclaimer is not spoken on every frame. */
    var suppressNavigationUntilMs: Long = 0L

    fun guidanceStartLines(): List<SpokenLine> {
        return if (disclaimerDelivered) {
            listOf(GuidanceSpeech.guidanceOn)
        } else {
            listOf(
                GuidanceSpeech.disclaimer,
                GuidanceSpeech.guidanceOn,
                GuidanceSpeech.gestureHint,
            )
        }
    }

    fun suppressAfter(lines: List<SpokenLine>, nowElapsedMs: Long) {
        val hold = if (lines.any { it.id == GuidanceSpeech.disclaimer.id }) {
            GuidanceSpeech.FIRST_INTRO_SUPPRESS_MS
        } else {
            GuidanceSpeech.RESTART_SUPPRESS_MS
        }
        suppressNavigationUntilMs = nowElapsedMs + hold
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
    if (cue.kind == VoiceAlertKind.CAMERA_UNAVAILABLE) {
        val starting = nowElapsedMs - guidanceStartedAtMs < VoiceConfig.CAMERA_START_GRACE_MS
        if (starting || introHolding) return null
    }
    if (introHolding &&
        cue.kind != VoiceAlertKind.TURN_SUGGESTION &&
        cue.priority != SpeechPriority.CRITICAL
    ) {
        return null
    }
    return cue
}
