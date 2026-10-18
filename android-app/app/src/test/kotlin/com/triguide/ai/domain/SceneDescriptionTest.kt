package com.triguide.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDescriptionTest {
    @Test
    fun anEmptyFrameIsNotAClearPathOrAFloorReading() {
        val line = SceneDescription.fromReported(emptyList())
        assertTrue(line.english.contains("not a clear path"))
        assertTrue(line.english.contains("not a floor reading"))
        assertFalse(line.english.contains("SAFE"))
        assertFalse(line.arabic.contains("SAFE"))
    }

    @Test
    fun reportedBoxesAreNamedAndNotCalledAClearance() {
        val line = SceneDescription.fromReported(listOf("person 0.81 left", "chair 0.42 right"))
        assertTrue(line.english.contains("person 0.81 left"))
        assertTrue(line.english.contains("chair 0.42 right"))
        assertTrue(line.english.contains("not a clearance"))
        assertTrue(line.english.contains("not a floor reading"))
        assertFalse(line.english.contains("SAFE"))
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
