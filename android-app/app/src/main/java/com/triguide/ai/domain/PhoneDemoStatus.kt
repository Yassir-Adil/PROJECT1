package com.triguide.ai.domain

/**
 * Short status lines for the phone screens.
 * These phrases describe the real detector state. They do not invent a detection.
 */
object DetectorStatusText {
    fun line(readiness: DetectorReadiness, detail: String): String {
        val trimmed = detail.trim()
        val suffix = if (trimmed.isEmpty()) "" else " $trimmed"
        return when (readiness) {
            DetectorReadiness.READY -> "READY.$suffix"
            DetectorReadiness.LOADING -> "Loading. No boxes yet.$suffix"
            DetectorReadiness.MODEL_MISSING -> "Not ready. The model file is missing.$suffix"
            DetectorReadiness.LOAD_FAILED -> "Not ready. The model failed to load.$suffix"
        }
    }
}

enum class CameraPermissionAction {
    /** The system can still show the camera permission dialog. */
    RequestInApp,

    /** A previous denial will not show the dialog again. Send the user to system settings. */
    OpenAppSettings,
}

/**
 * Xiaomi and HyperOS often stop showing the permission dialog after one denial.
 * The first request still goes through the in-app dialog.
 */
object CameraPermissionPrompt {
    fun action(requestAttempted: Boolean, shouldShowRationale: Boolean): CameraPermissionAction {
        if (!requestAttempted || shouldShowRationale) {
            return CameraPermissionAction.RequestInApp
        }
        return CameraPermissionAction.OpenAppSettings
    }
}
