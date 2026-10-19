package com.triguide.ai.diagnostics

import com.triguide.ai.domain.*
import org.junit.Assert.*
import org.junit.Test

class DiagnosticObjectPresentationTest {
    private val chair = DetectedObject("chair", 0.8f, 10f, 10f, 40f, 60f)
    private fun frame(objects: List<DetectedObject> = listOf(chair)) =
        DetectionFrame(objects, 100, 100, false, 20.0, true, 100L)
    private fun show(frame: DetectionFrame? = frame(), now: Long = 100, running: Boolean = true,
        ready: DetectorReadiness = DetectorReadiness.READY) =
        DiagnosticObjectPresentation.freshFrame(frame, ready, now, running)

    @Test fun freshRealFrameUsesExistingBoxCoordinatesAndLabels() { assertEquals(frame(), show()) }
    @Test fun ageBoundaryIsInclusiveAndOldBoxesDisappear() {
        assertNotNull(show(now = 1100)); assertNull(show(now = 1101))
    }
    @Test fun stopAndDetectorFailureHideAllBoxes() {
        assertNull(show(running = false)); assertNull(show(ready = DetectorReadiness.LOAD_FAILED))
    }
    @Test fun missingOrUntrustedTimestampCannotBeDrawnOrDescribed() {
        assertNull(show(frame().copy(completedAtElapsedMs = null)))
        assertNull(show(frame().copy(completedAtElapsedMs = -1)))
        assertNull(show(now = 99)); assertNull(show(frame().copy(fromRealDetector = false)))
    }
    @Test fun malformedDimensionsAndCoordinatesCannotBecomeNames() {
        assertNull(show(frame().copy(imageWidth = 0)))
        val visible = show(frame(listOf(chair.copy(left = Float.NaN), chair.copy(right = 101f), chair.copy(confidence = 0.2f))))!!
        assertTrue(visible.objects.isEmpty())
    }
    @Test fun spokenDescriptionReusesConservativeExistingNarrator() {
        val weak = frame(listOf(chair.copy(confidence = 0.4f)))
        val line = DiagnosticObjectPresentation.description(weak, DetectorReadiness.READY, 100, true)!!
        assertTrue(line.english.contains("something") || line.english.contains("looks like"))
        assertFalse(line.english.contains("a chair"))
    }
    @Test fun stoppedOrStaleDescriptionIsUnavailable() {
        assertNull(DiagnosticObjectPresentation.description(frame(), DetectorReadiness.READY, 100, false))
        assertNull(DiagnosticObjectPresentation.description(frame(), DetectorReadiness.READY, 1101, true))
    }
    @Test fun emptyFrameNeverClaimsSafetyOrAnAllowedTurn() {
        val line = DiagnosticObjectPresentation.description(frame(emptyList()), DetectorReadiness.READY, 100, true)!!
        for (word in listOf("safe", "clear path", "turn", "آمن", "فاضي", "انعطف")) {
            assertFalse(line.english.contains(word, true)); assertFalse(line.arabic.contains(word, true))
        }
    }
    @Test fun descriptionRespectsDisabledSpeechQuietModeAndStop() {
        assertTrue(DiagnosticObjectPresentation.maySpeak(VoiceSettings(), true))
        assertFalse(DiagnosticObjectPresentation.maySpeak(VoiceSettings(speechEnabled = false), true))
        assertFalse(DiagnosticObjectPresentation.maySpeak(VoiceSettings(quietMode = true), true))
        assertFalse(DiagnosticObjectPresentation.maySpeak(VoiceSettings(), false))
    }
}
