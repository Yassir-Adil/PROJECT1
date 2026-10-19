package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceReplayHarnessTest {
    private val source = FloorMaskSource(
        sourceId = "redmi-local-scene",
        modelId = "diagnostic-fixture",
        modelSha256 = FloorMaskModelSha256("a".repeat(64)),
    )
    private val mapping = ImageToFloorMaskMapping(
        imageWidthPx = 320,
        imageHeightPx = 240,
        cropLeftPx = 0,
        cropTopPx = 0,
        cropRightExclusivePx = 320,
        cropBottomExclusivePx = 240,
        rotationDegreesClockwise = 0,
        horizontallyMirrored = false,
        maskWidthCells = 2,
        maskHeightCells = 2,
    )

    private fun candidate(
        frameId: Long,
        frameElapsedMs: Long,
        values: List<Float> = List(4) { .9f },
    ) = CandidateFloorMaskEvidence(
        source = source,
        frameId = frameId,
        frameElapsedMs = frameElapsedMs,
        mapping = mapping,
        floorConfidence = values,
    )

    @Test
    fun replayCountsEvidenceStatesMetricsDecisionsAndVoiceEvents() {
        val report = EvidenceReplayHarness.replay(
            listOf(
                EvidenceReplaySample(
                    sceneId = "open-corridor",
                    candidate = candidate(1, 1_000),
                    evaluatedAtElapsedMs = 1_000,
                    inferenceLatencyMs = 40.0,
                    decisionKey = "UNKNOWN",
                    voiceEventKey = "camera-ready",
                ),
                EvidenceReplaySample(
                    sceneId = "open-corridor",
                    candidate = candidate(1, 1_000),
                    evaluatedAtElapsedMs = 1_001,
                    inferenceLatencyMs = 80.0,
                    decisionKey = "UNKNOWN",
                ),
                EvidenceReplaySample(
                    sceneId = "open-corridor",
                    candidate = candidate(2, 1_100),
                    evaluatedAtElapsedMs = 1_100,
                    inferenceLatencyMs = 160.0,
                    decisionKey = "HOLD",
                    voiceEventKey = "obstacle",
                ),
                EvidenceReplaySample(
                    sceneId = "open-corridor",
                    candidate = null,
                    evaluatedAtElapsedMs = 1_101,
                    decisionKey = "UNKNOWN",
                    failureReason = "mask_unavailable",
                    batteryPct = 81,
                    thermalStatus = "none",
                    memoryMb = 220,
                ),
            ),
        )

        val scene = report.scenes.single()
        assertEquals(4, report.sampleCount)
        assertEquals(2, report.acceptedFrameCount)
        assertEquals(4, scene.sampleCount)
        assertEquals(2, scene.stateCounts.getValue(FloorMaskEvidenceState.DIAGNOSTIC))
        assertEquals(1, scene.stateCounts.getValue(FloorMaskEvidenceState.STALE))
        assertEquals(1, scene.stateCounts.getValue(FloorMaskEvidenceState.UNAVAILABLE))
        assertEquals(1, scene.issueCounts.getValue(FloorMaskEvidenceIssue.NON_MONOTONIC_FRAME))
        assertEquals(1, scene.issueCounts.getValue(FloorMaskEvidenceIssue.NO_FRAME))
        assertEquals(80.0, scene.latencyP50Ms ?: error("p50 was not measured"), 0.0)
        assertEquals(160.0, scene.latencyP90Ms ?: error("p90 was not measured"), 0.0)
        assertEquals(10.0, scene.effectiveFps ?: error("FPS was not measured"), 0.0)
        assertEquals(mapOf("UNKNOWN" to 3, "HOLD" to 1), scene.decisionCounts)
        assertEquals(mapOf("camera-ready" to 1, "obstacle" to 1), scene.voiceEventCounts)
        assertEquals(mapOf("mask_unavailable" to 1), scene.failureReasonCounts)
        assertEquals(1, scene.samplesWithDeviceCounters)
    }

    @Test
    fun sceneStreamsKeepIndependentSequenceTokens() {
        val report = EvidenceReplayHarness.replay(
            listOf(
                EvidenceReplaySample("a", candidate(1, 100), 100),
                EvidenceReplaySample("b", candidate(1, 100), 100),
                EvidenceReplaySample("a", candidate(2, 200), 200),
                EvidenceReplaySample("b", candidate(2, 200), 200),
            ),
        )

        assertEquals(listOf("a", "b"), report.scenes.map { it.sceneId })
        assertTrue(report.scenes.all { it.acceptedFrameCount == 2 })
    }

    @Test
    fun malformedLatencyIsIgnoredFromPercentilesAndCounted() {
        val scene = EvidenceReplayHarness.replay(
            listOf(
                EvidenceReplaySample("scene", candidate(1, 100), 100, Double.NaN),
                EvidenceReplaySample("scene", candidate(2, 200), 200, -1.0),
                EvidenceReplaySample("scene", candidate(3, 300), 300, 50.0, decisionKey = " "),
            ),
        ).scenes.single()

        assertEquals(2, scene.invalidLatencyCount)
        assertEquals(50.0, scene.latencyP50Ms ?: error("p50 was not measured"), 0.0)
        assertEquals(50.0, scene.latencyP90Ms ?: error("p90 was not measured"), 0.0)
        assertEquals(10.0, scene.effectiveFps ?: error("FPS was not measured"), 0.0)
        assertTrue(scene.decisionCounts.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankSceneIdIsRejectedInsteadOfSilentlyMergingEvidence() {
        EvidenceReplayHarness.replay(
            listOf(EvidenceReplaySample(" ", null, evaluatedAtElapsedMs = 0)),
        )
    }
}
