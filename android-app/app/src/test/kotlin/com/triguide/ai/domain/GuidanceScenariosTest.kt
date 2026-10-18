package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceScenariosTest {
    @Test
    fun scenariosUseTheNavigationEngineOnSyntheticObservations() {
        val runs = GuidanceScenarios.run()
        assertEquals(
            listOf("straight-corridor", "obstacle-left", "blocked-path"),
            runs.map { it.scenario.id },
        )
        runs.forEach { run ->
            assertFalse(run.decision.basedOnRealVision)
            assertTrue(run.scenario.setup.contains("Not the camera"))
            assertEquals(PerceptionMode.SYNTHETIC_WORLD, run.scenario.observation.mode)
            assertTrue(run.scenario.observation.catalogComplete)
        }
        assertEquals(GuidanceAction.STRAIGHT, runs[0].decision.action)
        assertTrue(runs[0].decision.reason.contains("Synthetic map"))
        assertEquals(GuidanceAction.RIGHT, runs[1].decision.action)
        assertTrue(runs[1].decision.reason.contains("left barrel"))
        assertEquals(GuidanceAction.HOLD, runs[2].decision.action)
        assertTrue(runs[2].decision.reason.contains("forward axis"))
        assertEquals(0.0, runs[2].decision.linearSpeedMps, 0.0)
    }
}
