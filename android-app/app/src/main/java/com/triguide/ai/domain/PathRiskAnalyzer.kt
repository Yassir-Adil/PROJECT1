package com.triguide.ai.domain

import java.util.Locale

/**
 * Path understanding for the walking corridor in the image.
 * Does not invent meters. Floor alone is never treated as safe.
 */
enum class PathRiskLevel {
    /** A separate, validated source positively observed walkable area. Not a safety certificate. */
    CLEAR_OBSERVED,

    /** Weak evidence inside the corridor. Keep watching. Prefer silence. */
    POSSIBLE_OBSTACLE,

    /** Strong evidence of something in the walking corridor. */
    BLOCKED,

    /** Stale, contradictory, or insufficient vision. */
    UNKNOWN,
}

/**
 * COCO-style labels that may be irrelevant off the walking corridor.
 * Even a small item on the apparent walking surface remains a possible obstacle.
 */
object NavigationIgnoreLabels {
    private val ignored = setOf(
        "book", "remote", "cell phone", "mouse", "keyboard", "toothbrush",
        "hair drier", "scissors", "spoon", "fork", "knife", "bowl", "cup",
        "wine glass", "bottle", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
        "teddy bear", "clock", "vase", "frisbee", "sports ball", "baseball bat",
        "baseball glove", "tennis racket", "skateboard", "surfboard",
        "toothbrush", "tie", "handbag",
    )

    fun isIgnoredForPath(label: String): Boolean {
        val key = label.trim().lowercase(Locale.US)
        return key in ignored
    }
}

data class PathRiskResult(
    val level: PathRiskLevel,
    val corridorCues: List<DirectionalCue>,
    val dominantInCorridor: DirectionalCue?,
    val diagnostic: String,
)

/**
 * Answers: is there evidence of an obstacle in the user's forward walking corridor?
 */
object PathRiskAnalyzer {
    /**
     * Horizontal band treated as the walking corridor (preview space).
     * Outer thirds are off-path: a chair at the edge must not force HOLD.
     * Overlap uses box extents when present (not center alone).
     */
    const val CORRIDOR_LEFT = 0.25f
    const val CORRIDOR_RIGHT = 0.75f

    /** Real boxes use bottom-edge overlap with this risk band; legacy cues fall back
     * to their center (retaining the existing large-object exceptions). */
    const val UPPER_SCENE_Y = 0.38f

    /** Minimum area for a non-person corridor hit to count as BLOCKED. */
    const val MIN_BLOCK_AREA = 0.025f

    fun analyze(cues: List<DirectionalCue>): PathRiskResult {
        val usable = cues.filter { cue ->
            cue.confidence.isFinite() &&
                cue.confidence >= NavigationConfig.MIN_CUE_CONFIDENCE &&
                cue.confidence <= 1f &&
                cue.areaFraction.isFinite() &&
                cue.areaFraction > 0f &&
                cue.areaFraction <= 1f &&
                cue.centerXNormalized in 0f..1f &&
                cue.centerYNormalized in 0f..1f &&
                hasValidOptionalBounds(cue)
        }
        if (usable.isEmpty()) {
            return PathRiskResult(
                level = PathRiskLevel.UNKNOWN,
                corridorCues = emptyList(),
                dominantInCorridor = null,
                diagnostic = "diag:unknown no_usable_detections",
            )
        }
        val pathRelevant = usable.filterNot { shouldDropFromPath(it) }
        val inCorridor = pathRelevant.filter { inCorridor(it) }
        if (inCorridor.isEmpty()) {
            val sideOnly = pathRelevant.filter { !inCorridor(it) }
            val diag = if (sideOnly.isEmpty()) {
                "diag:unknown only_ignored_or_clutter"
            } else {
                "diag:unknown off_corridor count=${sideOnly.size} labels=${sideOnly.joinToString(",") { it.label }}"
            }
            return PathRiskResult(
                level = PathRiskLevel.UNKNOWN,
                corridorCues = emptyList(),
                dominantInCorridor = null,
                diagnostic = diag,
            )
        }
        val dominant = inCorridor.maxBy { weight(it) }
        val strong = isStrongBlocker(dominant)
        if (!strong) {
            return PathRiskResult(
                level = PathRiskLevel.POSSIBLE_OBSTACLE,
                corridorCues = inCorridor,
                dominantInCorridor = dominant,
                diagnostic = "diag:possible weak_corridor label=${dominant.label} " +
                    "conf=${fmt(dominant.confidence)} area=${fmt(dominant.areaFraction)} " +
                    "x=${fmt(dominant.centerXNormalized)} y=${fmt(dominant.centerYNormalized)}",
            )
        }
        return PathRiskResult(
            level = PathRiskLevel.BLOCKED,
            corridorCues = inCorridor,
            dominantInCorridor = dominant,
            diagnostic = "diag:blocked label=${dominant.label} " +
                "conf=${fmt(dominant.confidence)} area=${fmt(dominant.areaFraction)} " +
                "x=${fmt(dominant.centerXNormalized)} y=${fmt(dominant.centerYNormalized)} " +
                "corridor_count=${inCorridor.size}",
        )
    }

