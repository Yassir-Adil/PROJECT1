package com.triguide.ai.domain

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
