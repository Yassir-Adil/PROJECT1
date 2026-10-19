package com.triguide.ai.domain

/**
 * Builds a camera observation from a real detector frame.
 * Box centers and bounds are expressed in the upright camera frame used by the preview.
 * A mirrored front camera is flipped
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
                sampleCompletedAtElapsedMs = null,
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
                sampleCompletedAtElapsedMs = frame.completedAtElapsedMs,
                contradictory = true,
                summary = "Camera frame has no size.",
            )
        }
        val width = frame.imageWidth.toFloat()
        val height = frame.imageHeight.toFloat()
        val cues = frame.objects.map { detected ->
            val rawLeft = detected.left / width
            val rawRight = detected.right / width
            val left = if (frame.mirrorHorizontally) 1f - rawRight else rawLeft
            val right = if (frame.mirrorHorizontally) 1f - rawLeft else rawRight
            val top = detected.top / height
            val bottom = detected.bottom / height
            val boxWidth = (detected.right - detected.left).coerceAtLeast(0f)
            val boxHeight = (detected.bottom - detected.top).coerceAtLeast(0f)
            DirectionalCue(
                label = detected.label,
                confidence = detected.confidence,
                centerXNormalized = (left + right) / 2f,
                areaFraction = (boxWidth * boxHeight) / (width * height),
                centerYNormalized = (top + bottom) / 2f,
                boxLeftNormalized = left,
                boxRightNormalized = right,
                boxTopNormalized = top,
                boxBottomNormalized = bottom,
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
            sampleCompletedAtElapsedMs = frame.completedAtElapsedMs,
            summary = "Camera boxes in preview space.",
        )
    }
}
