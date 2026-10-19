package com.triguide.ai.domain

/** The model file's 64-digit SHA-256 hex digest; validated by the evaluator. */
@JvmInline
value class FloorMaskModelSha256(val hex: String)

/** Provenance for a candidate mask. */
data class FloorMaskSource(
    val sourceId: String,
    val modelId: String,
    val modelSha256: FloorMaskModelSha256,
)

/**
 * The mask is row-major, top-left first. It covers the source-image crop after clockwise rotation,
 * then horizontal mirroring in the rotated image, then uniform resampling to [maskWidthCells] x
 * [maskHeightCells] using cell centers. This is mapping metadata only; this evaluator does not
 * project image pixels or verify the producing model's resampling implementation.
 */
data class ImageToFloorMaskMapping(
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val cropLeftPx: Int,
    val cropTopPx: Int,
    val cropRightExclusivePx: Int,
    val cropBottomExclusivePx: Int,
    val rotationDegreesClockwise: Int,
    val horizontallyMirrored: Boolean,
    val maskWidthCells: Int,
    val maskHeightCells: Int,
)

/** All timestamps must be from the same monotonic elapsed-time clock, not wall-clock time. */
data class CandidateFloorMaskEvidence(
    val source: FloorMaskSource,
    val frameId: Long,
    val frameElapsedMs: Long,
    val mapping: ImageToFloorMaskMapping,
    val floorConfidence: List<Float>,
    /** True excludes a cell from the diagnostic component even if its floor confidence is high. */
    val excludedCells: List<Boolean>? = null,
)

/** Pass back only a token returned by a successful evaluation; retain it after a rejected frame. */
data class AcceptedFloorMaskFrame(val frameId: Long, val frameElapsedMs: Long)

enum class FloorMaskEvidenceState { UNAVAILABLE, STALE, INVALID, DIAGNOSTIC }

enum class FloorMaskEvidenceIssue {
    NO_FRAME,
    INVALID_CLOCK,
    INVALID_SOURCE,
    INVALID_FRAME,
    INVALID_MAPPING,
    INVALID_GRID,
    INVALID_CONFIDENCE,
    FUTURE_FRAME,
    EXPIRED_FRAME,
    NON_MONOTONIC_FRAME,
}

/** Counts are image-mask diagnostics, never a walkability, clearance, branch, or movement result. */
data class FloorMaskEvidenceResult(
    val state: FloorMaskEvidenceState,
    val issue: FloorMaskEvidenceIssue? = null,
    val floorLikeCellCount: Int = 0,
    val seedConnectedFloorLikeCellCount: Int = 0,
    /** Seed-connected count divided by *all* mask cells, including excluded cells. */
    val seedConnectedFractionOfMask: Double = 0.0,
    val acceptedFrame: AcceptedFloorMaskFrame? = null,
)

/** Pure, diagnostic-only evaluation of an untrusted candidate floor-confidence mask. */
object FloorMaskEvidenceEvaluator {
    const val FLOOR_LIKE_CONFIDENCE_THRESHOLD = 0.70f
    const val MAX_FRAME_AGE_MS = 1_000L // Age equal to this limit is stale.
    const val MAX_MASK_CELLS = 262_144
    const val MAX_IMAGE_DIMENSION_PX = 8_192

    private val sha256Hex = Regex("[0-9a-fA-F]{64}")

