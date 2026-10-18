package com.triguide.ai.domain

/**
 * Named simulator checks. Each one is an authored synthetic observation.
 * None of them is a camera frame, a floor mask, or an indoor evaluation.
 */
object GuidanceScenarios {
    data class Scenario(
        val id: String,
        val title: String,
        val setup: String,
        val observation: SceneObservation,
    )

    data class Run(
        val scenario: Scenario,
        val decision: NavigationDecision,
    )

    fun catalog(): List<Scenario> = listOf(
        Scenario(
            id = "straight-corridor",
            title = "Straight corridor",
            setup = "Synthetic map with no authored obstacle in the forward corridor. Not the camera.",
            observation = synthetic(emptyList()),
        ),
        Scenario(
            id = "obstacle-left",
            title = "Obstacle on the left",
            setup = "Synthetic map. One authored obstacle is on the left of the virtual robot. Not the camera.",
            observation = synthetic(listOf(KnownObstacle(name = "left barrel", forwardMeters = 1.0, lateralMeters = 0.25))),
        ),
        Scenario(
            id = "blocked-path",
            title = "Blocked path",
            setup = "Synthetic map. One authored obstacle is on the forward axis. Not the camera.",
            observation = synthetic(listOf(KnownObstacle(name = "post", forwardMeters = 1.0, lateralMeters = 0.0))),
        ),
    )

    fun run(engine: NavigationDecisionEngine = NavigationDecisionEngine()): List<Run> {
        return catalog().map { scenario -> Run(scenario, engine.decide(scenario.observation)) }
    }

    private fun synthetic(obstacles: List<KnownObstacle>): SceneObservation {
        return SceneObservation(
            cues = emptyList(),
            mode = PerceptionMode.SYNTHETIC_WORLD,
            realDetector = false,
            metricDepthAvailable = true,
            frameAgeMs = 0L,
            knownObstacles = obstacles,
            catalogComplete = true,
            summary = "Authored scenario. Not a camera frame.",
        )
    }
}
