package com.triguide.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraActiveCueTest {

    @Test
    fun playsOnceWhenStreamReadyAndGuidanceRunning() {
        assertTrue(
            CameraActiveCue.shouldPlayStartupTone(
                cameraStreaming = true,
                guidanceRunning = true,
                alreadyPlayedForThisBind = false,
                emergencyActive = false,
            ),
        )
    }

    @Test
    fun stopsRepeatingAndYieldsToEmergency() {
        assertFalse(
            CameraActiveCue.shouldPlayStartupTone(
                cameraStreaming = true,
                guidanceRunning = true,
                alreadyPlayedForThisBind = true,
                emergencyActive = false,
            ),
        )
        assertFalse(
            CameraActiveCue.shouldPlayStartupTone(
                cameraStreaming = true,
                guidanceRunning = true,
                alreadyPlayedForThisBind = false,
                emergencyActive = true,
            ),
        )
        assertFalse(
            CameraActiveCue.shouldPlayStartupTone(
                cameraStreaming = false,
                guidanceRunning = true,
                alreadyPlayedForThisBind = false,
                emergencyActive = false,
            ),
        )
        assertFalse(
            CameraActiveCue.shouldPlayStartupTone(
                cameraStreaming = true,
                guidanceRunning = false,
                alreadyPlayedForThisBind = false,
                emergencyActive = false,
            ),
        )
    }

    @Test
    fun everyStoppedOrNotRunningStateSuppressesTheNeutralStartupTone() {
        val states = listOf(
            Triple(true, false, false),
            Triple(true, true, true),
            Triple(false, true, false),
            Triple(false, false, true),
        )

        states.forEach { (streaming, running, emergency) ->
            assertFalse(
                "neutral startup tone must be suppressed for streaming=$streaming running=$running emergency=$emergency",
                CameraActiveCue.shouldPlayStartupTone(
                    cameraStreaming = streaming,
                    guidanceRunning = running,
                    alreadyPlayedForThisBind = false,
                    emergencyActive = emergency,
                ),
            )
        }
    }
}
