package com.triguide.ai.domain

import java.util.Locale

/**
 * Path understanding for the walking corridor in the image.
 * Does not invent meters. Floor alone is never treated as safe.
 */
enum class PathRiskLevel {
    /** No confirmed obstacle in the corridor. Not a safety certificate. */
    CLEAR_OBSERVED,

    /** Weak or off-corridor evidence. Keep watching. Prefer silence. */
    POSSIBLE_OBSTACLE,

    /** Strong evidence of something in the walking corridor. */
    BLOCKED,

    /** Stale, contradictory, or insufficient vision. */
    UNKNOWN,
}

/**
 * COCO-style labels that are usually not navigation blockers when alone
 * (small desktop items, food, false floor glints). Still drawn on the overlay.
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
     * Far outer edges (outside this band) are off-path and stay silent.
     */
    const val CORRIDOR_LEFT = 0.10f
    const val CORRIDOR_RIGHT = 0.90f

    /**
     * Objects whose vertical center is above this are often decorations / distant;
     * they still count if the box is large (person/furniture).
     */
    const val UPPER_SCENE_Y = 0.38f

    /** Small ignored-class boxes in the lower third are treated as floor clutter. */
    const val FLOOR_CLUTTER_Y = 0.62f
    const val FLOOR_CLUTTER_AREA = 0.08f

    /** Minimum area for a non-person corridor hit to count as BLOCKED. */
    const val MIN_BLOCK_AREA = 0.025f

    fun analyze(cues: List<DirectionalCue>): PathRiskResult {
        val usable = cues.filter { cue ->
            cue.confidence.isFinite() &&
                cue.confidence >= NavigationConfig.MIN_CUE_CONFIDENCE &&
                cue.confidence <= 1f &&
                cue.areaFraction > 0f &&
                cue.centerXNormalized in 0f..1f &&
                cue.centerYNormalized in 0f..1f
        }
        if (usable.isEmpty()) {
            return PathRiskResult(
                level = PathRiskLevel.CLEAR_OBSERVED,
                corridorCues = emptyList(),
                dominantInCorridor = null,
                diagnostic = "diag:clear empty_detections corridor_empty",
            )
        }
        val pathRelevant = usable.filterNot { shouldDropFromPath(it) }
        val inCorridor = pathRelevant.filter { inCorridor(it) }
        if (inCorridor.isEmpty()) {
            val sideOnly = pathRelevant.filter { !inCorridor(it) }
            val diag = if (sideOnly.isEmpty()) {
                "diag:clear only_ignored_or_clutter"
            } else {
                "diag:clear off_corridor count=${sideOnly.size} labels=${sideOnly.joinToString(",") { it.label }}"
            }
            return PathRiskResult(
                level = PathRiskLevel.CLEAR_OBSERVED,
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
        if (cue.centerXNormalized !in CORRIDOR_LEFT..CORRIDOR_RIGHT) return false
        // Large furniture / people count even if centered higher in the frame.
        if (cue.areaFraction >= 0.12f) return true
        if (isPersonLike(cue.label) && cue.areaFraction >= 0.04f) return true
        return cue.centerYNormalized >= UPPER_SCENE_Y
    }

    private fun shouldDropFromPath(cue: DirectionalCue): Boolean {
        // Cups, books, false floor glints, and similar COCO clutter never block the corridor.
        return NavigationIgnoreLabels.isIgnoredForPath(cue.label)
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
