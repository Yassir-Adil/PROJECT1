package com.triguide.ai.domain

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Steering choice for the virtual robot.
 * Positive wheel angle is a left turn, matching [com.triguide.ai.sim.TricycleKinematics].
 */
enum class GuidanceAction(val userLabel: String) {
    STRAIGHT("Straight"),
    SLIGHT_LEFT("Slight left"),
    LEFT("Left"),
    SLIGHT_RIGHT("Slight right"),
    RIGHT("Right"),
    HOLD("Hold"),
    UNKNOWN("Unknown"),
}

enum class PerceptionMode {
    /** Upright camera boxes. Horizontal position only. */
    CAMERA_2D,

    /** Authored simulator obstacles with known planar positions. */
    SYNTHETIC_WORLD,

    /** No usable sample. */
    UNAVAILABLE,
}

/**
 * One detector box reduced to image position.
 * [centerXNormalized] is 0 at the left edge of the preview and 1 at the right edge.
 * [areaFraction] is box area divided by frame area. It is not a distance.
 */
data class DirectionalCue(
    val label: String,
    val confidence: Float,
    val centerXNormalized: Float,
    val areaFraction: Float,
)

/**
 * Obstacle in the robot frame for the synthetic map.
 * [forwardMeters] is ahead of the rear axle. [lateralMeters] is positive to the left.
 * These meters are authored. They are never inferred from a camera box.
 */
data class KnownObstacle(
    val name: String,
    val forwardMeters: Double,
    val lateralMeters: Double,
)

data class SceneObservation(
    val cues: List<DirectionalCue>,
    val mode: PerceptionMode,
    val realDetector: Boolean,
    val metricDepthAvailable: Boolean,
    val frameAgeMs: Long?,
    val contradictory: Boolean = false,
    val knownObstacles: List<KnownObstacle> = emptyList(),
    /** Synthetic map only. True when [knownObstacles] is the full authored set for this pose. */
    val catalogComplete: Boolean = false,
    val summary: String = "",
)

object FloorSegmentationStatus {
    const val STATE = "NOT_STARTED"
    const val DETAIL =
        "Disabled. No floor model is loaded. Open floor is not inferred, and no floor mask is drawn."
}

object NavigationConfig {
    const val STALE_FRAME_MS = 1_000L
    const val MIN_CUE_CONFIDENCE = 0.50f
    const val CENTER_START = 1f / 3f
    const val CENTER_END = 2f / 3f

    /** A left-third box whose center is at or past this value sits near the center band. */
    const val LEFT_INNER_CENTER = CENTER_START - 0.08f

    /** A right-third box whose center is at or before this value sits near the center band. */
    const val RIGHT_INNER_CENTER = CENTER_END + 0.08f

    /** Share of the frame. A weight for image occupancy, not a range in meters. */
    const val STRONG_AREA_FRACTION = 0.12f
    const val RISK_AREA_FLOOR = 0.01f
    const val DOMINANCE_RATIO = 1.8f
    const val MAX_CAMERA_CONFIDENCE = 0.75f

    const val CONFIRM_FRAMES = 3
    const val FLIP_FRAMES = 5

    const val SPEED_STRAIGHT_MPS = 0.40
    const val SPEED_SLIGHT_MPS = 0.25
    const val SPEED_TURN_MPS = 0.20
    const val STEER_SLIGHT_RADIANS = 0.15
    const val STEER_FULL_RADIANS = 0.35

    const val LOOKAHEAD_METERS = 1.6
    const val MIN_FORWARD_METERS = 0.05
    const val CORRIDOR_HALF_WIDTH_METERS = 0.50
    const val CENTER_DEADBAND_METERS = 0.12
    const val SLIGHT_LATERAL_METERS = 0.30

    const val CAMERA_LABEL = "Camera 2D"
    const val SYNTHETIC_LABEL = "Synthetic map"
    const val NONE_LABEL = "None"
    const val MANUAL_LABEL = "Manual"

