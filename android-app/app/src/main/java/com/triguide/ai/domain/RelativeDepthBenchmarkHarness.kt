package com.triguide.ai.domain

/**
 * Deterministic, synthetic contract benchmark for the future relative-depth seam.
 *
 * This intentionally benchmarks the evidence policy, not a model or device speed.
 * It has no CameraX, network, asset loading, navigation, or speech dependency.
 * A passing case proves only that the diagnostic analyzer returned its expected
 * fail-closed state; it never proves a clear corridor.
 */
data class RelativeDepthBenchmarkCase(
    val id: String,
    val frame: RelativeDepthFrame?,
    val nowElapsedMs: Long,
    val previousFrameId: Long? = null,
    val expectedState: RelativeDepthEvidenceState,
    val expectedIssue: RelativeDepthEvidenceIssue,
    val expectedRaisesRisk: Boolean,
    /** When set, also assert [RelativeDepthRiskGate] HOLD-only merge behaviour. */
    val detectorHold: Boolean = false,
    val expectedHoldRequired: Boolean? = null,
)

data class RelativeDepthBenchmarkCaseResult(
    val id: String,
    val actualState: RelativeDepthEvidenceState,
    val actualIssue: RelativeDepthEvidenceIssue,
    val actualRaisesRisk: Boolean,
    val holdRequired: Boolean?,
    val riskGateReason: String?,
    val passed: Boolean,
)

data class RelativeDepthBenchmarkReport(
    val results: List<RelativeDepthBenchmarkCaseResult>,
    val stateCounts: Map<RelativeDepthEvidenceState, Int>,
    val riskRaisedCount: Int,
    val holdRequiredCount: Int,
    val failureIds: List<String>,
) {
    val caseCount: Int get() = results.size
    val passedCaseCount: Int get() = results.count { it.passed }
    val allPassed: Boolean get() = failureIds.isEmpty()

    /** Stable text signature for replay comparisons; contains no image data. */
    val deterministicSignature: String
        get() = results.joinToString("|") {
            "${it.id}:${it.actualState}:${it.actualIssue}:${it.actualRaisesRisk}:" +
                "${it.holdRequired}:${it.riskGateReason}:${it.passed}"
        }

    /**
     * Stable, local-only diagnostic export for CI and Redmi replay reports.
     * It contains policy outcomes only; no image, model output, or location data.
     */
    fun toDiagnosticTsv(): String = buildString {
        appendLine(
            "case_id\tactual_state\tactual_issue\trisk_raised\thold_required\trisk_gate_reason\tpassed",
        )
        results.forEach { result ->
            appendLine(
                listOf(
                    result.id,
                    result.actualState.name,
                    result.actualIssue.name,
                    result.actualRaisesRisk,
                    result.holdRequired ?: "-",
                    result.riskGateReason ?: "-",
                    result.passed,
                ).joinToString("\t"),
            )
        }
        appendLine(
            listOf(
                "SUMMARY",
                "case_count=$caseCount",
                "passed_case_count=$passedCaseCount",
                "risk_raised_count=$riskRaisedCount",
                "hold_required_count=$holdRequiredCount",
                "failure_ids=${failureIds.ifEmpty { listOf("-") }.joinToString(",")}",
            ).joinToString("\t"),
        )
        appendLine(
            listOf(
                "STATE_COUNTS",
                *RelativeDepthEvidenceState.values().map { state ->
                    "${state.name}=${stateCounts.getValue(state)}"
                }.toTypedArray(),
            ).joinToString("\t"),
        )
    }
}

