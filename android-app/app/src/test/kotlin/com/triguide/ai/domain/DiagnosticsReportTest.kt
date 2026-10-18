package com.triguide.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {
    @Test
    fun reportIncludesStatusAndDoesNotClaimAFloor() {
        val text = DiagnosticsReport.text(
            DiagnosticsSnapshot(
                versionName = "0.6.0",
                cameraPermission = "granted",
                detection = "READY",
                decision = "Hold",
                reason = "No confirmed passage.",
                confidence = "0.55",
                wheelDegrees = "0.0",
                source = "Camera 2D",
                floor = "NOT_STARTED. Disabled. No floor model is loaded.",
                voice = "Speech on.",
                lastCue = "none",
                performance = "Preview 10 FPS.",
                hardware = "No physical robot is connected.",
                shortcut = "Triple-tap the screen to restart guidance.",
            ),
        )
        assertTrue(text.startsWith("TriGuide diagnostics"))
        assertTrue(text.contains("Version: 0.6.0"))
        assertTrue(text.contains("Decision: Hold"))
        assertTrue(text.contains("Reason: No confirmed passage."))
        assertTrue(text.contains("Front wheel degrees: 0.0"))
        assertTrue(text.contains("Floor segmentation: NOT_STARTED"))
        assertTrue(text.contains("Triple-tap the screen"))
        assertTrue(text.contains("not a safety proof"))
        assertFalse(text.contains("floor mask is clear"))
    }
}
