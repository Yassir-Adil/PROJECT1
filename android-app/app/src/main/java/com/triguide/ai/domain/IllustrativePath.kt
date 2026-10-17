package com.triguide.ai.domain

import com.triguide.ai.sim.DriveCommand
import com.triguide.ai.sim.PlanarPose
import com.triguide.ai.sim.TricycleKinematics

/**
 * Short kinematic sketch of the current virtual command.
 * The points are a drawing aid. They are not a verified free path.
 */
object IllustrativePath {
    const val LABEL = "Illustrative path, not verified."
    const val STEP_COUNT = 8
    const val STEP_SECONDS = 0.25

    fun preview(
        command: SteeringCommand,
        steps: Int = STEP_COUNT,
        stepSeconds: Double = STEP_SECONDS,
    ): List<PlanarPose> {
        val safeSteps = steps.coerceAtLeast(0)
        val poses = ArrayList<PlanarPose>(safeSteps + 1)
        var pose = PlanarPose(0.0, 0.0, 0.0)
        poses += pose
        val drive = DriveCommand(command.linearSpeedMps, command.steeringRadians)
        repeat(safeSteps) {
            pose = TricycleKinematics.step(
                pose = pose,
                command = drive,
                wheelbaseMeters = TricycleKinematics.DEFAULT_WHEELBASE_METERS,
                dtSeconds = stepSeconds.coerceAtLeast(0.0),
            )
            poses += pose
        }
        return poses
    }
}