object RelativeDepthBenchmarkHarness {
    fun run(cases: List<RelativeDepthBenchmarkCase> = defaultCases()): RelativeDepthBenchmarkReport {
        val seenIds = mutableSetOf<String>()
        val results = cases.map { testCase ->
            require(testCase.id.isNotBlank()) { "Relative-depth benchmark case id must not be blank." }
            require(testCase.id.none { it == '\t' || it == '\n' || it == '\r' }) {
                "Relative-depth benchmark case id must be one TSV-safe line: ${testCase.id}"
            }
            require(seenIds.add(testCase.id)) { "Duplicate relative-depth benchmark case: ${testCase.id}" }

            val evidence = RelativeDepthEvidenceAnalyzer.evaluate(
                frame = testCase.frame,
                nowElapsedMs = testCase.nowElapsedMs,
                previousFrameId = testCase.previousFrameId,
            )
            val merge = if (testCase.expectedHoldRequired != null) {
                RelativeDepthRiskGate.merge(
                    detectorHold = testCase.detectorHold,
                    depthEvidence = evidence,
                )
            } else {
                null
            }
            val evidenceOk = evidence.state == testCase.expectedState &&
                evidence.issue == testCase.expectedIssue &&
                evidence.raisesRisk == testCase.expectedRaisesRisk
            val holdOk = testCase.expectedHoldRequired == null ||
                (merge != null && merge.holdRequired == testCase.expectedHoldRequired)
            RelativeDepthBenchmarkCaseResult(
                id = testCase.id,
                actualState = evidence.state,
                actualIssue = evidence.issue,
                actualRaisesRisk = evidence.raisesRisk,
                holdRequired = merge?.holdRequired,
                riskGateReason = merge?.reason,
                passed = evidenceOk && holdOk,
            )
        }
        val counts = RelativeDepthEvidenceState.values().associateWith { state ->
            results.count { it.actualState == state }
        }
        return RelativeDepthBenchmarkReport(
            results = results,
            stateCounts = counts,
            riskRaisedCount = results.count { it.actualRaisesRisk },
            holdRequiredCount = results.count { it.holdRequired == true },
            failureIds = results.filterNot { it.passed }.map { it.id },
        )
    }

