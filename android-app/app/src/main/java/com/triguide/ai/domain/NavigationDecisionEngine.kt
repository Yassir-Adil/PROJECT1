package com.triguide.ai.domain

import android.util.Log
import java.util.Locale
import kotlin.math.abs

/**
 * Turns one observation into a virtual steering choice plus a [PathRiskLevel].
 *
 * Camera samples use box position in the image. They do not produce meters.
 * Object detections alone cannot establish walkable space; no usable corridor evidence → UNKNOWN.
 * A camera obstacle → BLOCKED and Hold; boxes do not prove an alternate side is walkable.
 * Floor clutter / off-corridor boxes do not force a stop.
 */
class NavigationDecisionEngine : NavigationDecisionPolicy {
    override fun decide(observation: SceneObservation): NavigationDecision {
        val decision = when (observation.mode) {
            PerceptionMode.SYNTHETIC_WORLD -> decideSynthetic(observation)
            PerceptionMode.CAMERA_2D -> decideCamera(observation)
            PerceptionMode.UNAVAILABLE -> unknown(
                reason = "No real camera frame is available. Open space is not assumed.",
                observation = observation,
                label = NavigationConfig.NONE_LABEL,
                diagnostic = "diag:unknown no_camera_frame",
            )
        }
        runCatching {
            Log.i(TAG, "${decision.diagnostic} | risk=${decision.pathRisk} action=${decision.action} | ${decision.reason}")
        }
        return decision
    }

    private fun decideCamera(observation: SceneObservation): NavigationDecision {
        val label = NavigationConfig.CAMERA_LABEL
        if (!observation.realDetector) {
            return unknown(
                reason = "No real camera frame is available. Open space is not assumed.",
                observation = observation,
                label = NavigationConfig.NONE_LABEL,
                diagnostic = "diag:unknown detector_not_ready",
            )
        }
        if (observation.contradictory || observation.metricDepthAvailable || cuesAreInvalid(observation.cues)) {
            return unknown(
                reason = "The camera observation is contradictory. No direction is chosen. Depth is not measured.",
                observation = observation,
                label = label,
                diagnostic = "diag:unknown contradictory_or_invalid_cues",
            )
        }
        val age = observation.frameAgeMs
        if (age == null) {
            return unknown(
                reason = "The camera frame has no timestamp. Depth is not measured.",
                observation = observation,
                label = label,
                diagnostic = "diag:unknown missing_timestamp",
            )
        }
        if (age >= NavigationConfig.STALE_FRAME_MS) {
            return unknown(
                reason = "The camera frame is stale (${age} ms). Depth is not measured.",
                observation = observation,
                label = label,
                diagnostic = "diag:unknown stale_frame age_ms=$age",
            )
        }

        return decideCameraRisk(observation, PathRiskAnalyzer.analyze(observation.cues))
    }

    /** Pure decision seam for regression-testing an accidental camera CLEAR result. */
    internal fun decideCameraRisk(
        observation: SceneObservation,
        risk: PathRiskResult,
    ): NavigationDecision {
        if (observation.mode != PerceptionMode.CAMERA_2D) {
            return unknown(
                reason = "A camera risk was supplied for a non-camera observation.",
                observation = observation,
                label = NavigationConfig.NONE_LABEL,
                diagnostic = "diag:unknown camera_risk_mode_mismatch",
            )
        }
        val label = NavigationConfig.CAMERA_LABEL
        return when (risk.level) {
            PathRiskLevel.CLEAR_OBSERVED -> unknown(
                reason = "Camera boxes cannot verify walkable area. Depth is not measured.",
                observation = observation,
                label = label,
                diagnostic = "diag:unknown camera_clear_without_positive_evidence",
            )
            PathRiskLevel.POSSIBLE_OBSTACLE -> possibleObstacle(observation, risk)
            PathRiskLevel.BLOCKED -> blockedCorridor(observation, risk)
            PathRiskLevel.UNKNOWN -> unknown(
                reason = "Path risk is unknown. Depth is not measured.",
                observation = observation,
                label = label,
                diagnostic = risk.diagnostic,
            )
        }
    }

    private fun possibleObstacle(
        observation: SceneObservation,
        risk: PathRiskResult,
    ): NavigationDecision {
        val subject = risk.dominantInCorridor
        val name = subject?.let { displayLabel(it) }.orEmpty()
        return hold(
            reason = "Possible obstacle in the corridor ($name). Watching. Depth is not measured.",
            observation = observation,
            label = NavigationConfig.CAMERA_LABEL,
            confidence = subject?.let { cameraConfidence(it) } ?: 0.35f,
            subjectLabel = name,
            subjectSide = subject?.let { sideOf(it) }.orEmpty(),
            pathRisk = PathRiskLevel.POSSIBLE_OBSTACLE,
            diagnostic = risk.diagnostic,
        )
    }