    fun inCorridor(cue: DirectionalCue): Boolean {
        if (!hasValidOptionalBounds(cue)) return false
        val left = cue.boxLeftNormalized
        val right = cue.boxRightNormalized
        val bottom = cue.boxBottomNormalized
        if (left != null && right != null && bottom != null) {
            // A box can cross the walking band even when its center is outside it.
            return left <= CORRIDOR_RIGHT &&
                right >= CORRIDOR_LEFT &&
                bottom >= UPPER_SCENE_Y
        }
        // Legacy/synthetic cues have no box extent; retain the conservative center rule.
        if (cue.centerXNormalized !in CORRIDOR_LEFT..CORRIDOR_RIGHT) return false
        if (cue.areaFraction >= 0.12f) return true
        if (isPersonLike(cue.label) && cue.areaFraction >= 0.04f) return true
        return cue.centerYNormalized >= UPPER_SCENE_Y
    }

    private fun hasValidOptionalBounds(cue: DirectionalCue): Boolean {
        val left = cue.boxLeftNormalized
        val right = cue.boxRightNormalized
        val top = cue.boxTopNormalized
        val bottom = cue.boxBottomNormalized
        if (left == null && right == null && top == null && bottom == null) return true
        if (left == null || right == null || top == null || bottom == null) return false
        return left.isFinite() && right.isFinite() && top.isFinite() && bottom.isFinite() &&
            left >= 0f && right <= 1f && top >= 0f && bottom <= 1f &&
            left < right && top < bottom &&
            cue.centerXNormalized in left..right &&
            cue.centerYNormalized in top..bottom
    }

    private fun shouldDropFromPath(cue: DirectionalCue): Boolean {
        // Class names cannot make a lower-corridor box disappear. A cup or knife there
        // may be a trip hazard even when its box is too small to count as BLOCKED.
        return NavigationIgnoreLabels.isIgnoredForPath(cue.label) && !inCorridor(cue)
    }

    private fun isStrongBlocker(cue: DirectionalCue): Boolean {
        if (isPersonLike(cue.label) && cue.confidence >= NavigationConfig.MIN_CUE_CONFIDENCE) {
            return cue.areaFraction >= 0.03f
        }
        if (isFurnitureLike(cue.label)) {
            return cue.areaFraction >= MIN_BLOCK_AREA && cue.confidence >= NavigationConfig.MIN_CUE_CONFIDENCE
        }
        if (NavigationIgnoreLabels.isIgnoredForPath(cue.label)) {
            return cue.areaFraction >= 0.12f && cue.confidence >= 0.55f
        }
        return cue.areaFraction >= MIN_BLOCK_AREA && cue.confidence >= NavigationConfig.MIN_CUE_CONFIDENCE
    }

    private fun isPersonLike(label: String): Boolean {
        val key = label.trim().lowercase(Locale.US)
        return key == "person" || key == "dog" || key == "cat" || key == "horse" || key == "cow"
    }

    private fun isFurnitureLike(label: String): Boolean {
        val key = label.trim().lowercase(Locale.US)
        return key in setOf(
            "chair", "couch", "bed", "dining table", "toilet", "tv", "laptop",
            "refrigerator", "oven", "sink", "microwave", "bench", "potted plant",
        )
    }

    private fun weight(cue: DirectionalCue): Float {
        return cue.confidence * cue.areaFraction.coerceAtLeast(NavigationConfig.RISK_AREA_FLOOR)
    }

    private fun fmt(value: Float): String = String.format(Locale.US, "%.2f", value)
}
