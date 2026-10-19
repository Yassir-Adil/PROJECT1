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
 * Optional bounds are in the same mirrored, upright frame as the center. All four
 * are present for real camera boxes; nulls preserve legacy/synthetic call sites.
 */
data class DirectionalCue(
    val label: String,
    val confidence: Float,
    val centerXNormalized: Float,
    val areaFraction: Float,
    /** 0 at the top of the preview, 1 at the bottom. Default mid-frame for older call sites. */
    val centerYNormalized: Float = 0.55f,
    val boxLeftNormalized: Float? = null,
    val boxRightNormalized: Float? = null,
    val boxTopNormalized: Float? = null,
    val boxBottomNormalized: Float? = null,
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
    /**
     * Monotonic completion time of the successful camera inference that produced this sample.
     * Camera temporal votes count distinct newer identities, not 50 ms UI-loop replays.
     * Synthetic observations leave this null.
     */
    val sampleCompletedAtElapsedMs: Long? = null,
    val contradictory: Boolean = false,
    val knownObstacles: List<KnownObstacle> = emptyList(),
    /** Synthetic map only. True when [knownObstacles] is the full authored set for this pose. */
    val catalogComplete: Boolean = false,
    val summary: String = "",
)

object FloorSegmentationStatus {
    const val STATE = "DIAGNOSTIC_SEAM"
    const val DETAIL =
        "Diagnostic segmentation seam present. No floor model is bundled or validated. " +
            "Class mapping UNMAPPED. ADE20K / SegFormer weights are not in the APK. " +
            "Object detections do not prove an open path. " +
            "Mask output is diagnostic-only and is not connected to navigation or speech. " +
            "Live AI Vision mode draws PathRisk corridor colors only (research overlay)."
}

object NavigationConfig {
    const val STALE_FRAME_MS = 1_000L
    const val MIN_CUE_CONFIDENCE = DetectorConfig.SCORE_THRESHOLD

    /**
     * True center band. A box whose center lies here holds.
     * It is not a distance, and it is narrower than the middle third.
     */
    const val CENTER_START = 0.42f
    const val CENTER_END = 0.58f

    /**
     * Outer thirds. A dominant box here prefers a slight side turn.
     * A box still outside the center band, but nearer it, prefers a fuller turn.
     * A center box, or one opposite box, cancels that turn only when it is similarly strong.
     */
    const val LEFT_THIRD_END = 0.33f
    const val RIGHT_THIRD_START = 0.67f

    /** A left box whose center is at or past this value sits near the center band, so the turn is full. */
    const val LEFT_INNER_CENTER = LEFT_THIRD_END

    /** A right box whose center is at or before this value sits near the center band, so the turn is full. */
    const val RIGHT_INNER_CENTER = RIGHT_THIRD_START

    /** Floor used only so a tiny box still has a weight. Not a range. */
    const val RISK_AREA_FLOOR = 0.01f

    /**
     * An opposite or center box at least this fraction of the dominant score×area is similarly strong.
     * Weaker boxes on the other side do not add together into a both-sides hold.
     */
    const val OPPOSITE_SIMILAR_FRACTION = 0.70f

    /** One box is clearly largest when its area is at least this multiple of the next box. */
    const val CLEAR_AREA_RATIO = 1.5f

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
        subjectLabel: String = "",
        subjectSide: String = "",
        pathRisk: PathRiskLevel = PathRiskLevel.UNKNOWN,
        diagnostic: String = "",
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
            subjectLabel = subjectLabel,
            subjectSide = subjectSide,
            pathRisk = pathRisk,
            diagnostic = diagnostic,
        )
    }

    fun fromAction(
        action: GuidanceAction,
        reason: String,
        confidence: Float,
        basedOnRealVision: Boolean,
        metricDepthUsed: Boolean,
        perceptionLabel: String,
        subjectLabel: String = "",
        subjectSide: String = "",
        pathRisk: PathRiskLevel = PathRiskLevel.UNKNOWN,
        diagnostic: String = "",
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
            subjectLabel = subjectLabel,
            subjectSide = subjectSide,
            pathRisk = pathRisk,
            diagnostic = diagnostic,
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
