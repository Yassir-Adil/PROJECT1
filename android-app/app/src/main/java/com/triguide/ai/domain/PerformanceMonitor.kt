package com.triguide.ai.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Counts real CameraX analysis frames. It does not infer objects.
 */
class PerformanceMonitor {
    private val lock = Any()
    private var framesInWindow: Int = 0
    private var windowStartNs: Long = System.nanoTime()

    private val fpsState = MutableStateFlow(0.0)
    val framesPerSecond: StateFlow<Double> = fpsState.asStateFlow()

    private val framesState = MutableStateFlow(0L)
    val framesSeen: StateFlow<Long> = framesState.asStateFlow()

    fun onFrame(timestampNs: Long) {
        synchronized(lock) {
            framesInWindow += 1
            framesState.value = framesState.value + 1
            val now = System.nanoTime()
            val elapsed = now - windowStartNs
            if (elapsed >= 1_000_000_000L) {
                fpsState.value = framesInWindow * 1_000_000_000.0 / elapsed.toDouble()
                framesInWindow = 0
                windowStartNs = now
            }
        }
        // timestampNs is the camera frame time; kept for a later pipeline.
        if (timestampNs < 0L) {
            fpsState.value = 0.0
        }
    }

    fun reset() {
        synchronized(lock) {
            framesInWindow = 0
            windowStartNs = System.nanoTime()
            fpsState.value = 0.0
            framesState.value = 0L
        }
    }
}
