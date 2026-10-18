package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathRiskAnalyzerTest {
    @Test
    fun emptyCorridorIsClear() {
        val result = PathRiskAnalyzer.analyze(emptyList())
        assertEquals(PathRiskLevel.CLEAR_OBSERVED, result.level)
        assertNull(result.dominantInCorridor)
    }

    @Test
    fun personFarLeftIsOffCorridor() {
        val result = PathRiskAnalyzer.analyze(
            listOf(DirectionalCue("person", 0.90f, 0.05f, 0.20f, centerYNormalized = 0.55f)),
        )
        assertEquals(PathRiskLevel.CLEAR_OBSERVED, result.level)
        assertTrue(result.diagnostic.contains("off_corridor") || result.diagnostic.contains("clear"))
    }

    @Test
    fun personInCorridorCenterIsBlocked() {
        val result = PathRiskAnalyzer.analyze(
            listOf(DirectionalCue("person", 0.90f, 0.50f, 0.20f, centerYNormalized = 0.55f)),
        )
        assertEquals(PathRiskLevel.BLOCKED, result.level)
        assertEquals("person", result.dominantInCorridor?.label)
    }

    @Test
    fun smallFloorCupIsIgnored() {
        val result = PathRiskAnalyzer.analyze(
            listOf(DirectionalCue("cup", 0.80f, 0.50f, 0.02f, centerYNormalized = 0.90f)),
        )
        assertEquals(PathRiskLevel.CLEAR_OBSERVED, result.level)
    }

    @Test
    fun chairInCorridorIsBlocked() {
        val result = PathRiskAnalyzer.analyze(
            listOf(DirectionalCue("chair", 0.80f, 0.50f, 0.12f, centerYNormalized = 0.60f)),
        )
        assertEquals(PathRiskLevel.BLOCKED, result.level)
    }
}