    fun evaluate(
        candidate: CandidateFloorMaskEvidence?,
        nowElapsedMs: Long,
        previousAcceptedFrame: AcceptedFloorMaskFrame? = null,
    ): FloorMaskEvidenceResult {
        if (candidate == null) return rejected(FloorMaskEvidenceState.UNAVAILABLE, FloorMaskEvidenceIssue.NO_FRAME)
        if (nowElapsedMs < 0 || previousAcceptedFrame?.let { it.frameId <= 0 || it.frameElapsedMs < 0 } == true) {
            return rejected(FloorMaskEvidenceState.INVALID, FloorMaskEvidenceIssue.INVALID_CLOCK)
        }
        if (candidate.source.sourceId.isBlank() || candidate.source.modelId.isBlank() ||
            !sha256Hex.matches(candidate.source.modelSha256.hex)
        ) {
            return rejected(FloorMaskEvidenceState.INVALID, FloorMaskEvidenceIssue.INVALID_SOURCE)
        }
        if (candidate.frameId <= 0 || candidate.frameElapsedMs < 0) {
            return rejected(FloorMaskEvidenceState.INVALID, FloorMaskEvidenceIssue.INVALID_FRAME)
        }

        val mapping = candidate.mapping
        if (!validMapping(mapping)) {
            return rejected(FloorMaskEvidenceState.INVALID, FloorMaskEvidenceIssue.INVALID_MAPPING)
        }
        val cellCount = mapping.maskWidthCells * mapping.maskHeightCells // Bounded by validMapping.
        if (candidate.floorConfidence.size != cellCount ||
            candidate.excludedCells?.size?.let { it != cellCount } == true
        ) {
            return rejected(FloorMaskEvidenceState.INVALID, FloorMaskEvidenceIssue.INVALID_GRID)
        }
        if (candidate.floorConfidence.any { !it.isFinite() || it < 0f || it > 1f }) {
            return rejected(FloorMaskEvidenceState.INVALID, FloorMaskEvidenceIssue.INVALID_CONFIDENCE)
        }
        if (candidate.frameElapsedMs > nowElapsedMs) {
            return rejected(FloorMaskEvidenceState.INVALID, FloorMaskEvidenceIssue.FUTURE_FRAME)
        }
        if (nowElapsedMs - candidate.frameElapsedMs >= MAX_FRAME_AGE_MS) {
            return rejected(FloorMaskEvidenceState.STALE, FloorMaskEvidenceIssue.EXPIRED_FRAME)
        }
        if (previousAcceptedFrame != null &&
            (candidate.frameId <= previousAcceptedFrame.frameId ||
                candidate.frameElapsedMs <= previousAcceptedFrame.frameElapsedMs)
        ) {
            return rejected(FloorMaskEvidenceState.STALE, FloorMaskEvidenceIssue.NON_MONOTONIC_FRAME)
        }

        val width = mapping.maskWidthCells
        val height = mapping.maskHeightCells
        val eligible = BooleanArray(cellCount) { index ->
            candidate.excludedCells?.get(index) != true &&
                candidate.floorConfidence[index] >= FLOOR_LIKE_CONFIDENCE_THRESHOLD
        }
        val floorLikeCount = eligible.count { it }
        // A single central bottom cell is the only seed. A missing/occluded seed gives zero,
        // even when upper or side patches look floor-like. Even widths use the right-center cell.
        val seed = (height - 1) * width + width / 2
        var connectedCount = 0
        if (eligible[seed]) {
            val seen = BooleanArray(cellCount)
            val queue = IntArray(cellCount)
            var head = 0
            var tail = 0
            seen[seed] = true
            queue[tail++] = seed

            fun enqueue(index: Int) {
                if (eligible[index] && !seen[index]) {
                    seen[index] = true
                    queue[tail++] = index
                }
            }

            while (head < tail) {
                val index = queue[head++]
                connectedCount++
                val x = index % width
                val y = index / width
                if (x > 0) enqueue(index - 1)
                if (x + 1 < width) enqueue(index + 1)
                if (y > 0) enqueue(index - width)
                if (y + 1 < height) enqueue(index + width)
            }
        }
        return FloorMaskEvidenceResult(
            state = FloorMaskEvidenceState.DIAGNOSTIC,
            floorLikeCellCount = floorLikeCount,
            seedConnectedFloorLikeCellCount = connectedCount,
            seedConnectedFractionOfMask = connectedCount.toDouble() / cellCount,
            acceptedFrame = AcceptedFloorMaskFrame(candidate.frameId, candidate.frameElapsedMs),
        )
    }

    private fun validMapping(mapping: ImageToFloorMaskMapping): Boolean {
        if (mapping.imageWidthPx !in 1..MAX_IMAGE_DIMENSION_PX ||
            mapping.imageHeightPx !in 1..MAX_IMAGE_DIMENSION_PX ||
            mapping.maskWidthCells !in 1..MAX_MASK_CELLS ||
            mapping.maskHeightCells !in 1..MAX_MASK_CELLS ||
            mapping.maskWidthCells.toLong() * mapping.maskHeightCells > MAX_MASK_CELLS ||
            mapping.rotationDegreesClockwise !in setOf(0, 90, 180, 270)
        ) return false
        return mapping.cropLeftPx >= 0 && mapping.cropTopPx >= 0 &&
            mapping.cropRightExclusivePx <= mapping.imageWidthPx &&
            mapping.cropBottomExclusivePx <= mapping.imageHeightPx &&
            mapping.cropLeftPx < mapping.cropRightExclusivePx &&
            mapping.cropTopPx < mapping.cropBottomExclusivePx
    }

    private fun rejected(state: FloorMaskEvidenceState, issue: FloorMaskEvidenceIssue) =
        FloorMaskEvidenceResult(state = state, issue = issue)
}
