package com.triguide.ai.domain

/**
 * Pure evidence fusion for camera guidance.
 *
 * Separates synthetic-simulator clearance from physical-camera clearance.
 * Does not speak, drive TTS, or own CameraX. Callers must not treat
 * [FusionPathVerdict.CLEAR_OBSERVED] as permission to walk without a cane.
 *
 * Class-mapped floor confidence is required; UNMAPPED segmenters cannot clear.
 */
enum class FusionPathVerdict {
    /** Positive walkable-region evidence that passed all gate checks. Not a safety certificate. */
    CLEAR_OBSERVED,

    /** Corridor blocked or hazard raised by detector / contradictory mask. */
    HOLD,

    /** Insufficient, stale, unmapped, or disagreeing evidence. */
    UNKNOWN,
}

enum class FusionTurnHint {
    NONE,
    SLIGHT_LEFT,
    LEFT,
    SLIGHT_RIGHT,
    RIGHT,
}

data class FusionMaskFrameSample(
    val frameId: Long,
    val frameElapsedMs: Long,
    val evidenceState: FloorMaskEvidenceState,
    val seedConnectedFractionOfMask: Double,
    val seedConnectedFloorLikeCellCount: Int,
    val floorLikeCellCount: Int,
)

data class FusionSideRegionSample(
    val side: FusionTurnHint,
    val connectedFraction: Double,
    val obstacleFree: Boolean,
    val agreeingDistinctFrames: Int,
)

/**
 * Snapshot consumed by [EvidenceFusionGate.evaluate].
 * All timestamps must be the same monotonic clock used by FloorMaskEvidence.
 */
data class FusionEvidenceSnapshot(
    val nowElapsedMs: Long,
    val cameraHealthy: Boolean,
    val mappingProvenanceValid: Boolean,
    val classMappingStatus: FloorSegmenterModelContract.ClassMappingStatus,
    /** Newest-first or chronological; gate uses chronological evaluation. */
    val maskFrames: List<FusionMaskFrameSample>,
    val detectorRisk: PathRiskResult,
    val unknownRegionBlocksCandidate: Boolean = false,
    val leftAlternative: FusionSideRegionSample? = null,
    val rightAlternative: FusionSideRegionSample? = null,
)

data class FusionGateResult(
    val verdict: FusionPathVerdict,
    val turnHint: FusionTurnHint,
    val reasonCode: String,
    val agreeingDistinctFrames: Int,
    val diagnostic: String,
)

/**
 * Fail-closed fusion. CLEAR only when every documented condition holds.
 * Turns require an independent positive side region — never absence of boxes.
 */
object EvidenceFusionGate {
    const val MIN_SEED_CONNECTED_FRACTION = 0.12
    const val MIN_SEED_CONNECTED_CELLS = 4
    const val MIN_AGREEING_DISTINCT_FRAMES = 3
    const val MIN_SIDE_CONNECTED_FRACTION = 0.10
    const val MIN_SIDE_AGREEING_FRAMES = 3

    fun evaluate(input: FusionEvidenceSnapshot): FusionGateResult {
        if (input.nowElapsedMs < 0) {
            return reject(FusionPathVerdict.UNKNOWN, "invalid_clock", 0, "nowElapsedMs < 0")
        }
        if (!input.cameraHealthy) {
            return reject(FusionPathVerdict.UNKNOWN, "camera_unhealthy", 0, "Camera/geometry unhealthy.")
        }
        if (!input.mappingProvenanceValid) {
            return reject(FusionPathVerdict.UNKNOWN, "mapping_invalid", 0, "Mask provenance/mapping invalid.")
        }
        if (input.classMappingStatus == FloorSegmenterModelContract.ClassMappingStatus.UNMAPPED) {
            return holdOrUnknownFromDetector(
                input,
                reasonCode = "class_mapping_unmapped",
                detail = "Class mapping UNMAPPED; segmenter cannot supply clearance.",
            )
        }

        val chronological = input.maskFrames.sortedBy { it.frameElapsedMs }
        if (chronological.isEmpty()) {
            return holdOrUnknownFromDetector(input, "no_mask_frames", "No mask frames.")
        }
        if (hasDescendingFrameIds(chronological)) {
            return holdOrUnknownFromDetector(
                input,
                "non_monotonic_mask_frames",
                "Mask frame IDs move backwards in chronological order.",
            )
        }

        val freshValid = chronological.filter {
            isFreshValidMaskFrame(it, input.nowElapsedMs)
        }
        if (freshValid.isEmpty()) {
            return holdOrUnknownFromDetector(input, "no_fresh_valid_mask", "No fresh valid mask region.")
        }

        val agreeing = distinctAgreeing(freshValid)
        if (agreeing < MIN_AGREEING_DISTINCT_FRAMES) {
            return holdOrUnknownFromDetector(
                input,
                "insufficient_agreement",
                "Agreeing distinct frames=$agreeing < $MIN_AGREEING_DISTINCT_FRAMES",
                agreeing,
            )
        }

        when (input.detectorRisk.level) {
            PathRiskLevel.BLOCKED, PathRiskLevel.POSSIBLE_OBSTACLE ->
                return FusionGateResult(
                    verdict = FusionPathVerdict.HOLD,
                    turnHint = turnFromAlternatives(input, pathBlocked = true),
                    reasonCode = "detector_hazard",
                    agreeingDistinctFrames = agreeing,
                    diagnostic = "diag:fusion_hold detector=${input.detectorRisk.level} ${input.detectorRisk.diagnostic}",
                )
            PathRiskLevel.UNKNOWN ->
                return reject(
                    FusionPathVerdict.UNKNOWN,
                    "detector_unknown",
                    agreeing,
                    "Detector risk UNKNOWN; refuse clearance.",
                )
            PathRiskLevel.CLEAR_OBSERVED -> {
                // Detector-only CLEAR is not accepted; fusion still requires mask agreement above.
            }
        }

        if (input.unknownRegionBlocksCandidate) {
            return reject(
                FusionPathVerdict.UNKNOWN,
                "unknown_region_blocks",
                agreeing,
                "Unknown region blocks candidate path.",
            )
        }

        return FusionGateResult(
            verdict = FusionPathVerdict.CLEAR_OBSERVED,
            turnHint = FusionTurnHint.NONE,
            reasonCode = "fused_positive_region",
            agreeingDistinctFrames = agreeing,
            diagnostic = "diag:fusion_clear_observed frames=$agreeing fraction=${freshValid.last().seedConnectedFractionOfMask}",
        )
    }

