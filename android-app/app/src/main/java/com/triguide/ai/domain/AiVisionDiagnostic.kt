package com.triguide.ai.domain

/**
 * Research-only AI Vision overlay model.
 *
 * Colors come from existing [PathRiskAnalyzer] results. This is **not** floor
 * segmentation and must never be treated as walkability or spoken CLEAR.
 *
 * Color legend:
 * - Green walk lane: validated positive evidence only (not “safe” / not floor proof)
 * - Yellow: POSSIBLE_OBSTACLE in the walking corridor
 * - Red: BLOCKED in the walking corridor
 * - Blue: CLEAR_OBSERVED (fixture/synthetic research marker only; not a mobility certificate)
 */
enum class AiVisionTint {
    UNKNOWN_OFF_PATH,
    /** Stronger green fill for the lower corridor only after validated positive evidence. */
    OPEN_LANE,
    POSSIBLE,
    BLOCKED,
    RESEARCH_CLEAR_MARKER,
}

data class AiVisionBoxLayer(
    val boxLeftNormalized: Float,
    val boxTopNormalized: Float,
    val boxRightNormalized: Float,
    val boxBottomNormalized: Float,
    val tint: AiVisionTint,
    val label: String,
    val confidence: Float,
    val inCorridor: Boolean,
)

data class AiVisionOverlayModel(
    val enabled: Boolean,
    val corridorLeft: Float,
    val corridorRight: Float,
    val bandTint: AiVisionTint,
    val pathRisk: PathRiskLevel,
    val caption: String,
    val boxes: List<AiVisionBoxLayer>,
    /** True only when a validated positive walkable-region result permits a diagnostic strip. */
    val showOpenWalkLane: Boolean = false,
    val laneTopY: Float = OpenLaneCue.LANE_TOP_Y,
) {
    companion object {
        val HIDDEN = AiVisionOverlayModel(
            enabled = false,
            corridorLeft = PathRiskAnalyzer.CORRIDOR_LEFT,
            corridorRight = PathRiskAnalyzer.CORRIDOR_RIGHT,
            bandTint = AiVisionTint.UNKNOWN_OFF_PATH,
            pathRisk = PathRiskLevel.UNKNOWN,
            caption = "",
            boxes = emptyList(),
            showOpenWalkLane = false,
        )
    }
}

object AiVisionModeCopy {
    const val TOGGLE_LABEL = "AI Vision"
    const val TOGGLE_DESCRIPTION =
        "Toggle research path-risk overlay. Green appears only for a validated positive " +
            "walkable-region diagnostic and is labeled “${OpenLaneCue.DIAGNOSTIC_LABEL}”. " +
            "Empty or detector-only frames stay neutral. Not permission to walk."
    const val LEGEND =
        "Neutral band = unknown / off-path diagnostic. Yellow = possible obstacle. Red = blocked. " +
            "Green strip (if shown) = ${OpenLaneCue.DIAGNOSTIC_LABEL}. " +
            "One soft camera-active tone means the stream started — not safety."
    const val SAFETY_CAPTION =
        "AI Vision is diagnostic only. Green (if any) = ${OpenLaneCue.DIAGNOSTIC_LABEL}. " +
            "Never a walkability certificate."
}

object AiVisionDiagnostic {
    fun tintForPathRisk(level: PathRiskLevel): AiVisionTint = when (level) {
        PathRiskLevel.UNKNOWN -> AiVisionTint.UNKNOWN_OFF_PATH
        PathRiskLevel.POSSIBLE_OBSTACLE -> AiVisionTint.POSSIBLE
        PathRiskLevel.BLOCKED -> AiVisionTint.BLOCKED
        // Live camera path must not emit CLEAR; if a fixture does, keep it visually distinct
        // from green UNKNOWN so green never means “go”.
        PathRiskLevel.CLEAR_OBSERVED -> AiVisionTint.RESEARCH_CLEAR_MARKER
    }

