package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathRiskAnalyzerTest {
    @Test
    fun noDetectionsAreUnknownRatherThanClear() {
        val result = PathRiskAnalyzer.analyze(emptyList())
        assertEquals(PathRiskLevel.UNKNOWN, result.level)
        assertNull(result.dominantInCorridor)
        assertTrue(result.diagnostic.contains("no_usable_detections"))
    }

    @Test
    fun personFarLeftIsOffCorridor() {
        val result = PathRiskAnalyzer.analyze(
            listOf(DirectionalCue("person", 0.90f, 0.05f, 0.20f, centerYNormalized = 0.55f)),
        )
        assertEquals(PathRiskLevel.UNKNOWN, result.level)
        assertTrue(result.diagnostic.contains("off_corridor"))
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
    fun smallFloorCupRemainsPossibleObstacle() {
        val result = PathRiskAnalyzer.analyze(
            listOf(DirectionalCue("cup", 0.80f, 0.50f, 0.02f, centerYNormalized = 0.90f)),
        )
        assertEquals(PathRiskLevel.POSSIBLE_OBSTACLE, result.level)
        assertEquals("cup", result.dominantInCorridor?.label)
    }

    @Test
    fun smallLowerCorridorKnifeRemainsPossibleObstacle() {
        val result = PathRiskAnalyzer.analyze(
            listOf(DirectionalCue("knife", 0.75f, 0.52f, 0.015f, centerYNormalized = 0.88f)),
        )
        assertEquals(PathRiskLevel.POSSIBLE_OBSTACLE, result.level)
        assertEquals("knife", result.dominantInCorridor?.label)
    }

    @Test
    fun ignoredLabelOutsideCorridorIsStillIrrelevant() {
        val result = PathRiskAnalyzer.analyze(
            listOf(DirectionalCue("cup", 0.80f, 0.04f, 0.02f, centerYNormalized = 0.90f)),
        )
        assertEquals(PathRiskLevel.UNKNOWN, result.level)
        assertNull(result.dominantInCorridor)
        assertTrue(result.diagnostic.contains("only_ignored_or_clutter"))
    }

    @Test
    fun chairInCorridorIsBlocked() {
        val result = PathRiskAnalyzer.analyze(
            listOf(DirectionalCue("chair", 0.80f, 0.50f, 0.12f, centerYNormalized = 0.60f)),
        )
        assertEquals(PathRiskLevel.BLOCKED, result.level)
    }

    @Test
    fun chairOnlyOnFarLeftEdgeIsOffCorridorUnknown() {
        val chair = DirectionalCue(
            "chair", 0.90f, 0.08f, 0.096f, centerYNormalized = 0.55f,
            boxLeftNormalized = 0.00f, boxRightNormalized = 0.16f,
            boxTopNormalized = 0.25f, boxBottomNormalized = 0.85f,
        )
        val result = PathRiskAnalyzer.analyze(listOf(chair))
        assertFalse(PathRiskAnalyzer.inCorridor(chair))
        assertEquals(PathRiskLevel.UNKNOWN, result.level)
    }

    @Test
    fun chairBoxOverlappingCorridorFromLeftStillBlocks() {
        val chair = DirectionalCue(
            "chair", 0.90f, 0.20f, 0.12f, centerYNormalized = 0.55f,
            boxLeftNormalized = 0.10f, boxRightNormalized = 0.40f,
            boxTopNormalized = 0.25f, boxBottomNormalized = 0.85f,
        )
        val result = PathRiskAnalyzer.analyze(listOf(chair))
        assertTrue(PathRiskAnalyzer.inCorridor(chair))
        assertEquals(PathRiskLevel.BLOCKED, result.level)
    }

    @Test
    fun ignoredKnifeAndCupWhollyOutsideCorridorAreIrrelevant() {
        val knife = DirectionalCue(
            "knife", 0.75f, 0.08f, 0.016f, centerYNormalized = 0.80f,
            boxLeftNormalized = 0.00f, boxRightNormalized = 0.16f,
            boxTopNormalized = 0.75f, boxBottomNormalized = 0.85f,
        )
        val cup = DirectionalCue(
            "cup", 0.80f, 0.92f, 0.016f, centerYNormalized = 0.80f,
            boxLeftNormalized = 0.84f, boxRightNormalized = 1.00f,
            boxTopNormalized = 0.75f, boxBottomNormalized = 0.85f,
        )
        listOf(knife, cup).forEach { cue ->
            val result = PathRiskAnalyzer.analyze(listOf(cue))
            assertEquals(PathRiskLevel.UNKNOWN, result.level)
            assertFalse(PathRiskAnalyzer.inCorridor(cue))
        }
    }

    @Test
    fun tripHazardCupStillPossibleWhenOverlappingNarrowCorridor() {
        val cup = DirectionalCue(
            "cup", 0.80f, 0.30f, 0.02f, centerYNormalized = 0.90f,
            boxLeftNormalized = 0.22f, boxRightNormalized = 0.38f,
            boxTopNormalized = 0.85f, boxBottomNormalized = 0.95f,
        )
        val result = PathRiskAnalyzer.analyze(listOf(cup))
        assertEquals(PathRiskLevel.POSSIBLE_OBSTACLE, result.level)
    }

    @Test
    fun boxWhollyOutsideOrAboveRiskBandRemainsUnknown() {
        val outside = DirectionalCue(
            "chair", 0.90f, 0.04f, 0.032f, centerYNormalized = 0.70f,
            boxLeftNormalized = 0.00f, boxRightNormalized = 0.08f,
            boxTopNormalized = 0.50f, boxBottomNormalized = 0.90f,
        )
        val above = DirectionalCue(
            "chair", 0.90f, 0.08f, 0.032f, centerYNormalized = 0.10f,
            boxLeftNormalized = 0.00f, boxRightNormalized = 0.16f,
            boxTopNormalized = 0.00f, boxBottomNormalized = 0.20f,
        )
        listOf(outside, above).forEach { cue ->
            assertEquals(PathRiskLevel.UNKNOWN, PathRiskAnalyzer.analyze(listOf(cue)).level)
        }
    }

    @Test
    fun incompleteNonFiniteReversedOrOutOfRangeBoundsFailClosed() {
        val valid = DirectionalCue(
            "chair", 0.90f, 0.50f, 0.12f, centerYNormalized = 0.60f,
            boxLeftNormalized = 0.20f, boxRightNormalized = 0.80f,
            boxTopNormalized = 0.40f, boxBottomNormalized = 0.80f,
        )
        listOf(
            valid.copy(boxBottomNormalized = null),
            valid.copy(boxLeftNormalized = 0.90f),
            valid.copy(boxTopNormalized = Float.NaN),
            valid.copy(boxRightNormalized = 1.20f),
            valid.copy(centerXNormalized = 0.95f),
        ).forEach { cue ->
            assertFalse(PathRiskAnalyzer.inCorridor(cue))
            assertEquals(PathRiskLevel.UNKNOWN, PathRiskAnalyzer.analyze(listOf(cue)).level)
        }
    }
}
