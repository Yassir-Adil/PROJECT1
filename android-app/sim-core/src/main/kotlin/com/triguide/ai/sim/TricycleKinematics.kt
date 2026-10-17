package com.triguide.ai.sim

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Planar pose of the rear-axle midpoint.
 * Heading is radians, counter-clockwise from the +x axis.
 * +y is to the left of +x. This is a math frame, not a screen frame.
 */
data class PlanarPose(
    val xMeters: Double,
    val yMeters: Double,
    val headingRadians: Double,
)

/**
 * Manual drive command for the virtual tricycle.
 * Positive [steeringRadians] turns the front wheel toward +y (left) when moving forward.
 */
data class DriveCommand(
    val speedMetersPerSecond: Double,
    val steeringRadians: Double,
)

/**
 * Bicycle-model kinematics for a virtual three-wheel chassis:
 * two fixed rear wheels (track width is ignored) and one steered front wheel.
 * Integration is explicit Euler. Not a physical robot.
 */
object TricycleKinematics {
    const val DEFAULT_WHEELBASE_METERS: Double = 0.35
    const val MAX_SPEED_METERS_PER_SECOND: Double = 1.2
    const val MAX_STEERING_RADIANS: Double = Math.PI / 6.0

    fun clampCommand(command: DriveCommand): DriveCommand {
        return DriveCommand(
            speedMetersPerSecond = command.speedMetersPerSecond.coerceIn(
                -MAX_SPEED_METERS_PER_SECOND,
                MAX_SPEED_METERS_PER_SECOND,
            ),
            steeringRadians = command.steeringRadians.coerceIn(
                -MAX_STEERING_RADIANS,
                MAX_STEERING_RADIANS,
            ),
        )
    }

    fun step(
        pose: PlanarPose,
        command: DriveCommand,
        wheelbaseMeters: Double,
        dtSeconds: Double,
    ): PlanarPose {
        require(wheelbaseMeters > 0.0) { "wheelbaseMeters must be > 0" }
        require(dtSeconds >= 0.0) { "dtSeconds must be >= 0" }
        if (dtSeconds == 0.0) return pose

        val applied = clampCommand(command)
        val yawRate = applied.speedMetersPerSecond * tan(applied.steeringRadians) / wheelbaseMeters
        val heading = pose.headingRadians + yawRate * dtSeconds
        val x = pose.xMeters + applied.speedMetersPerSecond * cos(pose.headingRadians) * dtSeconds
        val y = pose.yMeters + applied.speedMetersPerSecond * sin(pose.headingRadians) * dtSeconds
        return PlanarPose(x, y, heading)
    }
}
