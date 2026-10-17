package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PerformanceMonitorTest {
    @Test
    fun inferenceSamplesDoNotChangeThePreviewFrameCount() {
        var now = 0L
        val monitor = PerformanceMonitor(clockNs = { now }, latencyWindowSize = 3)
        monitor.onFrame(1_000L)
        monitor.onInferenceCompleted(10.0)
        monitor.onInferenceCompleted(20.0)
        monitor.onInferenceCompleted(30.0)

        assertEquals(1L, monitor.framesSeen.value)
        assertEquals(3L, monitor.inferencesCompleted.value)
        assertEquals(30.0, monitor.lastInferenceLatencyMs.value!!, 0.001)
        assertEquals(20.0, monitor.averageInferenceLatencyMs.value!!, 0.001)
        assertNull(monitor.inferenceFramesPerSecond.value)
    }

    @Test
    fun inferenceFpsUsesCompletedResultsOverAOneSecondWindow() {
        var now = 0L
        val monitor = PerformanceMonitor(clockNs = { now }, latencyWindowSize = 3)
        monitor.onInferenceCompleted(10.0)
        monitor.onInferenceCompleted(20.0)
        monitor.onInferenceCompleted(30.0)
        now = 1_000_000_000L
        monitor.onInferenceCompleted(40.0)

        assertEquals(4.0, monitor.inferenceFramesPerSecond.value!!, 0.001)
        assertEquals(30.0, monitor.averageInferenceLatencyMs.value!!, 0.001)
        assertEquals(0.0, monitor.framesPerSecond.value, 0.001)
    }

    @Test
    fun negativeOrNonFiniteLatencyIsRejected() {
        val monitor = PerformanceMonitor()
        assertThrows(IllegalArgumentException::class.java) {
            monitor.onInferenceCompleted(-1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            monitor.onInferenceCompleted(Double.NaN)
        }
        assertEquals(0L, monitor.inferencesCompleted.value)
        assertNull(monitor.lastInferenceLatencyMs.value)
    }

    @Test
    fun resetClearsInferenceMeasurements() {
        var now = 0L
        val monitor = PerformanceMonitor(clockNs = { now })
        now = 1_000_000_000L
        monitor.onFrame(5L)
        monitor.onInferenceCompleted(15.0)
        monitor.reset()

        assertEquals(0L, monitor.framesSeen.value)
        assertEquals(0L, monitor.inferencesCompleted.value)
        assertNull(monitor.lastInferenceLatencyMs.value)
        assertNull(monitor.averageInferenceLatencyMs.value)
        assertNull(monitor.inferenceFramesPerSecond.value)
    }
}
