package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDescriptionTest {
    @Test
    fun emptyFrameDoesNotClaimClearanceOrSafety() {
        val line = SceneDescription.fromCues(emptyList())
        assertEquals("Nothing obvious ahead.", line.english)
        assertEquals("ما في جسم ظاهر قدامك.", line.arabic)
        assertFalse(line.english.contains("SAFE", ignoreCase = true))
        assertFalse(line.english.contains("clear path", ignoreCase = true))
        assertFalse(line.english.contains("آمن"))
        assertFalse(line.arabic.contains("فاضي"))
        assertFalse(line.arabic.contains("آمن"))
    }

    @Test
    fun onePersonInTheCenterIsShortArabicFriendlyEnglish() {
        val line = SceneDescription.fromCues(
            listOf(DirectionalCue("person", 0.86f, 0.50f, 0.20f)),
        )
        assertEquals("Ahead: a person in the center.", line.english)
        assertEquals("قدامك شخص في الوسط.", line.arabic)
        assertFalse(line.english.contains("clearance", ignoreCase = true))
        assertFalse(line.english.contains("SAFE", ignoreCase = true))
    }

    @Test
    fun twoObjectsOnDifferentSidesJoinNaturally() {
        val line = SceneDescription.fromCues(
            listOf(
                DirectionalCue("person", 0.86f, 0.50f, 0.20f),
                DirectionalCue("chair", 0.70f, 0.86f, 0.12f),
            ),
        )
        assertTrue(line.english.contains("a person in the center"))
        assertTrue(line.english.contains("chair on the right") || line.english.contains("a chair on the right"))
        assertTrue(line.english.contains(" and "))
        assertTrue(line.arabic.contains("شخص في الوسط"))
        assertTrue(line.arabic.contains("كرسي على اليمين"))
        assertTrue(line.arabic.contains(" و"))
        assertFalse(line.english.contains("SAFE", ignoreCase = true))
        assertFalse(line.arabic.contains("فاضي"))
    }

    @Test
    fun weakOrUnknownLabelFallsBackToSomething() {
        val weakKnown = SceneDescription.fromCues(
            listOf(DirectionalCue("chair", 0.40f, 0.50f, 0.10f)),
        )
        assertTrue(weakKnown.english.contains("something") || weakKnown.english.contains("what looks like"))
        assertTrue(weakKnown.arabic.contains("جسم") || weakKnown.arabic.contains("يبدو"))
        assertFalse(weakKnown.english.contains("Chair"))

        val unknown = SceneDescription.fromCues(
            listOf(DirectionalCue("frisbee", 0.80f, 0.50f, 0.10f)),
        )
        assertTrue(unknown.english.contains("something") || unknown.english.contains("what looks like"))
        assertTrue(unknown.arabic.contains("جسم") || unknown.arabic.contains("يبدو"))
        assertFalse(unknown.english.contains("Frisbee"))
    }

    @Test
    fun highConfidenceToiletIsNeverNamedInDescribe() {
        val line = SceneDescription.fromCues(
            listOf(DirectionalCue("toilet", 0.87f, 0.50f, 0.15f)),
        )
        assertTrue(line.english.contains("something") || line.english.contains("what looks like"))
        assertFalse(line.english.contains("toilet", ignoreCase = true))
        assertFalse(line.arabic.contains("مرحاض"))
        assertFalse(line.english.contains("SAFE", ignoreCase = true))
    }

    @Test
    fun weakPersonScoreIsHedgedNotNamedConfidently() {
        val line = SceneDescription.fromCues(
            listOf(DirectionalCue("person", 0.48f, 0.50f, 0.12f)),
        )
        assertTrue(line.english.contains("what looks like a person"))
        assertTrue(line.arabic.contains("يبدو شخص"))
        assertFalse(line.english.startsWith("Ahead: a person"))
        assertFalse(line.english.contains("SAFE", ignoreCase = true))
    }

    @Test
    fun midConfidencePersonMislabelIsHedged() {
        val line = SceneDescription.fromCues(
            listOf(DirectionalCue("person", 0.70f, 0.50f, 0.18f)),
        )
        assertTrue(line.english.contains("what looks like a person"))
        assertFalse(line.english.contains("Ahead: a person in the center."))
    }

    @Test
    fun noOutputClaimsClearanceOrSafety() {
        val samples = listOf(
            SceneDescription.fromCues(emptyList()),
            SceneDescription.fromCues(listOf(DirectionalCue("person", 0.9f, 0.5f, 0.2f))),
            SceneDescription.fromCues(
                listOf(
                    DirectionalCue("person", 0.8f, 0.2f, 0.2f),
                    DirectionalCue("chair", 0.7f, 0.8f, 0.1f),
                ),
            ),
            SceneDescription.fromCues(listOf(DirectionalCue("toaster", 0.9f, 0.5f, 0.1f))),
            SceneDescription.waiting(),
            SceneDescription.briefFromCues(listOf(DirectionalCue("bottle", 0.6f, 0.5f, 0.05f))),
        )
        samples.forEach { line ->
            assertFalse(line.english.contains("SAFE", ignoreCase = true))
            assertFalse(line.english.contains("safe path", ignoreCase = true))
            assertFalse(line.english.contains("clear path", ignoreCase = true))
            assertFalse(line.english.contains("clearance", ignoreCase = true))
            assertFalse(line.arabic.contains("آمن"))
            assertFalse(line.arabic.contains("فاضي"))
            assertFalse(line.arabic.contains("SAFE", ignoreCase = true))
        }
    }

    @Test
    fun matchedNameIsOptionalAndStillNotADistanceClaim() {
        val plain = SceneDescription.fromCues(listOf(DirectionalCue("person", 0.86f, 0.50f, 0.20f)))
        assertFalse(plain.english.contains("Ahmad"))
        val matched = SceneDescription.fromCues(
            listOf(DirectionalCue("person", 0.86f, 0.50f, 0.20f)),
            matchedName = "Ahmad",
        )
        assertTrue(matched.english.contains("Saved name: Ahmad"))
        assertTrue(matched.arabic.contains("اسم محفوظ: Ahmad"))
        assertFalse(matched.english.contains("SAFE", ignoreCase = true))
    }

    @Test
    fun waitingDoesNotInventObjects() {
        val line = SceneDescription.waiting()
        assertTrue(line.english.contains("No picture yet"))
        assertFalse(line.english.contains("person"))
        assertFalse(line.english.contains("SAFE", ignoreCase = true))
    }
}
