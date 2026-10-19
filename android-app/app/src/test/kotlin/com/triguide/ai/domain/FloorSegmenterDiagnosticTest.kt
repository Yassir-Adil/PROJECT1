package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorSegmenterDiagnosticTest {
    private val sha = "b".repeat(64)
    private val mapping = ImageToFloorMaskMapping(
        imageWidthPx = 64,
        imageHeightPx = 64,
        cropLeftPx = 0,
        cropTopPx = 0,
        cropRightExclusivePx = 64,
        cropBottomExclusivePx = 64,
        rotationDegreesClockwise = 0,
        horizontallyMirrored = false,
        maskWidthCells = 2,
        maskHeightCells = 2,
    )

    private fun rawConfidence(
        values: List<Float> = listOf(0.9f, 0.9f, 0.9f, 0.9f),
        frameId: Long = 1,
        frameElapsedMs: Long = 100,
        latencyMs: Double = 12.0,
    ) = RawFloorSegmenterOutput(
        modelId = "fixture-seg",
        modelSha256 = sha,
        frameId = frameId,
        frameElapsedMs = frameElapsedMs,
        inputWidthPx = 64,
        inputHeightPx = 64,
        mapping = mapping,
        maskWidth = 2,
        maskHeight = 2,
        outputType = FloorSegmenterOutputType.CONFIDENCE_MASK,
        confidenceValues = values,
        inferenceLatencyMs = latencyMs,
        delegate = FloorSegmenterDelegate.CPU,
        modelPresent = true,
        modelValid = true,
        initSucceeded = true,
    )

    @Test
    fun noModelYieldsDiagnosticUnavailable() {
        val diag = FloorSegmenterDiagnostic()
        assertEquals(FloorSegmenterRuntimeState.NO_MODEL, diag.runtimeState())
        assertFalse(diag.offerAnalysisSlot(nowElapsedMs = 50))
        assertEquals(FloorMaskEvidenceState.UNAVAILABLE, diag.latest.evidenceState)
        assertEquals(FloorSegmenterRuntimeState.NO_MODEL, diag.latest.runtimeState)
        assertEquals(FloorMaskEvidenceIssue.NO_FRAME, diag.latest.issue)
    }

    @Test
    fun invalidModelYieldsInvalid() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = false,
            initSucceeded = false,
        )
        assertEquals(FloorSegmenterRuntimeState.INVALID_MODEL, diag.runtimeState())
        diag.offerAnalysisSlot(10)
        assertEquals(FloorMaskEvidenceState.INVALID, diag.latest.evidenceState)
    }

    @Test
    fun initFailureYieldsUnavailable() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = false,
        )
        diag.offerAnalysisSlot(10)
        assertEquals(FloorSegmenterRuntimeState.INIT_FAILED, diag.latest.runtimeState)
        assertEquals(FloorMaskEvidenceState.UNAVAILABLE, diag.latest.evidenceState)
    }

    @Test
    fun malformedOutputInvalid() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        val result = diag.ingest(
            rawConfidence(values = listOf(0.9f)), // wrong size
            nowElapsedMs = 100,
        )
        assertEquals(FloorMaskEvidenceState.INVALID, result.evidenceState)
        assertEquals(FloorMaskEvidenceIssue.INVALID_GRID, result.issue)
    }

    @Test
    fun staleResultStaysStale() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        diag.ingest(rawConfidence(frameId = 1, frameElapsedMs = 100), nowElapsedMs = 100)
        val stale = diag.ingest(
            rawConfidence(frameId = 2, frameElapsedMs = 150),
            nowElapsedMs = 1_200, // age >= 1000 from frameElapsedMs
        )
        assertEquals(FloorMaskEvidenceState.STALE, stale.evidenceState)
        assertEquals(FloorMaskEvidenceIssue.EXPIRED_FRAME, stale.issue)
    }

    @Test
    fun outOfOrderResultStaleOrInvalid() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        diag.ingest(rawConfidence(frameId = 5, frameElapsedMs = 500), nowElapsedMs = 500)
        val ooo = diag.ingest(
            rawConfidence(frameId = 4, frameElapsedMs = 400),
            nowElapsedMs = 520,
        )
        assertEquals(FloorMaskEvidenceState.STALE, ooo.evidenceState)
        assertEquals(FloorMaskEvidenceIssue.NON_MONOTONIC_FRAME, ooo.issue)
    }

    @Test
    fun emptyResultUnavailable() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        val empty = diag.ingest(
            rawConfidence().copy(outputType = FloorSegmenterOutputType.EMPTY, confidenceValues = emptyList()),
            nowElapsedMs = 100,
        )
        assertEquals(FloorMaskEvidenceState.UNAVAILABLE, empty.evidenceState)
    }

    @Test
    fun invalidConfidenceRejected() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        val bad = diag.ingest(
            rawConfidence(values = listOf(0.9f, Float.NaN, 0.9f, 0.9f)),
            nowElapsedMs = 100,
        )
        assertEquals(FloorMaskEvidenceState.INVALID, bad.evidenceState)
        assertEquals(FloorMaskEvidenceIssue.INVALID_CONFIDENCE, bad.issue)
    }

    @Test
    fun missingConfidenceMaskUnavailable() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        val missing = diag.ingest(
            rawConfidence(values = emptyList()),
            nowElapsedMs = 100,
        )
        assertEquals(FloorMaskEvidenceState.UNAVAILABLE, missing.evidenceState)
    }

    @Test
    fun categoryMaskNeverBecomesDiagnosticWhileUnmapped() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        val cat = diag.ingest(
            RawFloorSegmenterOutput(
                modelId = "fixture-seg",
                modelSha256 = sha,
                frameId = 1,
                frameElapsedMs = 100,
                inputWidthPx = 64,
                inputHeightPx = 64,
                mapping = mapping,
                maskWidth = 2,
                maskHeight = 2,
                outputType = FloorSegmenterOutputType.CATEGORY_MASK,
                categoryValues = listOf(1, 1, 1, 1),
                inferenceLatencyMs = 10.0,
                modelPresent = true,
                modelValid = true,
                initSucceeded = true,
            ),
            nowElapsedMs = 100,
        )
        assertEquals(FloorMaskEvidenceState.INVALID, cat.evidenceState)
        assertTrue(cat.detail.contains("UNMAPPED"))
        assertNull(cat.toCandidateOrNull())
    }

    @Test
    fun validResultIsDiagnosticOnlyAndFeedsReplay() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        val ok = diag.ingest(rawConfidence(), nowElapsedMs = 100)
        assertEquals(FloorMaskEvidenceState.DIAGNOSTIC, ok.evidenceState)
        assertNull(ok.issue)
        assertEquals(FloorSegmenterRuntimeState.READY_DIAGNOSTIC, ok.runtimeState)
        val report = diag.replayDiagnostics()
        assertEquals(1, report.sampleCount)
        assertEquals(1, report.acceptedFrameCount)
        val text = listOf(ok.detail, FloorSegmenterModelContract.MAPPING_NOTE).joinToString(" ").lowercase()
        assertFalse(text.contains("safe path"))
        assertFalse(text.contains("clear path"))
        assertFalse(Regex("\\bwalk\\b").containsMatchIn(text))
        assertFalse(text.contains("continue"))
        assertFalse(Regex("\\bturn\\b").containsMatchIn(text))
    }

    @Test
    fun droppedFrameWhenBusy() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        assertTrue(diag.offerAnalysisSlot(10))
        assertFalse(diag.offerAnalysisSlot(11))
        assertEquals(1, diag.droppedFrameCount)
        assertEquals(FloorSegmenterRuntimeState.DROPPED_FRAME, diag.latest.runtimeState)
        diag.releaseAnalysisSlot()
        assertTrue(diag.offerAnalysisSlot(12))
        diag.releaseAnalysisSlot()
    }

    @Test
    fun cpuDelegateRecordedOnValidFixture() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        val ok = diag.ingest(rawConfidence(), nowElapsedMs = 100)
        assertEquals(FloorSegmenterDelegate.CPU, ok.delegate)
    }

    @Test
    fun segmentationOnlyCannotClaimClearObservedInReplayKeys() {
        val diag = FloorSegmenterDiagnostic(
            modelAssetPresent = true,
            modelBytesValid = true,
            initSucceeded = true,
        )
        diag.ingest(rawConfidence(), nowElapsedMs = 100, decisionKey = null, voiceEventKey = null)
        val scene = diag.replayDiagnostics().scenes.single()
        assertTrue(scene.decisionCounts.isEmpty())
        assertTrue(scene.voiceEventCounts.isEmpty())
        assertEquals(1, scene.stateCounts.getValue(FloorMaskEvidenceState.DIAGNOSTIC))
    }
}
