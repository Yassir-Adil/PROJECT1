package com.triguide.ai.domain

import java.util.Locale
import kotlin.math.abs

/**
 * Turns one observation into a virtual steering choice.
 *
 * Camera samples use the horizontal center of each box. They do not produce meters.
 * An empty frame, a center box, or two competing sides yields Hold or Unknown.
 * Straight is reserved for the synthetic map, where obstacle positions are authored.
 */
class NavigationDecisionEngine : NavigationDecisionPolicy {
    override fun decide(observation: SceneObservation): NavigationDecision {
        return when (observation.mode) {
            PerceptionMode.SYNTHETIC_WORLD -> decideSynthetic(observation)
            PerceptionMode.CAMERA_2D -> decideCamera(observation)
            PerceptionMode.UNAVAILABLE -> unknown(
                reason = "No real camera frame is available. Open space is not assumed.",
                observation = observation,
                label = NavigationConfig.NONE_LABEL,
            )
        }
    }

    private fun decideCamera(observation: SceneObservation): NavigationDecision {
        val label = NavigationConfig.CAMERA_LABEL
        if (!observation.realDetector) {
            return unknown(
                reason = "No real camera frame is available. Open space is not assumed.",
                observation = observation,
                label = NavigationConfig.NONE_LABEL,
            )
        }
        if (observation.contradictory || observation.metricDepthAvailable || cuesAreInvalid(observation.cues)) {
            return unknown(
                reason = "The camera observation is contradictory. No direction is chosen. Depth is not measured.",
                observation = observation,
                label = label,
            )
        }
        val age = observation.frameAgeMs
        if (age == null) {
            return hold(
                reason = "The camera frame has no timestamp. The robot holds. Depth is not measured.",
                observation = observation,
                label = label,
                confidence = 0.40f,
            )
        }
        if (age >= NavigationConfig.STALE_FRAME_MS) {
            return hold(
                reason = "The camera frame is stale (${age} ms). The robot holds. Depth is not measured.",
                observation = observation,
                label = label,
                confidence = 0.60f,
            )
        }
        val usable = observation.cues.filter { cue ->
            cue.confidence >= NavigationConfig.MIN_CUE_CONFIDENCE &&
                cue.confidence <= 1f &&
                cue.areaFraction > 0f &&
                cue.centerXNormalized in 0f..1f
        }
        if (usable.isEmpty()) {
            return hold(
                reason = "No confirmed passage. An empty detection is not open space. Depth is not measured.",
                observation = observation,
                label = label,
                confidence = 0.55f,
            )
        }
        val left = usable.filter { it.centerXNormalized < NavigationConfig.CENTER_START }
        val right = usable.filter { it.centerXNormalized > NavigationConfig.CENTER_END }
        val center = usable.filter { it.centerXNormalized in NavigationConfig.CENTER_START..NavigationConfig.CENTER_END }
        if (center.isNotEmpty()) {
            val name = displayLabel(center.maxBy { it.confidence })
            return hold(
                reason = "$name is in the center of the image. No confirmed passage to either side. Depth is not measured.",
                observation = observation,
                label = label,
                confidence = cameraConfidence(center.maxBy { it.confidence }),
            )
        }
        if (left.isNotEmpty() && right.isNotEmpty()) {
            val leftRisk = risk(left)
            val rightRisk = risk(right)
            if (leftRisk >= rightRisk * NavigationConfig.DOMINANCE_RATIO && leftRisk > rightRisk) {
                return steerAwayFrom(side = ImageSide.LEFT, cues = left, observation = observation)
            }
            if (rightRisk >= leftRisk * NavigationConfig.DOMINANCE_RATIO && rightRisk > leftRisk) {
                return steerAwayFrom(side = ImageSide.RIGHT, cues = right, observation = observation)
            }
            return hold(
                reason = "Boxes are on both sides of the image. No confirmed passage. Depth is not measured.",
                observation = observation,
                label = label,
                confidence = cameraConfidence(usable.maxBy { it.confidence }),
            )
        }
        if (left.isNotEmpty()) {
            return steerAwayFrom(side = ImageSide.LEFT, cues = left, observation = observation)
        }
        if (right.isNotEmpty()) {
            return steerAwayFrom(side = ImageSide.RIGHT, cues = right, observation = observation)
        }
        return hold(
            reason = "No confirmed passage. An empty detection is not open space. Depth is not measured.",
            observation = observation,
            label = label,
            confidence = 0.55f,
        )
    }

