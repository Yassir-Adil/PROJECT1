package com.triguide.ai.domain

/**
 * Builds a camera observation from a real detector frame.
 * Box centers are expressed in preview space. A mirrored front camera is flipped
 * so "left" matches the picture the user sees. No metric depth is attached.
 */
object SceneObservations {
    fun fromCamera(
        readiness: DetectorReadiness,
        frame: DetectionFrame?,
        nowElapsedMs: Long,
    ): SceneObservation {
        if (readiness != DetectorReadiness.READY || frame == null || !frame.fromRealDetector) {
            return SceneObservation(
                cues = emptyList(),
                mode = PerceptionMode.UNAVAILABLE,
                realDetector = false,
                metricDepthAvailable = false,
                frameAgeMs = null,
                summary = "No real detection frame.",
            )
        }
        if (frame.imageWidth <= 0 || frame.imageHeight <= 0) {
            return SceneObservation(
                cues = emptyList(),
                mode = PerceptionMode.CAMERA_2D,
                realDetector = true,
                metricDepthAvailable = false,
                frameAgeMs = null,
                contradictory = true,
                summary = "Camera frame has no size.",
            )
        }
        val width = frame.imageWidth.toFloat()
        val height = frame.imageHeight.toFloat()
        val cues = frame.objects.map { detected ->
            val rawCenter = (detected.left + detected.right) / 2f
            val center = if (frame.mirrorHorizontally) {
                frame.imageWidth.toFloat() - rawCenter
            } else {
                rawCenter
            }
            val boxWidth = (detected.right - detected.left).coerceAtLeast(0f)
            val boxHeight = (detected.bottom - detected.top).coerceAtLeast(0f)
            val centerY = (detected.top + detected.bottom) / 2f
            DirectionalCue(
                label = detected.label,
                confidence = detected.confidence,
                centerXNormalized = center / width,
                areaFraction = (boxWidth * boxHeight) / (width * height),
                centerYNormalized = (centerY / height).coerceIn(0f, 1f),
            )
        }
        val age = frame.completedAtElapsedMs?.let { completed ->
            if (nowElapsedMs < completed) null else nowElapsedMs - completed
        }
        return SceneObservation(
            cues = cues,
            mode = PerceptionMode.CAMERA_2D,
            realDetector = true,
            metricDepthAvailable = false,
            frameAgeMs = age,
            summary = "Camera boxes in preview space.",
        )
    }
}
