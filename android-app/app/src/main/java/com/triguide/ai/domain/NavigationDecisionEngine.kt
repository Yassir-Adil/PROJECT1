package com.triguide.ai.domain

import java.util.Locale
import kotlin.math.abs

/**
 * Turns one observation into a virtual steering choice.
 *
 * Camera samples use the horizontal center of each box. They do not produce meters.
 * A box in the middle band, an empty frame, or two competing sides yields Hold or Unknown.
 * A box clearly to one side suggests a slight turn. A box near the middle band suggests a fuller turn.
 * How much of the picture a box fills is not treated as a distance.
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
        val dominant = usable.maxBy { weight(it) }
        if (dominant.centerXNormalized < NavigationConfig.LEFT_THIRD_END) {
            if (!escapeBlocked(ImageSide.LEFT, dominant, usable)) {
                val side = usable.filter { it.centerXNormalized < NavigationConfig.CENTER_START }
                return steerAwayFrom(ImageSide.LEFT, side.ifEmpty { listOf(dominant) }, observation)
            }
            return holdForBlockedSide(dominant, usable, observation)
        }
        if (dominant.centerXNormalized > NavigationConfig.RIGHT_THIRD_START) {
            if (!escapeBlocked(ImageSide.RIGHT, dominant, usable)) {
                val side = usable.filter { it.centerXNormalized > NavigationConfig.CENTER_END }
                return steerAwayFrom(ImageSide.RIGHT, side.ifEmpty { listOf(dominant) }, observation)
            }
            return holdForBlockedSide(dominant, usable, observation)
        }
        val left = usable.filter { it.centerXNormalized < NavigationConfig.CENTER_START }
        val right = usable.filter { it.centerXNormalized > NavigationConfig.CENTER_END }
        val center = usable.filter { it.centerXNormalized in NavigationConfig.CENTER_START..NavigationConfig.CENTER_END }
        if (center.isNotEmpty()) {
            val chosen = center.maxBy { weight(it) }
            val name = displayLabel(chosen)
            return hold(
                reason = "$name is in the center of the image. No confirmed passage to either side. Depth is not measured.",
                observation = observation,
                label = label,
                confidence = cameraConfidence(chosen),
                subjectLabel = name,
                subjectSide = "center",
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
                subjectLabel = "",
                subjectSide = "both",
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
        val action = turnAction(side, strong = nearCenter)
        val name = displayLabel(strongest)
        val areaPercent = (strongest.areaFraction.coerceIn(0f, 1f) * 100f).toInt()
        val detectorConfidence = String.format(Locale.US, "%.2f", strongest.confidence)
        val awayWord = if (side == ImageSide.LEFT) "right" else "left"
        val steerWord = if (nearCenter) awayWord else "slightly $awayWord"
        return NavigationDecisions.fromAction(
            action = action,
            reason = "$name is on the ${sideWord(side)} of the image " +
                "(detector confidence $detectorConfidence, $areaPercent percent of the frame). " +
                "Steering $steerWord. Depth is not measured. The $awayWord side is not a confirmed gap. " +
                "The box size is not a distance.",
            confidence = cameraConfidence(strongest),
            basedOnRealVision = observation.realDetector,
            metricDepthUsed = false,
            perceptionLabel = NavigationConfig.CAMERA_LABEL,
            subjectLabel = name,
            subjectSide = sideWord(side),
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
        subjectLabel: String = "",
        subjectSide: String = "",
    ): NavigationDecision {
        return NavigationDecisions.fromAction(
            action = GuidanceAction.HOLD,
            reason = reason,
            confidence = confidence,
            basedOnRealVision = observation.realDetector && observation.mode == PerceptionMode.CAMERA_2D,
            metricDepthUsed = observation.mode == PerceptionMode.SYNTHETIC_WORLD && observation.metricDepthAvailable,
            perceptionLabel = label,
            subjectLabel = subjectLabel,
            subjectSide = subjectSide,
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

    /**
     * The outer-third turn waits when another box is strong enough to sit in the
     * center band or on the opposite side. A weak box does not cancel the turn.
     */
    private fun escapeBlocked(
        occupied: ImageSide,
        dominant: DirectionalCue,
        usable: List<DirectionalCue>,
    ): Boolean {
        return usable.any { other ->
            if (other === dominant) return@any false
            val inTheWay = when (occupied) {
                ImageSide.LEFT -> other.centerXNormalized >= NavigationConfig.CENTER_START
                ImageSide.RIGHT -> other.centerXNormalized <= NavigationConfig.CENTER_END
            }
            inTheWay && strongBlocker(other, dominant)
        }
    }

    private fun holdForBlockedSide(
        dominant: DirectionalCue,
        usable: List<DirectionalCue>,
        observation: SceneObservation,
    ): NavigationDecision {
        val label = NavigationConfig.CAMERA_LABEL
        val center = usable.filter { cue ->
            cue.centerXNormalized in NavigationConfig.CENTER_START..NavigationConfig.CENTER_END &&
                strongBlocker(cue, dominant)
        }
        if (center.isNotEmpty()) {
            val chosen = center.maxBy { weight(it) }
            val name = displayLabel(chosen)
            return hold(
                reason = "$name is in the center of the image. No confirmed passage to either side. Depth is not measured.",
                observation = observation,
                label = label,
                confidence = cameraConfidence(chosen),
                subjectLabel = name,
                subjectSide = "center",
            )
        }
        return hold(
            reason = "Boxes are on both sides of the image. No confirmed passage. Depth is not measured.",
            observation = observation,
            label = label,
            confidence = cameraConfidence(usable.maxBy { weight(it) }),
            subjectLabel = "",
            subjectSide = "both",
        )
    }

    private fun strongBlocker(other: DirectionalCue, dominant: DirectionalCue): Boolean {
        val dominantWeight = weight(dominant)
        if (dominantWeight <= 0f) return true
        return weight(other) * NavigationConfig.DOMINANCE_RATIO >= dominantWeight
    }

    private fun cameraConfidence(cue: DirectionalCue): Float {
        return cue.confidence.coerceIn(0f, 1f).coerceAtMost(NavigationConfig.MAX_CAMERA_CONFIDENCE)
    }

    private fun weight(cue: DirectionalCue): Float {
        return cue.confidence * cue.areaFraction.coerceAtLeast(NavigationConfig.RISK_AREA_FLOOR)
    }

    private fun risk(cues: List<DirectionalCue>): Float {
        return cues.sumOf { cue -> weight(cue).toDouble() }.toFloat()
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
