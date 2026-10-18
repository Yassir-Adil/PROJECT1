package com.triguide.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDescriptionTest {
    @Test
    fun anEmptyFrameIsNotAClearPathOrAFloorReading() {
        val line = SceneDescription.fromCues(emptyList())
        assertTrue(line.english.contains("no objects"))
        assertTrue(line.english.contains("not a clear path"))
        assertTrue(line.english.contains("not a floor reading"))
        assertFalse(line.english.contains("SAFE"))
        assertFalse(line.arabic.contains("SAFE"))
        assertFalse(line.english.contains("matches this picture"))
    }

    @Test
    fun reportedBoxesAreCountedByZoneAndNotCalledAClearance() {
        val line = SceneDescription.fromCues(
            listOf(
                DirectionalCue("person", 0.81f, 0.15f, 0.20f),
                DirectionalCue("person", 0.70f, 0.20f, 0.10f),
                DirectionalCue("bottle", 0.52f, 0.86f, 0.04f),
            ),
        )
        assertTrue(line.english.contains("2 people on the left"))
        assertTrue(line.english.contains("bottle on the right"))
        assertTrue(line.english.contains("report of boxes"))
        assertTrue(line.english.contains("not a distance"))
        assertTrue(line.english.contains("not a clearance"))
        assertTrue(line.english.contains("not a floor reading"))
        assertTrue(line.arabic.contains("شخصان"))
        assertTrue(line.arabic.contains("اليسار"))
        assertFalse(line.english.contains("SAFE"))
    }

    @Test
    fun aMatchedNameIsAddedOnlyWhenTheCallerSuppliesOne() {
        val plain = SceneDescription.fromCues(listOf(DirectionalCue("person", 0.80f, 0.50f, 0.20f)))
        assertFalse(plain.english.contains("Ahmad"))
        val matched = SceneDescription.fromCues(
            listOf(DirectionalCue("person", 0.80f, 0.50f, 0.20f)),
            matchedName = "Ahmad",
        )
        assertTrue(matched.english.contains("consented name matches"))
        assertTrue(matched.english.contains("Ahmad"))
        assertTrue(matched.english.contains("not a distance"))
        assertFalse(matched.english.contains("SAFE"))
    }

    @Test
    fun waitingDoesNotInventObjects() {
        val line = SceneDescription.waiting()
        assertTrue(line.english.contains("has not reported"))
        assertFalse(line.english.contains("person"))
        assertFalse(line.english.contains("SAFE"))
        val spoken = SceneDescription.text(line, ResolvedVoiceLanguage.ENGLISH)
        assertTrue(spoken.contains("has not reported"))
    }
}
