package com.triguide.ai.domain

/**
 * A model-agnostic relative inverse-depth frame.
 *
 * Values must be normalized to [0, 1], where larger means closer. This contract
 * deliberately does not claim metric distance, floor safety, or a free corridor.
 */
data class RelativeDepthFrame(
    val frameId: Long,
    val frameElapsedMs: Long,
    val width: Int,
    val height: Int,
    val normalizedInverseDepth: List<Float>,
    val source: String = "diagnostic-relative-depth",
)

enum class RelativeDepthEvidenceState {
    UNAVAILABLE,
    INVALID,
    STALE,
    DIAGNOSTIC_NO_HAZARD,
    WALL_OR_LARGE_OBSTACLE,
    POSSIBLE_DROP,
}

enum class RelativeDepthEvidenceIssue {
    NONE,
    NO_FRAME,
    INVALID_CLOCK,
    INVALID_GRID,
    INVALID_VALUE,
    FUTURE_FRAME,
    EXPIRED_FRAME,
    NON_MONOTONIC_FRAME,
}

data class RelativeDepthEvidence(
    val state: RelativeDepthEvidenceState,
    val issue: RelativeDepthEvidenceIssue,
    val frameId: Long,
    val frameElapsedMs: Long,
    val source: String,
    val centerNearScore: Float,
    val lowerFloorScore: Float,
    val detail: String,
) {
    /** Depth may only raise risk. It is never evidence for clearance. */
    val raisesRisk: Boolean
        get() = state == RelativeDepthEvidenceState.WALL_OR_LARGE_OBSTACLE ||
            state == RelativeDepthEvidenceState.POSSIBLE_DROP
}

/**
 * Conservative diagnostic analyzer for a normalized relative-depth map.
 *
 * This is intentionally not wired into navigation. A valid no-hazard result
 * means only "this heuristic found no depth hazard"; it cannot lower a detector
 * hazard and cannot produce STRAIGHT, TURN, CLEAR, or speech.
 */
object RelativeDepthEvidenceAnalyzer {
    const val MAX_FRAME_AGE_MS = 500L
    private const val MIN_GRID_SIDE = 4
    private const val NEAR_THRESHOLD = 0.75f
    private const val WALL_FRACTION_THRESHOLD = 0.55f
    private const val DROP_DELTA_THRESHOLD = 0.25f
    private const val MID_FLOOR_MINIMUM = 0.55f
    private const val LOWER_FLOOR_MAXIMUM = 0.35f

