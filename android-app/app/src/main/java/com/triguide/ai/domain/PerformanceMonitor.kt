package com.triguide.ai.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Counts CameraX analysis frames and completed detector inferences separately.
 * Latency is recorded only when the caller supplies a measured duration.
 */
class PerformanceMonitor(
    private val clockNs: () -> Long = System::nanoTime,
    private val latencyWindowSize: Int = 30,
) {
    init {
        require(latencyWindowSize > 0) { "latency window must be positive" }
    }

    private val lock = Any()
    private var framesInWindow: Int = 0
    private var windowStartNs: Long = clockNs()
    private var inferencesInWindow: Int = 0
    private var inferenceWindowStartNs: Long = clockNs()
    private val latencySamples = ArrayDeque<Double>()

    private val fpsState = MutableStateFlow(0.0)
    val framesPerSecond: StateFlow<Double> = fpsState.asStateFlow()

    private val framesState = MutableStateFlow(0L)
    val framesSeen: StateFlow<Long> = framesState.asStateFlow()

    private val lastLatencyState = MutableStateFlow<Double?>(null)
    val lastInferenceLatencyMs: StateFlow<Double?> = lastLatencyState.asStateFlow()

    private val averageLatencyState = MutableStateFlow<Double?>(null)
    val averageInferenceLatencyMs: StateFlow<Double?> = averageLatencyState.asStateFlow()

    private val inferenceFpsState = MutableStateFlow<Double?>(null)
    val inferenceFramesPerSecond: StateFlow<Double?> = inferenceFpsState.asStateFlow()

    private val inferenceCountState = MutableStateFlow(0L)
    val inferencesCompleted: StateFlow<Long> = inferenceCountState.asStateFlow()

    fun onFrame(timestampNs: Long) {
        synchronized(lock) {
            framesInWindow += 1
            framesState.value = framesState.value + 1
            val now = clockNs()
            val elapsed = now - windowStartNs
            if (elapsed >= 1_000_000_000L) {
                fpsState.value = framesInWindow * 1_000_000_000.0 / elapsed.toDouble()
                framesInWindow = 0
                windowStartNs = now
            }
        }
        if (timestampNs < 0L) {
            fpsState.value = 0.0
        }
    }

    fun onInferenceCompleted(latencyMs: Double) {
        require(latencyMs.isFinite() && latencyMs >= 0.0) {
            "Inference latency must be a finite, non-negative number of milliseconds."
        }
        synchronized(lock) {
            inferenceCountState.value = inferenceCountState.value + 1L
            lastLatencyState.value = latencyMs
            latencySamples.addLast(latencyMs)
            while (latencySamples.size > latencyWindowSize) {
                latencySamples.removeFirst()
            }
            averageLatencyState.value = latencySamples.average()
            inferencesInWindow += 1
            val now = clockNs()
            val elapsed = now - inferenceWindowStartNs
            if (elapsed >= 1_000_000_000L) {
                inferenceFpsState.value = inferencesInWindow * 1_000_000_000.0 / elapsed.toDouble()
                inferencesInWindow = 0
                inferenceWindowStartNs = now
            }
        }
    }

    fun reset() {
        synchronized(lock) {
            framesInWindow = 0
            windowStartNs = clockNs()
            inferencesInWindow = 0
            inferenceWindowStartNs = clockNs()
            latencySamples.clear()
            fpsState.value = 0.0
            framesState.value = 0L
            lastLatencyState.value = null
            averageLatencyState.value = null
            inferenceFpsState.value = null
            inferenceCountState.value = 0L
        }
    }
}
