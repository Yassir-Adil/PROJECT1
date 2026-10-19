package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceFusionGateTest {
    private fun risk(level: PathRiskLevel) = PathRiskResult(
        level = level,
        corridorCues = emptyList(),
        dominantInCorridor = null,
        diagnostic = "diag:test_${level.name.lowercase()}",
    )

    private fun mask(
        frameId: Long,
        elapsed: Long,
        fraction: Double = 0.25,
        cells: Int = 8,
        state: FloorMaskEvidenceState = FloorMaskEvidenceState.DIAGNOSTIC,
    ) = FusionMaskFrameSample(
        frameId = frameId,
        frameElapsedMs = elapsed,
        evidenceState = state,
        seedConnectedFractionOfMask = fraction,
        seedConnectedFloorLikeCellCount = cells,
        floorLikeCellCount = cells,
    )

    private fun base(
        mapping: FloorSegmenterModelContract.ClassMappingStatus =
            FloorSegmenterModelContract.ClassMappingStatus.EXPERIMENTAL_INDOOR_FLOOR_DOCUMENTED,
        frames: List<FusionMaskFrameSample> = listOf(
            mask(1, 100),
            mask(2, 200),
            mask(3, 300),
        ),
        detector: PathRiskLevel = PathRiskLevel.CLEAR_OBSERVED,
        now: Long = 350,
        healthy: Boolean = true,
        mappingValid: Boolean = true,
        unknownBlocks: Boolean = false,
        left: FusionSideRegionSample? = null,
        right: FusionSideRegionSample? = null,
    ) = FusionEvidenceSnapshot(
        nowElapsedMs = now,
        cameraHealthy = healthy,
        mappingProvenanceValid = mappingValid,
        classMappingStatus = mapping,
        maskFrames = frames,
        detectorRisk = risk(detector),
        unknownRegionBlocksCandidate = unknownBlocks,
        leftAlternative = left,
        rightAlternative = right,
    )

    @Test
    fun unmappedNeverClears() {
        val result = EvidenceFusionGate.evaluate(
            base(mapping = FloorSegmenterModelContract.ClassMappingStatus.UNMAPPED),
        )
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("class_mapping_unmapped", result.reasonCode)
    }

    @Test
    fun productionContractStaysUnmapped() {
        assertEquals(
            FloorSegmenterModelContract.ClassMappingStatus.UNMAPPED,
            FloorSegmenterModelContract.classMappingStatus,
        )
    }

    @Test
    fun detectorOnlyBlockedHoldsWithoutMaskClear() {
        val result = EvidenceFusionGate.evaluate(
            base(
                mapping = FloorSegmenterModelContract.ClassMappingStatus.UNMAPPED,
                detector = PathRiskLevel.BLOCKED,
                frames = emptyList(),
            ),
        )
        assertEquals(FusionPathVerdict.HOLD, result.verdict)
    }

    @Test
    fun emptyFramesUnknown() {
        val result = EvidenceFusionGate.evaluate(base(frames = emptyList(), detector = PathRiskLevel.UNKNOWN))
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("no_mask_frames", result.reasonCode)
    }

    @Test
    fun staleMasksDoNotClear() {
        val result = EvidenceFusionGate.evaluate(
            base(
                frames = listOf(mask(1, 100), mask(2, 200), mask(3, 300)),
                now = 2_000,
                detector = PathRiskLevel.UNKNOWN,
            ),
        )
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("no_fresh_valid_mask", result.reasonCode)
    }

    @Test
    fun futureMaskFramesDoNotClear() {
        val result = EvidenceFusionGate.evaluate(
            base(
                frames = listOf(mask(1, 400), mask(2, 500), mask(3, 600)),
                now = 350,
            ),
        )
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("no_fresh_valid_mask", result.reasonCode)
    }

    @Test
    fun malformedMaskSummaryDoesNotClear() {
        val result = EvidenceFusionGate.evaluate(
            base(
                frames = listOf(
                    mask(1, 100, fraction = 1.01, cells = 8),
                    mask(2, 200, fraction = 0.25, cells = 3),
                    mask(3, 300, fraction = 0.25, cells = 8),
                ),
            ),
        )
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("insufficient_agreement", result.reasonCode)
    }

    @Test
    fun descendingMaskFrameIdsDoNotClear() {
        val result = EvidenceFusionGate.evaluate(
            base(
                frames = listOf(mask(2, 100), mask(1, 200), mask(3, 300)),
            ),
        )
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("non_monotonic_mask_frames", result.reasonCode)
    }

    @Test
    fun singleFrameDoesNotClear() {
        val result = EvidenceFusionGate.evaluate(
            base(frames = listOf(mask(1, 100)), detector = PathRiskLevel.CLEAR_OBSERVED),
        )
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("insufficient_agreement", result.reasonCode)
    }

    @Test
    fun duplicateFrameIdsDoNotCountAsAgreement() {
        val result = EvidenceFusionGate.evaluate(
            base(
                frames = listOf(mask(1, 100), mask(1, 200), mask(1, 300)),
                detector = PathRiskLevel.CLEAR_OBSERVED,
            ),
        )
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("insufficient_agreement", result.reasonCode)
    }

    @Test
    fun unhealthyCameraUnknown() {
        val result = EvidenceFusionGate.evaluate(base(healthy = false))
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("camera_unhealthy", result.reasonCode)
    }

    @Test
    fun invalidMappingUnknown() {
        val result = EvidenceFusionGate.evaluate(base(mappingValid = false))
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("mapping_invalid", result.reasonCode)
    }

    @Test
    fun detectorHazardHoldsEvenWithStrongMasks() {
        val result = EvidenceFusionGate.evaluate(base(detector = PathRiskLevel.BLOCKED))
        assertEquals(FusionPathVerdict.HOLD, result.verdict)
        assertEquals("detector_hazard", result.reasonCode)
        assertEquals(FusionTurnHint.NONE, result.turnHint)
    }

    @Test
    fun turnRequiresIndependentSideEvidence() {
        val left = FusionSideRegionSample(
            side = FusionTurnHint.SLIGHT_LEFT,
            connectedFraction = 0.2,
            obstacleFree = true,
            agreeingDistinctFrames = 3,
        )
        val result = EvidenceFusionGate.evaluate(
            base(detector = PathRiskLevel.BLOCKED, left = left),
        )
        assertEquals(FusionPathVerdict.HOLD, result.verdict)
        assertEquals(FusionTurnHint.SLIGHT_LEFT, result.turnHint)
    }

    @Test
    fun bothSidesOkYieldsNoTurnAmbiguity() {
        val left = FusionSideRegionSample(FusionTurnHint.SLIGHT_LEFT, 0.2, true, 3)
        val right = FusionSideRegionSample(FusionTurnHint.SLIGHT_RIGHT, 0.2, true, 3)
        val result = EvidenceFusionGate.evaluate(
            base(detector = PathRiskLevel.BLOCKED, left = left, right = right),
        )
        assertEquals(FusionTurnHint.NONE, result.turnHint)
    }

    @Test
    fun unknownRegionBlocksClear() {
        val result = EvidenceFusionGate.evaluate(base(unknownBlocks = true))
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("unknown_region_blocks", result.reasonCode)
    }

    @Test
    fun detectorUnknownRefusesClearDespiteMasks() {
        val result = EvidenceFusionGate.evaluate(base(detector = PathRiskLevel.UNKNOWN))
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertEquals("detector_unknown", result.reasonCode)
    }

    @Test
    fun documentedMappingWithAgreementCanClearObserved() {
        val result = EvidenceFusionGate.evaluate(base())
        assertEquals(FusionPathVerdict.CLEAR_OBSERVED, result.verdict)
        assertEquals("fused_positive_region", result.reasonCode)
        assertEquals(FusionTurnHint.NONE, result.turnHint)
        assertEquals(3, result.agreeingDistinctFrames)
    }

    @Test
    fun diagnosticsAvoidBannedPathWording() {
        val result = EvidenceFusionGate.evaluate(base())
        val text = result.diagnostic.lowercase()
        assertFalse(text.contains("safe path"))
        assertFalse(text.contains("clear path"))
        assertFalse(Regex("\\bwalk\\b").containsMatchIn(text))
        assertFalse(text.contains("continue"))
        assertTrue(text.contains("fusion"))
    }

    @Test
    fun segmentationOnlyUnmappedCannotProduceClear() {
        val result = EvidenceFusionGate.evaluate(
            base(
                mapping = FloorSegmenterModelContract.ClassMappingStatus.UNMAPPED,
                detector = PathRiskLevel.UNKNOWN,
                frames = listOf(mask(1, 100, 0.9, 50), mask(2, 200, 0.9, 50), mask(3, 300, 0.9, 50)),
            ),
        )
        assertEquals(FusionPathVerdict.UNKNOWN, result.verdict)
        assertFalse(result.verdict == FusionPathVerdict.CLEAR_OBSERVED)
    }
}