    fun evaluate(
        frame: RelativeDepthFrame?,
        nowElapsedMs: Long,
        previousFrameId: Long? = null,
    ): RelativeDepthEvidence {
        if (frame == null) {
            return unavailable(RelativeDepthEvidenceIssue.NO_FRAME)
        }
        if (nowElapsedMs < 0L) {
            return invalid(frame, RelativeDepthEvidenceIssue.INVALID_CLOCK)
        }
        if (frame.frameId <= 0L || frame.frameElapsedMs < 0L) {
            return invalid(frame, RelativeDepthEvidenceIssue.INVALID_GRID)
        }
        if (previousFrameId != null && frame.frameId <= previousFrameId) {
            return stale(frame, RelativeDepthEvidenceIssue.NON_MONOTONIC_FRAME)
        }
        if (frame.frameElapsedMs > nowElapsedMs) {
            return invalid(frame, RelativeDepthEvidenceIssue.FUTURE_FRAME)
        }
        if (nowElapsedMs - frame.frameElapsedMs >= MAX_FRAME_AGE_MS) {
            return stale(frame, RelativeDepthEvidenceIssue.EXPIRED_FRAME)
        }
        if (frame.width < MIN_GRID_SIDE || frame.height < MIN_GRID_SIDE ||
            frame.normalizedInverseDepth.size != frame.width * frame.height
        ) {
            return invalid(frame, RelativeDepthEvidenceIssue.INVALID_GRID)
        }
        if (frame.normalizedInverseDepth.any { !it.isFinite() || it < 0f || it > 1f }) {
            return invalid(frame, RelativeDepthEvidenceIssue.INVALID_VALUE)
        }

        val centerLeft = (frame.width * 0.25f).toInt().coerceAtLeast(0)
        val centerRight = (frame.width * 0.75f).toInt().coerceAtMost(frame.width)
        val middleTop = (frame.height * 0.35f).toInt().coerceAtLeast(0)
        val middleBottom = (frame.height * 0.60f).toInt().coerceAtMost(frame.height)
        val lowerTop = middleBottom

        val middle = valuesIn(frame, centerLeft, centerRight, middleTop, middleBottom)
        val lower = valuesIn(frame, centerLeft, centerRight, lowerTop, frame.height)
        if (middle.isEmpty() || lower.isEmpty()) {
            return invalid(frame, RelativeDepthEvidenceIssue.INVALID_GRID)
        }

        val centerNearScore = lower.average().toFloat()
        val middleFloorScore = middle.average().toFloat()
        val nearFraction = lower.count { it >= NEAR_THRESHOLD }.toFloat() / lower.size
        val possibleDrop = middleFloorScore >= MID_FLOOR_MINIMUM &&
            centerNearScore <= LOWER_FLOOR_MAXIMUM &&
            middleFloorScore - centerNearScore >= DROP_DELTA_THRESHOLD
        val wall = centerNearScore >= NEAR_THRESHOLD && nearFraction >= WALL_FRACTION_THRESHOLD

        return when {
            possibleDrop -> diagnostic(
                frame = frame,
                state = RelativeDepthEvidenceState.POSSIBLE_DROP,
                centerNearScore = centerNearScore,
                lowerFloorScore = centerNearScore,
                detail = "Relative depth suggests an abrupt floor discontinuity; diagnostic HOLD signal only.",
            )
            wall -> diagnostic(
                frame = frame,
                state = RelativeDepthEvidenceState.WALL_OR_LARGE_OBSTACLE,
                centerNearScore = centerNearScore,
                lowerFloorScore = centerNearScore,
                detail = "Relative depth is near across the center corridor; diagnostic obstacle signal only.",
            )
            else -> diagnostic(
                frame = frame,
                state = RelativeDepthEvidenceState.DIAGNOSTIC_NO_HAZARD,
                centerNearScore = centerNearScore,
                lowerFloorScore = centerNearScore,
                detail = "No relative-depth hazard found by this heuristic; clearance is not established.",
            )
        }
    }

    private fun valuesIn(
        frame: RelativeDepthFrame,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
    ): List<Float> {
        return buildList {
            for (row in top until bottom) {
                for (column in left until right) {
                    add(frame.normalizedInverseDepth[row * frame.width + column])
                }
            }
        }
    }

    private fun unavailable(issue: RelativeDepthEvidenceIssue): RelativeDepthEvidence =
        RelativeDepthEvidence(
            state = RelativeDepthEvidenceState.UNAVAILABLE,
            issue = issue,
            frameId = 0L,
            frameElapsedMs = 0L,
            source = "none",
            centerNearScore = 0f,
            lowerFloorScore = 0f,
            detail = "No relative-depth frame is available; clearance is not established.",
        )

    private fun invalid(
        frame: RelativeDepthFrame,
        issue: RelativeDepthEvidenceIssue,
    ): RelativeDepthEvidence =
        RelativeDepthEvidence(
            state = RelativeDepthEvidenceState.INVALID,
            issue = issue,
            frameId = frame.frameId,
            frameElapsedMs = frame.frameElapsedMs,
            source = frame.source,
            centerNearScore = 0f,
            lowerFloorScore = 0f,
            detail = "Relative-depth frame is invalid; clearance is not established.",
        )

    private fun stale(
        frame: RelativeDepthFrame,
        issue: RelativeDepthEvidenceIssue,
    ): RelativeDepthEvidence =
        RelativeDepthEvidence(
            state = RelativeDepthEvidenceState.STALE,
            issue = issue,
            frameId = frame.frameId,
            frameElapsedMs = frame.frameElapsedMs,
            source = frame.source,
            centerNearScore = 0f,
            lowerFloorScore = 0f,
            detail = "Relative-depth frame is stale or out of order; clearance is not established.",
        )

    private fun diagnostic(
        frame: RelativeDepthFrame,
        state: RelativeDepthEvidenceState,
        centerNearScore: Float,
        lowerFloorScore: Float,
        detail: String,
    ): RelativeDepthEvidence =
        RelativeDepthEvidence(
            state = state,
            issue = RelativeDepthEvidenceIssue.NONE,
            frameId = frame.frameId,
            frameElapsedMs = frame.frameElapsedMs,
            source = frame.source,
            centerNearScore = centerNearScore,
            lowerFloorScore = lowerFloorScore,
            detail = detail,
        )
}
