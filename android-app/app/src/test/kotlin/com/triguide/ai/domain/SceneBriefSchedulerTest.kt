package com.triguide.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneBriefSchedulerTest {
    @Test
    fun aSingleObjectDoesNotAddASecondSentence() {
        val scheduler = SceneBriefScheduler()
        val cue = scheduler.nextCue(
            cues = listOf(DirectionalCue("person", 0.80f, 0.15f, 0.20f)),
            nowElapsedMs = 1_000L,
            navigationSpokeThisTick = false,
        )
        assertNull(cue)
    }

    @Test
    fun severalObjectsAreBriefedWhenNavigationIsQuiet() {
        val scheduler = SceneBriefScheduler()
        val cues = listOf(
            DirectionalCue("person", 0.80f, 0.15f, 0.20f),
            DirectionalCue("bottle", 0.60f, 0.86f, 0.04f),
        )
        assertNull(
            scheduler.nextCue(cues, nowElapsedMs = 1_000L, navigationSpokeThisTick = true),
        )
        val cue = scheduler.nextCue(cues, nowElapsedMs = 1_050L, navigationSpokeThisTick = false)
        assertTrue(cue != null)
        assertTrue(cue!!.english.contains("Reported:"))
        assertTrue(cue.english.contains("on the left"))
        assertFalse(cue.english.contains("right"))
        assertFalse(cue.english.contains("center"))
        assertTrue(cue.english.contains("not a clear path"))
        assertTrue(cue.kind == VoiceAlertKind.SCENE_BRIEF)
        assertNull(scheduler.nextCue(cues, nowElapsedMs = 2_000L, navigationSpokeThisTick = false))
    }
}
