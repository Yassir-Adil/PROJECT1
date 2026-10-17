package com.triguide.ai.robot

import com.triguide.ai.domain.RobotState
import com.triguide.ai.domain.SteeringCommand
import com.triguide.ai.domain.VirtualRobotController
import com.triguide.ai.sim.DriveCommand
import com.triguide.ai.sim.PlanarPose
import com.triguide.ai.sim.TricycleKinematics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SimulatedTricycleController(
    private val wheelbaseMeters: Double = TricycleKinematics.DEFAULT_WHEELBASE_METERS,
) : VirtualRobotController {
    private val stateFlow = MutableStateFlow(RobotState())
    override val state: StateFlow<RobotState> = stateFlow.asStateFlow()

    private var command = SteeringCommand(0.0, 0.0)

    override fun setCommand(command: SteeringCommand) {
        val applied = TricycleKinematics.clampCommand(
            DriveCommand(command.linearSpeedMps, command.steeringRadians),
        )
        this.command = SteeringCommand(applied.speedMetersPerSecond, applied.steeringRadians)
        stateFlow.value = stateFlow.value.copy(
            linearSpeedMps = this.command.linearSpeedMps,
            steeringRadians = this.command.steeringRadians,
        )
    }

    override fun tick(dtSeconds: Double) {
        val current = stateFlow.value
        val next = TricycleKinematics.step(
            pose = current.pose,
            command = DriveCommand(command.linearSpeedMps, command.steeringRadians),
            wheelbaseMeters = wheelbaseMeters,
            dtSeconds = dtSeconds,
        )
        stateFlow.value = current.copy(pose = next)
    }

    override fun reset() {
        command = SteeringCommand(0.0, 0.0)
        stateFlow.value = RobotState()
    }
}
