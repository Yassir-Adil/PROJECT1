package com.triguide.ai.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Applies the smoother to camera frames and to the synthetic map separately,
 * so a synthetic run does not erase the camera vote.
 */
class GuidanceSession(
    engine: NavigationDecisionEngine = NavigationDecisionEngine(),
) {
    private val cameraSmoother = TemporalDecisionSmoother(engine)
    private val syntheticSmoother = TemporalDecisionSmoother(engine)
    private val rawEngine = engine

    private val decisionState = MutableStateFlow(NavigationDecisions.unavailable())
    val decision: StateFlow<NavigationDecision> = decisionState.asStateFlow()

    fun onCamera(observation: SceneObservation): NavigationDecision {
        val next = cameraSmoother.update(observation)
        decisionState.value = next
        return next
    }

    fun onSynthetic(observation: SceneObservation): NavigationDecision {
        val next = syntheticSmoother.update(observation)
        decisionState.value = next
        return next
    }

    fun rawDecision(observation: SceneObservation): NavigationDecision {
        return rawEngine.decide(observation)
    }

    fun resetCamera() {
        cameraSmoother.reset()
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
}