    /** Small fixed scene set for CI/replay; no downloaded data or model weights. */
    fun defaultCases(): List<RelativeDepthBenchmarkCase> = listOf(
        RelativeDepthBenchmarkCase(
            id = "empty",
            frame = null,
            nowElapsedMs = 1_100L,
            expectedState = RelativeDepthEvidenceState.UNAVAILABLE,
            expectedIssue = RelativeDepthEvidenceIssue.NO_FRAME,
            expectedRaisesRisk = false,
            detectorHold = false,
            expectedHoldRequired = false,
        ),
        RelativeDepthBenchmarkCase(
            id = "empty_preserves_detector_hold",
            frame = null,
            nowElapsedMs = 1_100L,
            expectedState = RelativeDepthEvidenceState.UNAVAILABLE,
            expectedIssue = RelativeDepthEvidenceIssue.NO_FRAME,
            expectedRaisesRisk = false,
            detectorHold = true,
            expectedHoldRequired = true,
        ),
        RelativeDepthBenchmarkCase(
            id = "normal_floor_like_gradient",
            frame = frame(values = normalFloorGradient()),
            nowElapsedMs = 1_100L,
            expectedState = RelativeDepthEvidenceState.DIAGNOSTIC_NO_HAZARD,
            expectedIssue = RelativeDepthEvidenceIssue.NONE,
            expectedRaisesRisk = false,
            detectorHold = false,
            expectedHoldRequired = false,
        ),
        RelativeDepthBenchmarkCase(
            id = "normal_floor_cannot_clear_detector_hold",
            frame = frame(values = normalFloorGradient()),
            nowElapsedMs = 1_100L,
            expectedState = RelativeDepthEvidenceState.DIAGNOSTIC_NO_HAZARD,
            expectedIssue = RelativeDepthEvidenceIssue.NONE,
            expectedRaisesRisk = false,
            detectorHold = true,
            expectedHoldRequired = true,
        ),
        RelativeDepthBenchmarkCase(
            id = "center_wall",
            frame = frame(values = List(GRID_CELLS) { 0.90f }),
            nowElapsedMs = 1_100L,
            expectedState = RelativeDepthEvidenceState.WALL_OR_LARGE_OBSTACLE,
            expectedIssue = RelativeDepthEvidenceIssue.NONE,
            expectedRaisesRisk = true,
            detectorHold = false,
            expectedHoldRequired = true,
        ),
        RelativeDepthBenchmarkCase(
            id = "possible_drop",
            frame = frame(values = possibleDropGrid()),
            nowElapsedMs = 1_100L,
            expectedState = RelativeDepthEvidenceState.POSSIBLE_DROP,
            expectedIssue = RelativeDepthEvidenceIssue.NONE,
            expectedRaisesRisk = true,
            detectorHold = false,
            expectedHoldRequired = true,
        ),
        RelativeDepthBenchmarkCase(
            id = "stale",
            frame = frame(frameElapsedMs = 1_000L),
            nowElapsedMs = 1_600L,
            expectedState = RelativeDepthEvidenceState.STALE,
            expectedIssue = RelativeDepthEvidenceIssue.EXPIRED_FRAME,
            expectedRaisesRisk = false,
            detectorHold = true,
            expectedHoldRequired = true,
        ),
        RelativeDepthBenchmarkCase(
            id = "future",
            frame = frame(frameElapsedMs = 1_200L),
            nowElapsedMs = 1_100L,
            expectedState = RelativeDepthEvidenceState.INVALID,
            expectedIssue = RelativeDepthEvidenceIssue.FUTURE_FRAME,
            expectedRaisesRisk = false,
            detectorHold = false,
            expectedHoldRequired = false,
        ),
        RelativeDepthBenchmarkCase(
            id = "malformed_grid",
            frame = frame(width = 3, height = 3, values = List(9) { 0.5f }),
            nowElapsedMs = 1_100L,
            expectedState = RelativeDepthEvidenceState.INVALID,
            expectedIssue = RelativeDepthEvidenceIssue.INVALID_GRID,
            expectedRaisesRisk = false,
            detectorHold = false,
            expectedHoldRequired = false,
        ),
        RelativeDepthBenchmarkCase(
            id = "non_finite_value",
            frame = frame(values = List(GRID_CELLS) { index -> if (index == 7) Float.NaN else 0.5f }),
            nowElapsedMs = 1_100L,
            expectedState = RelativeDepthEvidenceState.INVALID,
            expectedIssue = RelativeDepthEvidenceIssue.INVALID_VALUE,
            expectedRaisesRisk = false,
            detectorHold = false,
            expectedHoldRequired = false,
        ),
        RelativeDepthBenchmarkCase(
            id = "out_of_order",
            frame = frame(frameId = 4L),
            nowElapsedMs = 1_100L,
            previousFrameId = 4L,
            expectedState = RelativeDepthEvidenceState.STALE,
            expectedIssue = RelativeDepthEvidenceIssue.NON_MONOTONIC_FRAME,
            expectedRaisesRisk = false,
            detectorHold = false,
            expectedHoldRequired = false,
        ),
    )

    private const val GRID_WIDTH = 8
    private const val GRID_HEIGHT = 8
    private const val GRID_CELLS = GRID_WIDTH * GRID_HEIGHT

    private fun frame(
        frameId: Long = 1L,
        frameElapsedMs: Long = 1_000L,
        width: Int = GRID_WIDTH,
        height: Int = GRID_HEIGHT,
        values: List<Float> = List(width * height) { 0.2f },
    ) = RelativeDepthFrame(frameId, frameElapsedMs, width, height, values)

    private fun normalFloorGradient(): List<Float> = buildList {
        repeat(GRID_HEIGHT) { row ->
            repeat(GRID_WIDTH) { add(0.15f + row * 0.06f) }
        }
    }

    private fun possibleDropGrid(): List<Float> = buildList {
        repeat(GRID_HEIGHT) { row ->
            val value = if (row == 2 || row == 3 || row == 4) 0.8f else 0.2f
            repeat(GRID_WIDTH) { add(value) }
        }
    }
}
