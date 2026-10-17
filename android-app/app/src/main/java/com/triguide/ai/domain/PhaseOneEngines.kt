package com.triguide.ai.domain

/**
 * Explicit non-detector. Returns no objects and never marks the result as real vision.
 */
class UnavailableObjectDetectionEngine : ObjectDetectionEngine {
    override val statusLabel: String = "NOT_IMPLEMENTED"
    override val isRealDetector: Boolean = false

    override fun currentObservation(): EnvironmentObservation {
        return EnvironmentObservation(
            summary = "No detection model is loaded. This is not an obstacle reading.",
            detections = emptyList(),
            synthetic = false,
            realDetector = false,
        )
    }
}

/**
 * Holds still. Vision does not steer the virtual robot in this phase.
 */
class HoldNavigationPolicy : NavigationDecisionPolicy {
    override fun decide(observation: EnvironmentObservation): NavigationDecision {
        return NavigationDecision(
            linearSpeedMps = 0.0,
            steeringRadians = 0.0,
            spokenCue = "No guidance. Object detection is not implemented.",
            basedOnRealVision = false,
        )
    }
}

class MockHardwareController : FutureHardwareController {
    override val connectionLabel: String = "MOCK — no physical robot connected"
    override val isMock: Boolean = true

    override fun dispatch(command: SteeringCommand): HardwareDispatchResult {
        return HardwareDispatchResult(
            accepted = false,
            detail = "Mock only. Steering was not sent to hardware.",
        )
    }
}
