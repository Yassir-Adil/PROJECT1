package com.triguide.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveNavStateTest {
    @Test
    fun restoredToolOrLiveScreenAlwaysRequiresExplicitRestart() {
        for (screen in Screen.entries) {
            val nav = LiveNavState.restored(screen.name, 7)
            assertEquals(screen, nav.screen)
            assertFalse(nav.running)
            assertEquals(7, nav.epoch)
            nav.returnToLive()
            assertFalse(nav.running)
            nav.start(fromGesture = true)
            assertTrue(nav.running)
            assertEquals(8, nav.epoch)
        }
    }

    @Test
    fun malformedRestoreFailsClosed() {
        val nav = LiveNavState.restored("not-a-screen", -1)
        assertEquals(Screen.LiveVision, nav.screen)
        assertFalse(nav.running)
        assertEquals(0, nav.epoch)
    }
    @Test
    fun returningFromMoreAfterEmergencyStopDoesNotRestartGuidance() {
        val nav = LiveNavState()
        nav.pause()
        nav.showMore()
        val epochBeforeReturn = nav.epoch

        nav.returnToLive()

        assertEquals(Screen.LiveVision, nav.screen)
        assertFalse(nav.running)
        assertEquals(epochBeforeReturn, nav.epoch)
    }

    @Test
    fun returningFromMoreAfterActiveSessionRequiresExplicitRestart() {
        val nav = LiveNavState()
        nav.showMore()

        nav.returnToLive()

        assertEquals(Screen.LiveVision, nav.screen)
        assertFalse(nav.running)
        assertEquals(0, nav.epoch)
        nav.start(fromGesture = true)
        assertTrue(nav.running)
        assertEquals(1, nav.epoch)
    }

    @Test
    fun returningFromOtherToolsNeverStartsCamera() {
        for (tool in listOf(Screen.Simulator, Screen.Scenarios, Screen.Diagnostics, Screen.Settings)) {
            val nav = LiveNavState()
            nav.pause()
            nav.screen = tool

            nav.returnToLive()

            assertEquals(Screen.LiveVision, nav.screen)
            assertFalse(nav.running)
            assertEquals(0, nav.epoch)
        }
    }
}
