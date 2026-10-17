package com.triguide.ai.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.tan

class TricycleKinematicsTest {
    private val origin = PlanarPose(0.0, 0.0, 0.0)
    private val wheelbase = 0.5

    @Test
    fun straightLineMovesAlongXOnly() {
        val next = TricycleKinematics.step(
            pose = origin,
            command = DriveCommand(speedMetersPerSecond = 1.0, steeringRadians = 0.0),
            wheelbaseMeters = wheelbase,
            dtSeconds = 1.0,
        )
        assertEquals(1.0, next.xMeters, 1e-9)
        assertEquals(0.0, next.yMeters, 1e-9)
        assertEquals(0.0, next.headingRadians, 1e-9)
    }

    @Test
    fun zeroSpeedDoesNotMoveEvenIfSteeringIsSet() {
        val next = TricycleKinematics.step(
            pose = origin,
            command = DriveCommand(speedMetersPerSecond = 0.0, steeringRadians = 0.4),
            wheelbaseMeters = wheelbase,
            dtSeconds = 2.0,
        )
        assertEquals(origin, next)
    }

    @Test
    fun zeroDtReturnsTheSamePose() {
        val pose = PlanarPose(1.5, -0.25, 0.3)
        val next = TricycleKinematics.step(
            pose = pose,
            command = DriveCommand(1.0, 0.2),
            wheelbaseMeters = wheelbase,
            dtSeconds = 0.0,
        )
        assertEquals(pose, next)
    }

    @Test
    fun positiveSteerWhileMovingForwardIncreasesHeading() {
        val dt = 0.1
        val speed = 1.0
        val steer = 0.2
        val next = TricycleKinematics.step(
            pose = origin,
            command = DriveCommand(speed, steer),
            wheelbaseMeters = wheelbase,
            dtSeconds = dt,
        )
        val expectedHeading = speed * tan(steer) / wheelbase * dt
        assertEquals(speed * dt, next.xMeters, 1e-9)
        assertEquals(0.0, next.yMeters, 1e-9)
        assertEquals(expectedHeading, next.headingRadians, 1e-9)
    }

    @Test
    fun reversingWithPositiveSteerDecreasesHeading() {
        val next = TricycleKinematics.step(
            pose = origin,
            command = DriveCommand(speedMetersPerSecond = -1.0, steeringRadians = 0.2),
            wheelbaseMeters = wheelbase,
            dtSeconds = 0.1,
        )
        assertEquals(-0.1, next.xMeters, 1e-9)
        org.junit.Assert.assertTrue(next.headingRadians < 0.0)
    }

    @Test
    fun speedAndSteerAreClamped() {
        val clamped = TricycleKinematics.clampCommand(
            DriveCommand(speedMetersPerSecond = 9.0, steeringRadians = 3.0),
        )
        assertEquals(TricycleKinematics.MAX_SPEED_METERS_PER_SECOND, clamped.speedMetersPerSecond, 0.0)
        assertEquals(TricycleKinematics.MAX_STEERING_RADIANS, clamped.steeringRadians, 1e-12)

        val next = TricycleKinematics.step(
            pose = origin,
            command = DriveCommand(9.0, 3.0),
            wheelbaseMeters = wheelbase,
            dtSeconds = 1.0,
        )
        val expected = TricycleKinematics.step(
            pose = origin,
            command = clamped,
            wheelbaseMeters = wheelbase,
            dtSeconds = 1.0,
        )
        assertEquals(expected, next)
    }

    @Test
    fun stepDoesNotMutateInputPose() {
        val pose = PlanarPose(2.0, 3.0, 0.5)
        TricycleKinematics.step(pose, DriveCommand(1.0, 0.1), wheelbase, 0.2)
        assertEquals(2.0, pose.xMeters, 0.0)
        assertEquals(3.0, pose.yMeters, 0.0)
        assertEquals(0.5, pose.headingRadians, 0.0)
    }

    @Test
    fun nonPositiveWheelbaseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TricycleKinematics.step(origin, DriveCommand(1.0, 0.0), wheelbaseMeters = 0.0, dtSeconds = 0.1)
        }
    }

    @Test
    fun negativeDtIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TricycleKinematics.step(origin, DriveCommand(1.0, 0.0), wheelbase, dtSeconds = -0.01)
        }
    }
}
