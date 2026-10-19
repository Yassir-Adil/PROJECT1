package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloorMaskEvidenceTest {
    private val source = FloorMaskSource(
        sourceId = "offline-clip-01",
        modelId = "candidate-floor-segmenter",
        modelSha256 = FloorMaskModelSha256("a".repeat(64)),
    )
    private val mapping = ImageToFloorMaskMapping(
        imageWidthPx = 300,
        imageHeightPx = 300,
        cropLeftPx = 0,
        cropTopPx = 0,
        cropRightExclusivePx = 300,
        cropBottomExclusivePx = 300,
        rotationDegreesClockwise = 0,
        horizontallyMirrored = false,
        maskWidthCells = 3,
        maskHeightCells = 3,
    )

    private fun candidate(
        values: List<Float> = List(9) { 0.9f },
        frameId: Long = 1,
        frameElapsedMs: Long = 1_000,
    ) = CandidateFloorMaskEvidence(source, frameId, frameElapsedMs, mapping, values)

    @Test
    fun openToyMaskCountsOnlyDiagnosticCellsAndReturnsSequenceToken() {
        val result = FloorMaskEvidenceEvaluator.evaluate(
            candidate(values = List(9) { FloorMaskEvidenceEvaluator.FLOOR_LIKE_CONFIDENCE_THRESHOLD }),
            nowElapsedMs = 1_000,
        )
        assertEquals(FloorMaskEvidenceState.DIAGNOSTIC, result.state)
        assertNull(result.issue)
        assertEquals(9, result.floorLikeCellCount)
        assertEquals(9, result.seedConnectedFloorLikeCellCount)
        assertEquals(1.0, result.seedConnectedFractionOfMask, 0.0)
        assertEquals(AcceptedFloorMaskFrame(1, 1_000), result.acceptedFrame)
    }

    @Test
    fun validCropRotationAndMirrorMetadataAreAccepted() {
        val transformed = mapping.copy(
            cropLeftPx = 20,
            cropTopPx = 30,
            cropRightExclusivePx = 280,
            cropBottomExclusivePx = 270,
            rotationDegreesClockwise = 90,
            horizontallyMirrored = true,
        )
        val result = FloorMaskEvidenceEvaluator.evaluate(candidate().copy(mapping = transformed), 1_000)
        assertEquals(FloorMaskEvidenceState.DIAGNOSTIC, result.state)
        assertEquals(9, result.seedConnectedFloorLikeCellCount)
    }

    @Test
    fun disconnectedUpperPatchIsNotSeedConnected() {
        val result = FloorMaskEvidenceEvaluator.evaluate(
            candidate(listOf(.9f, .9f, .9f, 0f, 0f, 0f, 0f, .9f, 0f)),
            nowElapsedMs = 1_001,
        )
        assertEquals(FloorMaskEvidenceState.DIAGNOSTIC, result.state)
        assertEquals(4, result.floorLikeCellCount)
        assertEquals(1, result.seedConnectedFloorLikeCellCount)
        assertEquals(1.0 / 9.0, result.seedConnectedFractionOfMask, 1e-12)
    }

    @Test
    fun diagonallyTouchingPatchIsNotFourConnected() {
        val result = FloorMaskEvidenceEvaluator.evaluate(
            candidate(listOf(0f, 0f, 0f, .9f, 0f, 0f, 0f, .9f, 0f)),
            nowElapsedMs = 1_000,
        )
        assertEquals(2, result.floorLikeCellCount)
        assertEquals(1, result.seedConnectedFloorLikeCellCount)
    }

    @Test
    fun blockedBottomSeedCannotSelectAnUpperOrSideRegion() {
        val values = MutableList(9) { .9f }.apply { this[7] = .1f }
        val result = FloorMaskEvidenceEvaluator.evaluate(candidate(values), nowElapsedMs = 1_000)
        assertEquals(FloorMaskEvidenceState.DIAGNOSTIC, result.state)
        assertEquals(8, result.floorLikeCellCount)
        assertEquals(0, result.seedConnectedFloorLikeCellCount)
        assertEquals(0.0, result.seedConnectedFractionOfMask, 0.0)
    }

    @Test
    fun evenWidthUsesRightCenterBottomCellAsDocumented() {
        val evenMapping = mapping.copy(maskWidthCells = 4, maskHeightCells = 2)
        val leftCenterOnly = List(8) { if (it == 5) .9f else 0f }
        val rightCenterOnly = List(8) { if (it == 6) .9f else 0f }
        val leftResult = FloorMaskEvidenceEvaluator.evaluate(
            candidate(values = leftCenterOnly).copy(mapping = evenMapping), nowElapsedMs = 1_000,
        )
        val rightResult = FloorMaskEvidenceEvaluator.evaluate(
            candidate(values = rightCenterOnly).copy(mapping = evenMapping), nowElapsedMs = 1_000,
        )
        assertEquals(0, leftResult.seedConnectedFloorLikeCellCount)
        assertEquals(1, rightResult.seedConnectedFloorLikeCellCount)
    }

    @Test
    fun excludedBottomSeedHasZeroComponentEvenWithHighConfidence() {
        val excluded = List(9) { it == 7 }
        val result = FloorMaskEvidenceEvaluator.evaluate(
            candidate().copy(excludedCells = excluded), nowElapsedMs = 1_000,
        )
        assertEquals(FloorMaskEvidenceState.DIAGNOSTIC, result.state)
        assertEquals(8, result.floorLikeCellCount)
        assertEquals(0, result.seedConnectedFloorLikeCellCount)
    }

    @Test
    fun excludedObstacleCellCutsTheOnlyFourConnectedBridge() {
        val vertical = mapping.copy(maskWidthCells = 1, maskHeightCells = 4)
        val result = FloorMaskEvidenceEvaluator.evaluate(
            candidate(values = List(4) { .9f }).copy(
                mapping = vertical,
                excludedCells = listOf(false, true, false, false),
            ),
            nowElapsedMs = 1_000,
        )
        assertEquals(FloorMaskEvidenceState.DIAGNOSTIC, result.state)
        assertEquals(3, result.floorLikeCellCount)
        assertEquals(2, result.seedConnectedFloorLikeCellCount)
        assertEquals(.5, result.seedConnectedFractionOfMask, 0.0)
    }

    @Test
    fun confidenceThresholdIsInclusiveAndJustBelowIsNotFloorLike() {
        val justBelow = FloorMaskEvidenceEvaluator.FLOOR_LIKE_CONFIDENCE_THRESHOLD - .001f
        val values = List(9) { if (it == 7) justBelow else 1f }
        val result = FloorMaskEvidenceEvaluator.evaluate(candidate(values), nowElapsedMs = 1_000)
        assertEquals(8, result.floorLikeCellCount)
        assertEquals(0, result.seedConnectedFloorLikeCellCount)
    }

    @Test
    fun absentFrameIsUnavailableWithNoCountsOrToken() {
        val result = FloorMaskEvidenceEvaluator.evaluate(null, nowElapsedMs = 1_000)
        assertRejected(result, FloorMaskEvidenceState.UNAVAILABLE, FloorMaskEvidenceIssue.NO_FRAME)
    }

    @Test
    fun malformedGeometryAndGridAreInvalid() {
        val malformedMappings = listOf(
            mapping.copy(imageWidthPx = 0),
            mapping.copy(cropRightExclusivePx = 0),
            mapping.copy(cropRightExclusivePx = 301),
            mapping.copy(cropLeftPx = -1),
            mapping.copy(rotationDegreesClockwise = 45),
            mapping.copy(maskWidthCells = 0),
            mapping.copy(maskWidthCells = 1_024, maskHeightCells = 1_024),
        )
        malformedMappings.forEach { bad ->
            assertRejected(
                FloorMaskEvidenceEvaluator.evaluate(candidate().copy(mapping = bad), 1_000),
                FloorMaskEvidenceState.INVALID,
                FloorMaskEvidenceIssue.INVALID_MAPPING,
            )
        }
        assertRejected(
            FloorMaskEvidenceEvaluator.evaluate(candidate(values = List(8) { .9f }), 1_000),
            FloorMaskEvidenceState.INVALID,
            FloorMaskEvidenceIssue.INVALID_GRID,
        )
        assertRejected(
            FloorMaskEvidenceEvaluator.evaluate(
                candidate().copy(excludedCells = List(8) { false }), 1_000,
            ),
            FloorMaskEvidenceState.INVALID,
            FloorMaskEvidenceIssue.INVALID_GRID,
        )
    }

    @Test
    fun nonFiniteAndOutOfRangeConfidenceIsInvalid() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.01f, 1.01f).forEach { bad ->
            val values = List(9) { if (it == 7) bad else .9f }
            assertRejected(
                FloorMaskEvidenceEvaluator.evaluate(candidate(values), 1_000),
                FloorMaskEvidenceState.INVALID,
                FloorMaskEvidenceIssue.INVALID_CONFIDENCE,
            )
        }
    }

    @Test
    fun invalidSourceClockAndFrameAreRejected() {
        val invalidSources = listOf(
            source.copy(sourceId = " "),
            source.copy(modelId = ""),
            source.copy(modelSha256 = FloorMaskModelSha256("not-a-sha256")),
            source.copy(modelSha256 = FloorMaskModelSha256("g".repeat(64))),
        )
        invalidSources.forEach { bad ->
            assertRejected(
                FloorMaskEvidenceEvaluator.evaluate(candidate().copy(source = bad), 1_000),
                FloorMaskEvidenceState.INVALID,
                FloorMaskEvidenceIssue.INVALID_SOURCE,
            )
        }
        assertRejected(
            FloorMaskEvidenceEvaluator.evaluate(candidate(frameId = 0), 1_000),
            FloorMaskEvidenceState.INVALID,
            FloorMaskEvidenceIssue.INVALID_FRAME,
        )
        assertRejected(
            FloorMaskEvidenceEvaluator.evaluate(candidate(frameElapsedMs = -1), 1_000),
            FloorMaskEvidenceState.INVALID,
            FloorMaskEvidenceIssue.INVALID_FRAME,
        )
        assertRejected(
            FloorMaskEvidenceEvaluator.evaluate(candidate(), -1),
            FloorMaskEvidenceState.INVALID,
            FloorMaskEvidenceIssue.INVALID_CLOCK,
        )
    }

    @Test
    fun ageBoundaryIsStaleAndFutureFrameIsInvalid() {
        val limit = FloorMaskEvidenceEvaluator.MAX_FRAME_AGE_MS
        assertEquals(
            FloorMaskEvidenceState.DIAGNOSTIC,
            FloorMaskEvidenceEvaluator.evaluate(candidate(), 1_000 + limit - 1).state,
        )
        assertRejected(
            FloorMaskEvidenceEvaluator.evaluate(candidate(), 1_000 + limit),
            FloorMaskEvidenceState.STALE,
            FloorMaskEvidenceIssue.EXPIRED_FRAME,
        )
        assertRejected(
            FloorMaskEvidenceEvaluator.evaluate(candidate(frameElapsedMs = 1_001), 1_000),
            FloorMaskEvidenceState.INVALID,
            FloorMaskEvidenceIssue.FUTURE_FRAME,
        )
    }

    @Test
    fun duplicateAndOutOfOrderIdOrTimestampCannotAdvanceSequence() {
        val first = FloorMaskEvidenceEvaluator.evaluate(candidate(frameId = 3), 1_000)
        val token = first.acceptedFrame!!
        val rejectedFrames = listOf(
            candidate(frameId = 3, frameElapsedMs = 1_001),
            candidate(frameId = 2, frameElapsedMs = 1_001),
            candidate(frameId = 4, frameElapsedMs = 1_000),
            candidate(frameId = 4, frameElapsedMs = 999),
        )
        rejectedFrames.forEach { frame ->
            assertRejected(
                FloorMaskEvidenceEvaluator.evaluate(frame, 1_001, token),
                FloorMaskEvidenceState.STALE,
                FloorMaskEvidenceIssue.NON_MONOTONIC_FRAME,
            )
        }
        val next = FloorMaskEvidenceEvaluator.evaluate(
            candidate(frameId = 4, frameElapsedMs = 1_002), 1_002, token,
        )
        assertEquals(FloorMaskEvidenceState.DIAGNOSTIC, next.state)
        assertEquals(AcceptedFloorMaskFrame(4, 1_002), next.acceptedFrame)
    }

    private fun assertRejected(
        result: FloorMaskEvidenceResult,
        state: FloorMaskEvidenceState,
        issue: FloorMaskEvidenceIssue,
    ) {
        assertEquals(state, result.state)
        assertEquals(issue, result.issue)
        assertEquals(0, result.floorLikeCellCount)
        assertEquals(0, result.seedConnectedFloorLikeCellCount)
        assertEquals(0.0, result.seedConnectedFractionOfMask, 0.0)
        assertNull(result.acceptedFrame)
    }
}
