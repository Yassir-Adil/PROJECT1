package com.triguide.ai.domain

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
    fun twoObjectsStaySilentUntilAThirdAppears() {
        val scheduler = SceneBriefScheduler()
        val two = listOf(
            DirectionalCue("person", 0.80f, 0.15f, 0.20f),
            DirectionalCue("bottle", 0.60f, 0.86f, 0.04f),
        )
        assertNull(scheduler.nextCue(two, nowElapsedMs = 1_050L, navigationSpokeThisTick = false))
    }

    @Test
    fun threeObjectsAreBriefedWhenNavigationIsQuiet() {
        val scheduler = SceneBriefScheduler()
        val cues = listOf(
            DirectionalCue("person", 0.80f, 0.15f, 0.20f),
            DirectionalCue("chair", 0.70f, 0.50f, 0.10f),
            DirectionalCue("bottle", 0.60f, 0.86f, 0.04f),
        )
        assertNull(
            scheduler.nextCue(cues, nowElapsedMs = 1_000L, navigationSpokeThisTick = true),
        )
        val cue = scheduler.nextCue(cues, nowElapsedMs = 1_050L, navigationSpokeThisTick = false)
        assertTrue(cue != null)
        assertTrue(cue!!.english.startsWith("Ahead:"))
        assertTrue(cue.kind == VoiceAlertKind.SCENE_BRIEF)
        assertNull(scheduler.nextCue(cues, nowElapsedMs = 2_000L, navigationSpokeThisTick = false))
    }
}