    private fun blockedCorridor(
        observation: SceneObservation,
        risk: PathRiskResult,
    ): NavigationDecision {
        val dominant = risk.dominantInCorridor
            ?: return hold(
                reason = "Corridor blocked with no dominant label. Depth is not measured.",
                observation = observation,
                label = NavigationConfig.CAMERA_LABEL,
                confidence = 0.55f,
                pathRisk = PathRiskLevel.BLOCKED,
                diagnostic = risk.diagnostic,
            )
        val side = horizontalBand(dominant)
        if (side == Band.CENTER) {
            val name = displayLabel(dominant)
            return hold(
                reason = "$name is in the walking corridor center. Stop and check. Depth is not measured.",
                observation = observation,
                label = NavigationConfig.CAMERA_LABEL,
                confidence = cameraConfidence(dominant),
                subjectLabel = name,
                subjectSide = "center",
                pathRisk = PathRiskLevel.BLOCKED,
                diagnostic = risk.diagnostic,
            )
        }
        val occupied = if (side == Band.LEFT) ImageSide.LEFT else ImageSide.RIGHT
        val usable = risk.corridorCues
        if (escapeBlocked(occupied, dominant, usable)) {
            return holdForBlockedSide(dominant, usable, observation, risk.diagnostic)
        }
        val name = displayLabel(dominant)
        val observedSide = sideWord(occupied)
        return hold(
            reason = "$name is detected on the $observedSide side. Camera boxes do not confirm a walkable gap; stop and check. Depth is not measured.",
            observation = observation,
            label = NavigationConfig.CAMERA_LABEL,
            confidence = cameraConfidence(dominant),
            subjectLabel = name,
            subjectSide = observedSide,
            pathRisk = PathRiskLevel.BLOCKED,
            diagnostic = risk.diagnostic,
        )
    }

    private fun decideSynthetic(observation: SceneObservation): NavigationDecision {
        val label = NavigationConfig.SYNTHETIC_LABEL
        if (observation.contradictory || obstaclesAreInvalid(observation.knownObstacles)) {
            return unknown(
                reason = "Synthetic map observations disagree, so no direction is chosen.",
                observation = observation,
                label = label,
                diagnostic = "diag:unknown synthetic_contradictory",
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
                pathRisk = PathRiskLevel.UNKNOWN,
                diagnostic = "diag:unknown synthetic_incomplete",
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
                pathRisk = PathRiskLevel.CLEAR_OBSERVED,
                diagnostic = "diag:clear synthetic_empty_corridor",
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
                pathRisk = PathRiskLevel.BLOCKED,
                diagnostic = "diag:blocked synthetic_axis ${onAxis.first().name}",
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
                pathRisk = PathRiskLevel.BLOCKED,
                diagnostic = "diag:blocked synthetic_both_sides",
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
            pathRisk = PathRiskLevel.BLOCKED,
            diagnostic = "diag:blocked synthetic_side ${chosen.name}",
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

    private fun sideOf(cue: DirectionalCue): String {
        return when (horizontalBand(cue)) {
            Band.LEFT -> "left"
            Band.RIGHT -> "right"
            Band.CENTER -> "center"
        }
    }

    private fun horizontalBand(cue: DirectionalCue): Band {
        return when {
            cue.centerXNormalized < NavigationConfig.CENTER_START -> Band.LEFT
            cue.centerXNormalized > NavigationConfig.CENTER_END -> Band.RIGHT
            else -> Band.CENTER
        }
    }

    private fun hold(
        reason: String,
        observation: SceneObservation,
        label: String,
        confidence: Float,
        subjectLabel: String = "",
        subjectSide: String = "",
        pathRisk: PathRiskLevel = PathRiskLevel.BLOCKED,
        diagnostic: String = "",
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
            pathRisk = pathRisk,
            diagnostic = diagnostic,
        )
    }

    private fun unknown(
        reason: String,
        observation: SceneObservation,
        label: String,
        diagnostic: String,
    ): NavigationDecision {
        return NavigationDecisions.fromAction(
            action = GuidanceAction.UNKNOWN,
            reason = reason,
            confidence = 0.10f,
            basedOnRealVision = observation.realDetector && observation.mode == PerceptionMode.CAMERA_2D,
            metricDepthUsed = false,
            perceptionLabel = label,
            pathRisk = PathRiskLevel.UNKNOWN,
            diagnostic = diagnostic,
        )
    }

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
        diagnostic: String,
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
                reason = "$name is in the walking corridor center. Stop and check. Depth is not measured.",
                observation = observation,
                label = label,
                confidence = cameraConfidence(chosen),
                subjectLabel = name,
                subjectSide = "center",
                pathRisk = PathRiskLevel.BLOCKED,
                diagnostic = diagnostic,
            )
        }
        return hold(
            reason = "Strong boxes on both sides of the corridor. Stop and check. Depth is not measured.",
            observation = observation,
            label = label,
            confidence = cameraConfidence(usable.maxBy { weight(it) }),
            subjectLabel = "",
            subjectSide = "both",
            pathRisk = PathRiskLevel.BLOCKED,
            diagnostic = diagnostic,
        )
    }

    private fun strongBlocker(other: DirectionalCue, dominant: DirectionalCue): Boolean {
        val dominantWeight = weight(dominant)
        if (dominantWeight <= 0f) return true
        return weight(other) >= dominantWeight * NavigationConfig.OPPOSITE_SIMILAR_FRACTION
    }

    private fun cameraConfidence(cue: DirectionalCue): Float {
        return cue.confidence.coerceIn(0f, 1f).coerceAtMost(NavigationConfig.MAX_CAMERA_CONFIDENCE)
    }

    private fun weight(cue: DirectionalCue): Float {
        return cue.confidence * cue.areaFraction.coerceAtLeast(NavigationConfig.RISK_AREA_FLOOR)
    }

    private fun displayLabel(cue: DirectionalCue): String {
        return cue.label.trim().ifBlank { "object" }
    }

    private fun cuesAreInvalid(cues: List<DirectionalCue>): Boolean {
        return cues.any { cue ->
            !cue.confidence.isFinite() ||
                !cue.centerXNormalized.isFinite() ||
                !cue.centerYNormalized.isFinite() ||
                !cue.areaFraction.isFinite() ||
                cue.centerXNormalized !in 0f..1f ||
                cue.centerYNormalized !in 0f..1f ||
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
    private enum class Band { LEFT, CENTER, RIGHT }

    companion object {
        private const val TAG = "TriGuideNav"
    }
}