    private fun decideSynthetic(observation: SceneObservation): NavigationDecision {
        val label = NavigationConfig.SYNTHETIC_LABEL
        if (observation.contradictory || obstaclesAreInvalid(observation.knownObstacles)) {
            return unknown(
                reason = "Synthetic map observations disagree, so no direction is chosen.",
                observation = observation,
                label = label,
            )
        }
        if (!observation.metricDepthAvailable || !observation.catalogComplete) {
            return NavigationDecisions.fromAction(
                action = GuidanceAction.HOLD,
                reason = "Synthetic map is incomplete. No forward corridor is confirmed.",
                confidence = 0.50f,
                basedOnRealVision = false,
                metricDepthUsed = false,
                perceptionLabel = label,
            )
        }
        val blocking = observation.knownObstacles.filter { obstacle ->
            obstacle.forwardMeters in NavigationConfig.MIN_FORWARD_METERS..NavigationConfig.LOOKAHEAD_METERS &&
                abs(obstacle.lateralMeters) <= NavigationConfig.CORRIDOR_HALF_WIDTH_METERS
        }
        if (blocking.isEmpty()) {
            return NavigationDecisions.fromAction(
                action = GuidanceAction.STRAIGHT,
                reason = "Synthetic map: the authored list has no obstacle in the forward corridor. This is a simulator map.",
                confidence = 0.80f,
                basedOnRealVision = false,
                metricDepthUsed = true,
                perceptionLabel = label,
            )
        }
        val nearestForward = blocking.minOf { it.forwardMeters }
        val nearest = blocking.filter { abs(it.forwardMeters - nearestForward) < 1e-6 }
        val onAxis = nearest.filter { abs(it.lateralMeters) <= NavigationConfig.CENTER_DEADBAND_METERS }
        if (onAxis.isNotEmpty()) {
            return hold(
                reason = "Synthetic map: ${onAxis.first().name} is on the forward axis. The virtual robot holds.",
                observation = observation,
                label = label,
                confidence = 0.85f,
            )
        }
        val hasLeft = nearest.any { it.lateralMeters > 0.0 }
        val hasRight = nearest.any { it.lateralMeters < 0.0 }
        if (hasLeft && hasRight) {
            return hold(
                reason = "Synthetic map: authored obstacles are on both sides at the same distance. The virtual robot holds.",
                observation = observation,
                label = label,
                confidence = 0.80f,
            )
        }
        val chosen = nearest.minBy { abs(it.lateralMeters) }
        val away = if (chosen.lateralMeters > 0.0) ImageSide.LEFT else ImageSide.RIGHT
        val strong = abs(chosen.lateralMeters) < NavigationConfig.SLIGHT_LATERAL_METERS
        val action = turnAction(away, strong)
        return NavigationDecisions.fromAction(
            action = action,
            reason = "Synthetic map: ${chosen.name} is ${sideWord(away)} of the virtual robot. Steering ${action.userLabel.lowercase(Locale.US)}. This is a simulator map.",
            confidence = 0.80f,
            basedOnRealVision = false,
            metricDepthUsed = true,
            perceptionLabel = label,
        )
    }