    private fun holdOrUnknownFromDetector(
        input: FusionEvidenceSnapshot,
        reasonCode: String,
        detail: String,
        agreeing: Int = 0,
    ): FusionGateResult {
        return when (input.detectorRisk.level) {
            PathRiskLevel.BLOCKED, PathRiskLevel.POSSIBLE_OBSTACLE ->
                FusionGateResult(
                    verdict = FusionPathVerdict.HOLD,
                    turnHint = turnFromAlternatives(input, pathBlocked = true),
                    reasonCode = reasonCode,
                    agreeingDistinctFrames = agreeing,
                    diagnostic = "diag:fusion_hold $reasonCode | $detail | ${input.detectorRisk.diagnostic}",
                )
            else ->
                reject(FusionPathVerdict.UNKNOWN, reasonCode, agreeing, detail)
        }
    }

    private fun turnFromAlternatives(
        input: FusionEvidenceSnapshot,
        pathBlocked: Boolean,
    ): FusionTurnHint {
        if (!pathBlocked) return FusionTurnHint.NONE
        val leftOk = input.leftAlternative?.let { sideOk(it) } == true
        val rightOk = input.rightAlternative?.let { sideOk(it) } == true
        return when {
            leftOk && !rightOk -> input.leftAlternative!!.side
            rightOk && !leftOk -> input.rightAlternative!!.side
            else -> FusionTurnHint.NONE // ambiguity or both/neither
        }
    }

    private fun sideOk(side: FusionSideRegionSample): Boolean {
        if (side.side == FusionTurnHint.NONE) return false
        if (!side.obstacleFree) return false
        if (!side.connectedFraction.isFinite() || side.connectedFraction < MIN_SIDE_CONNECTED_FRACTION) return false
        if (side.agreeingDistinctFrames < MIN_SIDE_AGREEING_FRAMES) return false
        return true
    }

    private fun distinctAgreeing(frames: List<FusionMaskFrameSample>): Int {
        // Distinct frame IDs only; duplicate IDs do not count twice.
        return frames.map { it.frameId }.toSet().size
    }

    private fun isFreshValidMaskFrame(
        frame: FusionMaskFrameSample,
        nowElapsedMs: Long,
    ): Boolean {
        if (frame.evidenceState != FloorMaskEvidenceState.DIAGNOSTIC) return false
        if (frame.frameId <= 0L || frame.frameElapsedMs < 0L) return false
        // Reject future timestamps explicitly; otherwise a negative age would look fresh.
        if (frame.frameElapsedMs > nowElapsedMs) return false
        if (nowElapsedMs - frame.frameElapsedMs >= FloorMaskEvidenceEvaluator.MAX_FRAME_AGE_MS) {
            return false
        }
        if (!frame.seedConnectedFractionOfMask.isFinite() ||
            frame.seedConnectedFractionOfMask !in MIN_SEED_CONNECTED_FRACTION..1.0
        ) {
            return false
        }
        if (frame.floorLikeCellCount < MIN_SEED_CONNECTED_CELLS ||
            frame.seedConnectedFloorLikeCellCount !in MIN_SEED_CONNECTED_CELLS..frame.floorLikeCellCount
        ) {
            return false
        }
        return true
    }

    private fun hasDescendingFrameIds(frames: List<FusionMaskFrameSample>): Boolean {
        var previous: Long? = null
        for (frame in frames) {
            if (previous != null && frame.frameId < previous!!) return true
            previous = frame.frameId
        }
        return false
    }

    private fun reject(
        verdict: FusionPathVerdict,
        reasonCode: String,
        agreeing: Int,
        detail: String,
    ) = FusionGateResult(
        verdict = verdict,
        turnHint = FusionTurnHint.NONE,
        reasonCode = reasonCode,
        agreeingDistinctFrames = agreeing,
        diagnostic = "diag:fusion_${verdict.name.lowercase()} $reasonCode | $detail",
    )
}
