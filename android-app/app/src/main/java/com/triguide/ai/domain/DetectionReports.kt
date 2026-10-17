package com.triguide.ai.domain

import java.util.Locale

enum class DetectorReadiness {
    LOADING,
    READY,
    MODEL_MISSING,
    LOAD_FAILED,
}

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * One successful on-device result. Boxes are pixels in the upright camera frame.
 */
data class DetectionFrame(
    val objects: List<DetectedObject>,
    val imageWidth: Int,
    val imageHeight: Int,
    val mirrorHorizontally: Boolean,
    val inferenceLatencyMs: Double?,
    val fromRealDetector: Boolean,
)

enum class HorizontalZone {
    LEFT,
    CENTER,
    RIGHT,
}

data class OverlayRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

object DetectorConfig {
    const val MODEL_ASSET_PATH = "models/efficientdet_lite0.tflite"
    const val MODEL_DISPLAY_NAME = "MediaPipe EfficientDet-Lite0 int8"
    const val MODEL_SOURCE_URL =
        "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/latest/efficientdet_lite0.tflite"
    const val SCORE_THRESHOLD = 0.5f
    const val MAX_RESULTS = 5
    const val TASKS_VERSION = "1.1.1"
}

object FrameTimestamps {
    fun next(previous: Long, candidate: Long): Long {
        require(candidate >= 0L) { "timestamp must be non-negative" }
        return if (candidate <= previous) previous + 1L else candidate
    }
}

object DetectionReports {
    fun horizontalZone(centerX: Float, imageWidth: Int): HorizontalZone {
        require(imageWidth > 0) { "image width must be positive" }
        val normalized = centerX / imageWidth.toFloat()
        return when {
            normalized < 1f / 3f -> HorizontalZone.LEFT
            normalized > 2f / 3f -> HorizontalZone.RIGHT
            else -> HorizontalZone.CENTER
        }
    }

    /**
     * Maps an upright-frame box onto a view that center-crops the frame (PreviewView FILL_CENTER).
     */
    fun mapCenterCrop(
        boxLeft: Float,
        boxTop: Float,
        boxRight: Float,
        boxBottom: Float,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
        mirrorHorizontally: Boolean,
    ): OverlayRect? {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
            return null
        }
        var left = boxLeft
        var right = boxRight
        if (mirrorHorizontally) {
            val mirroredLeft = imageWidth.toFloat() - right
            val mirroredRight = imageWidth.toFloat() - left
            left = mirroredLeft
            right = mirroredRight
        }
        val scale = maxOf(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
        val offsetX = (viewWidth - imageWidth * scale) / 2f
        val offsetY = (viewHeight - imageHeight * scale) / 2f
        return OverlayRect(
            left = left * scale + offsetX,
            top = boxTop * scale + offsetY,
            right = right * scale + offsetX,
            bottom = boxBottom * scale + offsetY,
        )
    }

    fun formatDetection(detected: DetectedObject, imageWidth: Int): String {
        val confidence = String.format(Locale.US, "%.2f", detected.confidence)
        if (imageWidth <= 0) {
            return "${detected.label} $confidence"
        }
        val centerX = (detected.left + detected.right) / 2f
        val zone = horizontalZone(centerX, imageWidth).name.lowercase(Locale.US)
        return "${detected.label} $confidence $zone"
    }

    fun observation(
        readiness: DetectorReadiness,
        detail: String,
        frame: DetectionFrame?,
        modelName: String,
        scoreThreshold: Float,
    ): EnvironmentObservation {
        if (readiness != DetectorReadiness.READY) {
            val summary = when (readiness) {
                DetectorReadiness.LOADING ->
                    "Object detector is loading. This is not an obstacle reading."
                DetectorReadiness.MODEL_MISSING ->
                    "Object detection model is missing ($detail). No boxes are shown."
                DetectorReadiness.LOAD_FAILED ->
                    "Object detector failed to load ($detail). No boxes are shown."
                DetectorReadiness.READY -> error("ready is handled below")
            }
            return emptyObservation(summary, realDetector = false)
        }
        if (frame == null || !frame.fromRealDetector) {
            return emptyObservation(
                summary = "$modelName is loaded. No detection result yet. This is not an obstacle reading.",
                realDetector = false,
            )
        }
        val threshold = String.format(Locale.US, "%.2f", scoreThreshold)
        if (frame.objects.isEmpty()) {
            return emptyObservation(
                summary = "$modelName found no objects at or above $threshold confidence.",
                realDetector = true,
            )
        }
        val lines = frame.objects.map { formatDetection(it, frame.imageWidth) }
        val noun = if (lines.size == 1) "object" else "objects"
        return EnvironmentObservation(
            summary = "$modelName detected ${lines.size} $noun: ${lines.joinToString(", ")}.",
            detections = lines,
            synthetic = false,
            realDetector = true,
        )
    }

    private fun emptyObservation(summary: String, realDetector: Boolean): EnvironmentObservation {
        return EnvironmentObservation(
            summary = summary,
            detections = emptyList(),
            synthetic = false,
            realDetector = realDetector,
        )
    }
}