    fun speedAndSteer(action: GuidanceAction): Pair<Double, Double> {
        return when (action) {
            GuidanceAction.STRAIGHT -> SPEED_STRAIGHT_MPS to 0.0
            GuidanceAction.SLIGHT_LEFT -> SPEED_SLIGHT_MPS to STEER_SLIGHT_RADIANS
            GuidanceAction.LEFT -> SPEED_TURN_MPS to STEER_FULL_RADIANS
            GuidanceAction.SLIGHT_RIGHT -> SPEED_SLIGHT_MPS to -STEER_SLIGHT_RADIANS
            GuidanceAction.RIGHT -> SPEED_TURN_MPS to -STEER_FULL_RADIANS
            GuidanceAction.HOLD, GuidanceAction.UNKNOWN -> 0.0 to 0.0
        }
    }

    fun sameFamily(left: GuidanceAction, right: GuidanceAction): Boolean {
        return family(left) != 0 && family(left) == family(right)
    }

    fun oppositeFamily(left: GuidanceAction, right: GuidanceAction): Boolean {
        val a = family(left)
        val b = family(right)
        return a != 0 && b != 0 && a != b
    }

    private fun family(action: GuidanceAction): Int {
        return when (action) {
            GuidanceAction.SLIGHT_LEFT, GuidanceAction.LEFT -> -1
            GuidanceAction.SLIGHT_RIGHT, GuidanceAction.RIGHT -> 1
            else -> 0
        }
    }
}

object NavigationSpeech {
    fun of(action: GuidanceAction, reason: String, confidence: Float, perceptionLabel: String): String {
        val percent = (confidence.coerceIn(0f, 1f) * 100f).roundToInt()
        return "$perceptionLabel. ${action.userLabel}. $reason Confidence $percent percent."
    }
}

object NavigationDecisions {
    fun of(
        action: GuidanceAction,
        reason: String,
        confidence: Float,
        linearSpeedMps: Double,
        steeringRadians: Double,
        basedOnRealVision: Boolean,
        metricDepthUsed: Boolean,
        perceptionLabel: String,
    ): NavigationDecision {
        val bounded = confidence.coerceIn(0f, 1f)
        return NavigationDecision(
            action = action,
            reason = reason,
            confidence = bounded,
            linearSpeedMps = linearSpeedMps,
            steeringRadians = steeringRadians,
            spokenCue = NavigationSpeech.of(action, reason, bounded, perceptionLabel),
            basedOnRealVision = basedOnRealVision,
            metricDepthUsed = metricDepthUsed,
            perceptionLabel = perceptionLabel,
        )
    }

    fun fromAction(
        action: GuidanceAction,
        reason: String,
        confidence: Float,
        basedOnRealVision: Boolean,
        metricDepthUsed: Boolean,
        perceptionLabel: String,
    ): NavigationDecision {
        val (speed, steer) = NavigationConfig.speedAndSteer(action)
        return of(
            action = action,
            reason = reason,
            confidence = confidence,
            linearSpeedMps = speed,
            steeringRadians = steer,
            basedOnRealVision = basedOnRealVision,
            metricDepthUsed = metricDepthUsed,
            perceptionLabel = perceptionLabel,
        )
    }

    fun unavailable(): NavigationDecision {
        return fromAction(
            action = GuidanceAction.UNKNOWN,
            reason = "Navigation has not received an observation.",
            confidence = 0f,
            basedOnRealVision = false,
            metricDepthUsed = false,
            perceptionLabel = NavigationConfig.NONE_LABEL,
        )
    }

    fun manual(): NavigationDecision {
        return fromAction(
            action = GuidanceAction.HOLD,
            reason = "Buttons are driving the virtual robot. The navigation engine is not applied.",
            confidence = 0f,
            basedOnRealVision = false,
            metricDepthUsed = false,
            perceptionLabel = NavigationConfig.MANUAL_LABEL,
        )
    }

    fun formatConfidence(confidence: Float): String {
        return String.format(Locale.US, "%.2f", confidence.coerceIn(0f, 1f))
    }
}