    private fun steerAwayFrom(
        side: ImageSide,
        cues: List<DirectionalCue>,
        observation: SceneObservation,
    ): NavigationDecision {
        val strongest = cues.maxBy { it.confidence * it.areaFraction.coerceAtLeast(NavigationConfig.RISK_AREA_FLOOR) }
        val nearCenter = when (side) {
            ImageSide.LEFT -> strongest.centerXNormalized >= NavigationConfig.LEFT_INNER_CENTER
            ImageSide.RIGHT -> strongest.centerXNormalized <= NavigationConfig.RIGHT_INNER_CENTER
        }
        val strong = strongest.areaFraction >= NavigationConfig.STRONG_AREA_FRACTION || nearCenter
        val action = turnAction(side, strong)
        val name = displayLabel(strongest)
        val areaPercent = (strongest.areaFraction.coerceIn(0f, 1f) * 100f).toInt()
        val detectorConfidence = String.format(Locale.US, "%.2f", strongest.confidence)
        val awayWord = if (side == ImageSide.LEFT) "right" else "left"
        return NavigationDecisions.fromAction(
            action = action,
            reason = "$name is on the ${sideWord(side)} of the image " +
                "(detector confidence $detectorConfidence, $areaPercent percent of the frame). " +
                "Steering $awayWord. Depth is not measured. The $awayWord side is not a confirmed gap.",
            confidence = cameraConfidence(strongest),
            basedOnRealVision = observation.realDetector,
            metricDepthUsed = false,
            perceptionLabel = NavigationConfig.CAMERA_LABEL,
        )
    }

    private fun turnAction(occupiedSide: ImageSide, strong: Boolean): GuidanceAction {
        return when (occupiedSide) {
            ImageSide.LEFT -> if (strong) GuidanceAction.RIGHT else GuidanceAction.SLIGHT_RIGHT
            ImageSide.RIGHT -> if (strong) GuidanceAction.LEFT else GuidanceAction.SLIGHT_LEFT
        }
    }

    private fun sideWord(side: ImageSide): String {
        return if (side == ImageSide.LEFT) "left" else "right"
    }

    private fun hold(
        reason: String,
        observation: SceneObservation,
        label: String,
        confidence: Float,
    ): NavigationDecision {
        return NavigationDecisions.fromAction(
            action = GuidanceAction.HOLD,
            reason = reason,
            confidence = confidence,
            basedOnRealVision = observation.realDetector && observation.mode == PerceptionMode.CAMERA_2D,
            metricDepthUsed = observation.mode == PerceptionMode.SYNTHETIC_WORLD && observation.metricDepthAvailable,
            perceptionLabel = label,
        )
    }

    private fun unknown(
        reason: String,
        observation: SceneObservation,
        label: String,
    ): NavigationDecision {
        return NavigationDecisions.fromAction(
            action = GuidanceAction.UNKNOWN,
            reason = reason,
            confidence = 0.10f,
            basedOnRealVision = observation.realDetector && observation.mode == PerceptionMode.CAMERA_2D,
            metricDepthUsed = false,
            perceptionLabel = label,
        )
    }

    private fun cameraConfidence(cue: DirectionalCue): Float {
        return cue.confidence.coerceIn(0f, 1f).coerceAtMost(NavigationConfig.MAX_CAMERA_CONFIDENCE)
    }

    private fun risk(cues: List<DirectionalCue>): Float {
        return cues.sumOf { cue ->
            (cue.confidence * cue.areaFraction.coerceAtLeast(NavigationConfig.RISK_AREA_FLOOR)).toDouble()
        }.toFloat()
    }

    private fun displayLabel(cue: DirectionalCue): String {
        return cue.label.trim().ifBlank { "object" }
    }

    private fun cuesAreInvalid(cues: List<DirectionalCue>): Boolean {
        return cues.any { cue ->
            !cue.confidence.isFinite() ||
                !cue.centerXNormalized.isFinite() ||
                !cue.areaFraction.isFinite() ||
                cue.centerXNormalized !in 0f..1f ||
                cue.areaFraction < 0f ||
                cue.confidence < 0f ||
                cue.confidence > 1f
        }
    }

    private fun obstaclesAreInvalid(obstacles: List<KnownObstacle>): Boolean {
        return obstacles.any { obstacle ->
            !obstacle.forwardMeters.isFinite() || !obstacle.lateralMeters.isFinite()
        }
    }

    private enum class ImageSide { LEFT, RIGHT }
}
