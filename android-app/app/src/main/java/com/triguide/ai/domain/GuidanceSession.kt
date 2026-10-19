package com.triguide.ai.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Applies the smoother to camera frames and to the synthetic map separately,
 * so a synthetic run does not erase the camera vote.
 *
 * Camera temporal votes count **distinct, valid, newer** inference results
 * (by [SceneObservation.sampleCompletedAtElapsedMs]), not 50 ms UI-loop replays
 * of the same DetectionFrame. Synthetic votes remain call-based and unchanged.
 */
class GuidanceSession(
    engine: NavigationDecisionPolicy = NavigationDecisionEngine(),
    val evidenceRecorder: DecisionEvidenceRecorder = DecisionEvidenceRecorder(),
    val evidenceStats: PerceptionEvidenceStats = PerceptionEvidenceStats(),
) {
    private val cameraSmoother = TemporalDecisionSmoother(engine)
    private val syntheticSmoother = TemporalDecisionSmoother(engine)
    private val rawEngine = engine

    private var lastCameraSampleAtMs: Long? = null
    private var lastCameraDecision: NavigationDecision = NavigationDecisions.unavailable()

    private val decisionState = MutableStateFlow(NavigationDecisions.unavailable())
    val decision: StateFlow<NavigationDecision> = decisionState.asStateFlow()

    fun onCamera(observation: SceneObservation): NavigationDecision {
        if (!isVotableCameraSample(observation)) {
            noteRejectReason(observation)
            return failCamera(observation)
        }
        val sampleAt = observation.sampleCompletedAtElapsedMs
            ?: return failCamera(observation)
        val previous = lastCameraSampleAtMs
        if (previous != null && sampleAt < previous) {
            evidenceStats.increment(PerceptionEvidenceStats.REJECTED_OUT_OF_ORDER)
            return failCamera(
                observation = observation,
                reasonOverride = "The camera result is out of order. Pending votes were cleared. " +
                    "Depth is not measured.",
            )
        }
        if (previous != null && sampleAt == previous) {
            // Same inference replayed on the UI tick: keep the last decision, do not vote again.
            decisionState.value = lastCameraDecision
            return lastCameraDecision
        }
        lastCameraSampleAtMs = sampleAt
        val next = cameraSmoother.update(observation)
        lastCameraDecision = next
        decisionState.value = next
        evidenceRecorder.record(next, elapsedMs = sampleAt)
        return next
    }

    private fun noteRejectReason(observation: SceneObservation) {
        when {
            observation.mode != PerceptionMode.CAMERA_2D || !observation.realDetector ->
                evidenceStats.increment(PerceptionEvidenceStats.REJECTED_EMPTY)
            observation.contradictory ->
                evidenceStats.increment(PerceptionEvidenceStats.REJECTED_INVALID)
            observation.sampleCompletedAtElapsedMs == null ->
                evidenceStats.increment(PerceptionEvidenceStats.REJECTED_INVALID)
            else -> {
                val age = observation.frameAgeMs
                if (age == null || age < 0L || age >= NavigationConfig.STALE_FRAME_MS) {
                    evidenceStats.increment(PerceptionEvidenceStats.REJECTED_STALE)
                } else {
                    evidenceStats.increment(PerceptionEvidenceStats.REJECTED_INVALID)
                }
            }
        }
    }

    fun onSynthetic(observation: SceneObservation): NavigationDecision {
        val next = syntheticSmoother.update(observation)
        decisionState.value = next
        evidenceRecorder.record(next, elapsedMs = observation.sampleCompletedAtElapsedMs ?: 0L)
        return next
    }

    fun rawDecision(observation: SceneObservation): NavigationDecision {
        return rawEngine.decide(observation)
    }

    fun resetCamera() {
        cameraSmoother.reset()
        lastCameraSampleAtMs = null
        lastCameraDecision = NavigationDecisions.unavailable()
    }

    fun resetSynthetic() {
        syntheticSmoother.reset()
    }

    fun markIdle(reason: String) {
        decisionState.value = NavigationDecisions.fromAction(
            action = GuidanceAction.UNKNOWN,
            reason = reason,
            confidence = 0f,
            basedOnRealVision = false,
            metricDepthUsed = false,
            perceptionLabel = NavigationConfig.NONE_LABEL,
        )
    }

    private fun isVotableCameraSample(observation: SceneObservation): Boolean {
        if (observation.mode != PerceptionMode.CAMERA_2D) return false
        if (!observation.realDetector) return false
        if (observation.contradictory) return false
        if (observation.sampleCompletedAtElapsedMs == null) return false
        val age = observation.frameAgeMs ?: return false
        if (age < 0L || age >= NavigationConfig.STALE_FRAME_MS) return false
        return true
    }

    private fun failCamera(
        observation: SceneObservation,
        reasonOverride: String? = null,
    ): NavigationDecision {
        cameraSmoother.reset()
        lastCameraSampleAtMs = null
        val next = if (reasonOverride != null) {
            NavigationDecisions.fromAction(
                action = GuidanceAction.UNKNOWN,
                reason = reasonOverride,
                confidence = 0f,
                basedOnRealVision = observation.realDetector,
                metricDepthUsed = false,
                perceptionLabel = if (observation.realDetector) {
                    NavigationConfig.CAMERA_LABEL
                } else {
                    NavigationConfig.NONE_LABEL
                },
            )
        } else {
            rawEngine.decide(observation)
        }
        lastCameraDecision = next
        decisionState.value = next
        evidenceRecorder.record(
            next,
            elapsedMs = observation.sampleCompletedAtElapsedMs ?: 0L,
        )
        return next
    }
}
