package com.triguide.ai.domain

/**
 * Calm Live Vision copy and priority state.
 * Technical model/FPS/reason/wheel/last-cue details stay on Diagnostics.
 */
data class LiveVisionPriority(
    val label: String,
    val isError: Boolean,
)

object LiveVisionStatus {
    /** Compact on-screen safety; full disclaimer remains in Settings. */
    const val SAFETY_LINE = "Research mode · Not a mobility aid"

    /** Two short, non-pressable shortcut lines for the running screen. */
    const val TIP_LINE_ONE = "↑×3 Describe · ↓×3 More"
    const val TIP_LINE_TWO = "→×3 Status · ←×3 / 3 taps Restart"
    const val BRIEF_TIPS = "$TIP_LINE_ONE\n$TIP_LINE_TWO"

    /** Stable TalkBack for the preview. Does not announce changing detector labels. */
    const val PREVIEW_DESCRIPTION =
        "Live camera preview. The only control while running is emergency stop — tap once. " +
            "Boxes are for debugging and are unverified. " +
            "Swipe up three times to describe this frame. Swipe down three times for More. " +
            "Swipe right three times to hear status. Swipe left three times or triple-tap to restart. " +
            "With TalkBack, use the custom actions on this screen."

    /** Visible gesture strip — not a button. */
    const val GESTURE_HINT = BRIEF_TIPS

    /** Action alone is not evidence of a walkable corridor, side path, or junction. */
    fun guidancePrompt(action: GuidanceAction): String {
        return when (action) {
            GuidanceAction.HOLD -> "Stop · check with cane"
            // This presenter has no positive walkable-area or branch evidence. A movement
            // action by itself must not become a walking instruction on the phone screen.
            else -> "Path not confirmed · stop and check with cane"
        }
    }

    /** The explicit status shortcut speaks one short line, in the selected language. */
    fun spokenStatus(priority: LiveVisionPriority, language: ResolvedVoiceLanguage): String {
        if (language == ResolvedVoiceLanguage.ENGLISH) return priority.label
        return when (priority.label) {
            "Guidance paused" -> "التوجيه متوقف"
            "Camera permission needed" -> "إذن الكاميرا مطلوب"
            "Camera unavailable" -> "الكاميرا غير متاحة"
            "Detection unavailable" -> "التعرف غير متاح"
            "Vision temporarily unavailable" -> "الرؤية غير متاحة مؤقتاً"
            "Starting camera" -> "جاري تشغيل الكاميرا"
            "Stop and check" -> "توقف وتحقق"
            "Possible obstacle · height unknown" -> "عقبة محتملة؛ ارتفاعها غير مقاس"
            else -> "المسار غير مؤكد"
        }
    }

    /**
     * One high-priority line for the default Live Vision screen.
     * Errors and permission outrank Stop and check / Path not confirmed.
     */
    fun priority(
        running: Boolean,
        permissionGranted: Boolean,
        cameraState: String,
        readiness: DetectorReadiness,
        detail: String,
        inferenceError: String?,
        action: GuidanceAction,
        pathRisk: PathRiskLevel = PathRiskLevel.UNKNOWN,
    ): LiveVisionPriority {
        if (!running) {
            return LiveVisionPriority("Guidance paused", isError = false)
        }
        if (!permissionGranted) {
            return LiveVisionPriority("Camera permission needed", isError = true)
        }
        if (cameraState.startsWith("Failed") || cameraState.startsWith("No camera")) {
            return LiveVisionPriority("Camera unavailable", isError = true)
        }
        when (readiness) {
            DetectorReadiness.MODEL_MISSING,
            DetectorReadiness.LOAD_FAILED ->
                return LiveVisionPriority("Detection unavailable", isError = true)
            DetectorReadiness.LOADING,
            DetectorReadiness.READY -> Unit
        }
        if (!inferenceError.isNullOrBlank()) {
            return LiveVisionPriority("Vision temporarily unavailable", isError = true)
        }
        // Idle/starting with permission: do not surface a stale Hold before the stream.
        if (
            cameraState == "Idle" ||
            cameraState == "Starting camera" ||
            readiness == DetectorReadiness.LOADING
        ) {
            return LiveVisionPriority("Starting camera", isError = false)
        }
        return when (action) {
            GuidanceAction.HOLD -> when (pathRisk) {
                PathRiskLevel.POSSIBLE_OBSTACLE -> LiveVisionPriority("Possible obstacle · height unknown", isError = false)
                PathRiskLevel.UNKNOWN, PathRiskLevel.CLEAR_OBSERVED -> LiveVisionPriority("Path not confirmed", isError = false)
                PathRiskLevel.BLOCKED -> LiveVisionPriority("Stop and check", isError = false)
            }
            GuidanceAction.UNKNOWN,
            GuidanceAction.STRAIGHT,
            GuidanceAction.SLIGHT_LEFT,
            GuidanceAction.LEFT,
            GuidanceAction.SLIGHT_RIGHT,
            GuidanceAction.RIGHT -> LiveVisionPriority("Path not confirmed", isError = false)
        }
    }
}
