package com.triguide.ai.domain

import com.triguide.ai.sim.PlanarPose
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fixed obstacle list for deterministic simulator runs.
 * Positions are authored in the plane. They are not measured by the camera.
 * +x is the robot's starting forward axis and +y is left.
 */
object SyntheticObstacleWorld {
    data class MapObstacle(
        val name: String,
        val xMeters: Double,
        val yMeters: Double,
    )

    val obstacles: List<MapObstacle> = listOf(
        MapObstacle(name = "left barrel", xMeters = 1.0, yMeters = 0.20),
        MapObstacle(name = "right crate", xMeters = 2.6, yMeters = -0.40),
    )

    fun observe(pose: PlanarPose): SceneObservation {
        val relative = obstacles.map { obstacle -> toRobotFrame(pose, obstacle) }
        val ahead = relative.filter { it.forwardMeters in NavigationConfig.MIN_FORWARD_METERS..NavigationConfig.LOOKAHEAD_METERS }
        return SceneObservation(
            cues = emptyList(),
            mode = PerceptionMode.SYNTHETIC_WORLD,
            realDetector = false,
            metricDepthAvailable = true,
            frameAgeMs = 0L,
            knownObstacles = ahead,
            catalogComplete = true,
            summary = "Authored synthetic obstacle map.",
        )
    }

    fun toRobotFrame(pose: PlanarPose, obstacle: MapObstacle): KnownObstacle {
        val dx = obstacle.xMeters - pose.xMeters
        val dy = obstacle.yMeters - pose.yMeters
        val heading = pose.headingRadians
        val forward = dx * cos(heading) + dy * sin(heading)
        val lateral = -dx * sin(heading) + dy * cos(heading)
        return KnownObstacle(
            name = obstacle.name,
            forwardMeters = forward,
            lateralMeters = lateral,
        )
    }
}