    fun build(
        enabled: Boolean,
        pathRisk: PathRiskResult,
        cues: List<DirectionalCue> = pathRisk.corridorCues,
        fusedEvidence: FusionGateResult? = null,
    ): AiVisionOverlayModel {
        if (!enabled) return AiVisionOverlayModel.HIDDEN
        val openLane = OpenLaneCue.showGreenWalkLane(pathRisk.level, fusedEvidence)
        val band = if (openLane) AiVisionTint.OPEN_LANE else tintForPathRisk(pathRisk.level)
        val boxes = cues.mapNotNull { cue -> boxLayer(cue, pathRisk.level) }
        return AiVisionOverlayModel(
            enabled = true,
            corridorLeft = PathRiskAnalyzer.CORRIDOR_LEFT,
            corridorRight = PathRiskAnalyzer.CORRIDOR_RIGHT,
            bandTint = band,
            pathRisk = pathRisk.level,
            caption = captionFor(pathRisk, openLane),
            boxes = boxes,
            showOpenWalkLane = openLane,
            laneTopY = OpenLaneCue.LANE_TOP_Y,
        )
    }

    /**
     * Fixture-only helper: builds an overlay from authored cues without claiming
     * a live segmenter. Used by unit tests and Diagnostics demos.
     */
    fun fromFixtureCues(cues: List<DirectionalCue>): AiVisionOverlayModel {
        val risk = PathRiskAnalyzer.analyze(cues)
        return build(enabled = true, pathRisk = risk, cues = cues)
    }

    private fun boxLayer(cue: DirectionalCue, overall: PathRiskLevel): AiVisionBoxLayer? {
        val left = cue.boxLeftNormalized ?: return null
        val right = cue.boxRightNormalized ?: return null
        val top = cue.boxTopNormalized ?: return null
        val bottom = cue.boxBottomNormalized ?: return null
        if (!left.isFinite() || !right.isFinite() || !top.isFinite() || !bottom.isFinite()) {
            return null
        }
        if (right <= left || bottom <= top) return null
        val inBand = PathRiskAnalyzer.inCorridor(cue)
        val tint = when {
            !inBand -> AiVisionTint.UNKNOWN_OFF_PATH
            overall == PathRiskLevel.BLOCKED -> AiVisionTint.BLOCKED
            overall == PathRiskLevel.POSSIBLE_OBSTACLE -> AiVisionTint.POSSIBLE
            overall == PathRiskLevel.CLEAR_OBSERVED -> AiVisionTint.RESEARCH_CLEAR_MARKER
            else -> AiVisionTint.UNKNOWN_OFF_PATH
        }
        return AiVisionBoxLayer(
            boxLeftNormalized = left.coerceIn(0f, 1f),
            boxTopNormalized = top.coerceIn(0f, 1f),
            boxRightNormalized = right.coerceIn(0f, 1f),
            boxBottomNormalized = bottom.coerceIn(0f, 1f),
            tint = tint,
            label = cue.label,
            confidence = cue.confidence,
            inCorridor = inBand,
        )
    }

    private fun captionFor(pathRisk: PathRiskResult, openLane: Boolean): String {
        val riskWord = when (pathRisk.level) {
            PathRiskLevel.UNKNOWN -> "corridor risk unknown (fail-closed)"
            PathRiskLevel.POSSIBLE_OBSTACLE -> "possible obstacle"
            PathRiskLevel.BLOCKED -> "blocked"
            PathRiskLevel.CLEAR_OBSERVED -> "research CLEAR marker (not a walk certificate)"
        }
        val laneNote = if (openLane) {
            " Lane=${OpenLaneCue.DIAGNOSTIC_LABEL}."
        } else {
            " Lane=hidden (no validated positive evidence)."
        }
        return "${AiVisionModeCopy.SAFETY_CAPTION}$laneNote Risk=$riskWord. ${pathRisk.diagnostic}"
    }
}
