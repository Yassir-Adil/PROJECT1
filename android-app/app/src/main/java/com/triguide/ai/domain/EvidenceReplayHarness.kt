package com.triguide.ai.domain

import kotlin.math.ceil

/**
 * One deterministic, local-only replay sample for a labeled Redmi scene.
 *
 * The optional decision and voice keys are observations recorded by a caller;
 * this harness counts them but never derives navigation or speech behavior.
 */
data class EvidenceReplaySample(
    val sceneId: String,
    val candidate: CandidateFloorMaskEvidence?,
    val evaluatedAtElapsedMs: Long,
    val inferenceLatencyMs: Double? = null,
    val decisionKey: String? = null,
    val voiceEventKey: String? = null,
    /** Explicit frame id when known (else derived from candidate when present). */
    val frameId: Long? = null,
    /** Short text mask summary (counts/fractions only — never pixels). */
    val maskSummary: String? = null,
    /** Fail-closed reason code when evidence was rejected or held. */
    val failureReason: String? = null,
    /** Optional device counters when available (local diagnostics only). */
    val batteryPct: Int? = null,
    val thermalStatus: String? = null,
    val memoryMb: Int? = null,
)

/** Per-scene diagnostic totals from [EvidenceReplayHarness.replay]. */
data class EvidenceReplaySceneSummary(
    val sceneId: String,
    val sampleCount: Int,
    val stateCounts: Map<FloorMaskEvidenceState, Int>,
    val issueCounts: Map<FloorMaskEvidenceIssue, Int>,
    val acceptedFrameCount: Int,
    val decisionCounts: Map<String, Int>,
    val voiceEventCounts: Map<String, Int>,
    val failureReasonCounts: Map<String, Int>,
    /** Nearest-rank percentile over finite, non-negative latency observations. */
    val latencyP50Ms: Double?,
    /** Nearest-rank percentile over finite, non-negative latency observations. */
    val latencyP90Ms: Double?,
    /** Accepted diagnostic frames per second, or null when fewer than two exist. */
    val effectiveFps: Double?,
    val invalidLatencyCount: Int,
    val samplesWithDeviceCounters: Int,
)

/** Aggregate replay output. This is evidence bookkeeping, never a safety result. */
data class EvidenceReplayReport(
    val scenes: List<EvidenceReplaySceneSummary>,
) {
    val sampleCount: Int get() = scenes.sumOf { it.sampleCount }
    val acceptedFrameCount: Int get() = scenes.sumOf { it.acceptedFrameCount }
}

/**
 * Replays already captured evidence through the existing diagnostic evaluator.
 *
 * This class intentionally has no dependency on [NavigationDecisionEngine], TTS,
 * CameraX, networking, or model loading. A valid mask remains DIAGNOSTIC only.
 */
object EvidenceReplayHarness {
    fun replay(samples: List<EvidenceReplaySample>): EvidenceReplayReport {
        val grouped = samples.groupBy { sample ->
            require(sample.sceneId.isNotBlank()) { "Evidence replay sceneId must not be blank." }
            sample.sceneId
        }
        return EvidenceReplayReport(
            scenes = grouped.map { (sceneId, sceneSamples) -> replayScene(sceneId, sceneSamples) },
        )
    }

    private fun replayScene(
        sceneId: String,
        samples: List<EvidenceReplaySample>,
    ): EvidenceReplaySceneSummary {
        var previousAccepted: AcceptedFloorMaskFrame? = null
        val stateCounts = FloorMaskEvidenceState.values().associateWith { 0 }.toMutableMap()
        val issueCounts = mutableMapOf<FloorMaskEvidenceIssue, Int>()
        val acceptedTimes = mutableListOf<Long>()
        val validLatencies = mutableListOf<Double>()
        var invalidLatencyCount = 0

        samples.forEach { sample ->
            val result = FloorMaskEvidenceEvaluator.evaluate(
                candidate = sample.candidate,
                nowElapsedMs = sample.evaluatedAtElapsedMs,
                previousAcceptedFrame = previousAccepted,
            )
            stateCounts[result.state] = stateCounts.getValue(result.state) + 1
            result.issue?.let { issue -> issueCounts[issue] = issueCounts.getOrDefault(issue, 0) + 1 }
            result.acceptedFrame?.let { accepted ->
                previousAccepted = accepted
                acceptedTimes += accepted.frameElapsedMs
            }

            val latency = sample.inferenceLatencyMs
            if (latency == null) {
                // No latency was recorded for this sample; leave it out of the percentile.
            } else if (latency.isFinite() && latency >= 0.0) {
                validLatencies += latency
            } else {
                invalidLatencyCount += 1
            }
        }

        val deviceCounterSamples = samples.count { sample ->
            sample.batteryPct != null || sample.thermalStatus != null || sample.memoryMb != null
        }
        return EvidenceReplaySceneSummary(
            sceneId = sceneId,
            sampleCount = samples.size,
            stateCounts = stateCounts.toMap(),
            issueCounts = issueCounts.toMap(),
            acceptedFrameCount = acceptedTimes.size,
            decisionCounts = countKeys(samples.map { it.decisionKey }),
            voiceEventCounts = countKeys(samples.map { it.voiceEventKey }),
            failureReasonCounts = countKeys(samples.map { it.failureReason }),
            latencyP50Ms = nearestRank(validLatencies, 0.50),
            latencyP90Ms = nearestRank(validLatencies, 0.90),
            effectiveFps = fps(acceptedTimes),
            invalidLatencyCount = invalidLatencyCount,
            samplesWithDeviceCounters = deviceCounterSamples,
        )
    }

    private fun countKeys(keys: List<String?>): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        keys.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.forEach { key ->
            counts[key] = counts.getOrDefault(key, 0) + 1
        }
        return counts
    }

    private fun nearestRank(values: List<Double>, percentile: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = ceil(percentile * sorted.size).toInt().coerceAtLeast(1) - 1
        return sorted[rank.coerceAtMost(sorted.lastIndex)]
    }

    private fun fps(acceptedTimes: List<Long>): Double? {
        if (acceptedTimes.size < 2) return null
        val elapsedMs = acceptedTimes.last() - acceptedTimes.first()
        if (elapsedMs <= 0L) return null
        return (acceptedTimes.size - 1) * 1_000.0 / elapsedMs
    }
}
