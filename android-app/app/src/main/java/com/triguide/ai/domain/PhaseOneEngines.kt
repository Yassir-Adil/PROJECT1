package com.triguide.ai.domain

/**
 * Holds still. A loaded detector does not steer the virtual robot.
 */
class HoldNavigationPolicy : NavigationDecisionPolicy {
    override fun decide(observation: EnvironmentObservation): NavigationDecision {
        val spokenCue = if (observation.realDetector) {
            "Vision is not steering the robot. ${observation.summary}"
        } else {
            "No guidance. ${observation.summary}"
        }
        return NavigationDecision(
            linearSpeedMps = 0.0,
            steeringRadians = 0.0,
            spokenCue = spokenCue,
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
