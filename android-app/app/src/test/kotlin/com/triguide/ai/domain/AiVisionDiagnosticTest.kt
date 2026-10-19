package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVisionDiagnosticTest {

    @Test
    fun disabledReturnsHidden() {
        val risk = PathRiskAnalyzer.analyze(emptyList())
        val model = AiVisionDiagnostic.build(enabled = false, pathRisk = risk)
        assertFalse(model.enabled)
        assertTrue(model.boxes.isEmpty())
    }

    @Test
    fun emptyCuesStaysNeutralAndDoesNotShowWalkLane() {
        val risk = PathRiskAnalyzer.analyze(emptyList())
        val model = AiVisionDiagnostic.build(enabled = true, pathRisk = risk)
        assertEquals(PathRiskLevel.UNKNOWN, model.pathRisk)
        assertFalse(model.showOpenWalkLane)
        assertEquals(AiVisionTint.UNKNOWN_OFF_PATH, model.bandTint)
        assertEquals(OpenLaneCue.LANE_TOP_Y, model.laneTopY)
        assertFalse(model.caption.contains("path is clear", ignoreCase = true))
        assertTrue(model.caption.contains("not", ignoreCase = true))
    }

    @Test
    fun blockedCorridorChairIsRed() {
        val cue = DirectionalCue(
            label = "chair",
            confidence = 0.80f,
            centerXNormalized = 0.50f,
            areaFraction = 0.08f,
            centerYNormalized = 0.70f,
            boxLeftNormalized = 0.40f,
            boxRightNormalized = 0.60f,
            boxTopNormalized = 0.50f,
            boxBottomNormalized = 0.90f,
        )
        val model = AiVisionDiagnostic.fromFixtureCues(listOf(cue))
        assertEquals(PathRiskLevel.BLOCKED, model.pathRisk)
        assertEquals(AiVisionTint.BLOCKED, model.bandTint)
        assertFalse(model.showOpenWalkLane)
        assertEquals(1, model.boxes.size)
        assertEquals(AiVisionTint.BLOCKED, model.boxes.single().tint)
        assertTrue(model.boxes.single().inCorridor)
        assertEquals(PathRiskAnalyzer.CORRIDOR_LEFT, model.corridorLeft)
        assertEquals(PathRiskAnalyzer.CORRIDOR_RIGHT, model.corridorRight)
    }

    @Test
    fun weakCorridorCupIsYellowPossible() {
        val cue = DirectionalCue(
            label = "cup",
            confidence = 0.40f,
            centerXNormalized = 0.50f,
            areaFraction = 0.01f,
            centerYNormalized = 0.80f,
            boxLeftNormalized = 0.48f,
            boxRightNormalized = 0.52f,
            boxTopNormalized = 0.75f,
            boxBottomNormalized = 0.85f,
        )
        val model = AiVisionDiagnostic.fromFixtureCues(listOf(cue))
        assertEquals(PathRiskLevel.POSSIBLE_OBSTACLE, model.pathRisk)
        assertEquals(AiVisionTint.POSSIBLE, model.bandTint)
        assertFalse(model.showOpenWalkLane)
        assertEquals(AiVisionTint.POSSIBLE, model.boxes.single().tint)
    }

    @Test
    fun edgeChairOffCorridorIsUnknownGreenBox() {
        val cue = DirectionalCue(
            label = "chair",
            confidence = 0.85f,
            centerXNormalized = 0.08f,
            areaFraction = 0.06f,
            centerYNormalized = 0.70f,
            boxLeftNormalized = 0.00f,
            boxRightNormalized = 0.16f,
            boxTopNormalized = 0.50f,
            boxBottomNormalized = 0.90f,
        )
        val model = AiVisionDiagnostic.fromFixtureCues(listOf(cue))
        assertEquals(PathRiskLevel.UNKNOWN, model.pathRisk)
        assertFalse(model.showOpenWalkLane)
        assertEquals(AiVisionTint.UNKNOWN_OFF_PATH, model.bandTint)
        assertEquals(1, model.boxes.size)
        assertFalse(model.boxes.single().inCorridor)
        assertEquals(AiVisionTint.UNKNOWN_OFF_PATH, model.boxes.single().tint)
    }

    @Test
    fun clearObservedUsesBlueMarkerNeverUnknownGreen() {
        assertEquals(
            AiVisionTint.RESEARCH_CLEAR_MARKER,
            AiVisionDiagnostic.tintForPathRisk(PathRiskLevel.CLEAR_OBSERVED),
        )
        assertEquals(
            AiVisionTint.UNKNOWN_OFF_PATH,
            AiVisionDiagnostic.tintForPathRisk(PathRiskLevel.UNKNOWN),
        )
    }

    @Test
    fun legendAndCopyNeverClaimPathClear() {
        val banned = listOf("path is clear", "path clear", "في طريق فاضي", "طريق فاضي", "safe to walk")
        val haystack = (AiVisionModeCopy.LEGEND + AiVisionModeCopy.TOGGLE_DESCRIPTION +
            AiVisionModeCopy.SAFETY_CAPTION).lowercase()
        banned.forEach { phrase ->
            assertFalse("banned phrase in AI Vision copy: $phrase", haystack.contains(phrase.lowercase()))
        }
        assertTrue(AiVisionModeCopy.LEGEND.contains(OpenLaneCue.DIAGNOSTIC_LABEL))
    }

    @Test
    fun fusedClearMayShowLabeledOpenLane() {
        val risk = PathRiskResult(
            level = PathRiskLevel.CLEAR_OBSERVED,
            corridorCues = emptyList(),
            dominantInCorridor = null,
            diagnostic = "fixture",
        )
        val fused = FusionGateResult(
            verdict = FusionPathVerdict.CLEAR_OBSERVED,
            turnHint = FusionTurnHint.NONE,
            reasonCode = "test",
            agreeingDistinctFrames = EvidenceFusionGate.MIN_AGREEING_DISTINCT_FRAMES,
            diagnostic = "test",
        )
        val model = AiVisionDiagnostic.build(
            enabled = true,
            pathRisk = risk,
            fusedEvidence = fused,
        )
        assertTrue(model.showOpenWalkLane)
        assertEquals(AiVisionTint.OPEN_LANE, model.bandTint)
        assertTrue(model.caption.contains(OpenLaneCue.DIAGNOSTIC_LABEL))
    }
}
