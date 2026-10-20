package com.triguide.ai.diagnostics

import kotlin.math.ceil

internal data class SemanticOverlaySnapshot(val publishedSamples: Int, val canvasSamples: Int,
    val canvasCalls: Int, val paintedCells: Int, val clearedWithoutDraw: Int,
    val publishToFirstCanvasP50Ms: Double?, val publishToFirstCanvasP90Ms: Double?,
    val submittedCalls: Int, val submittedCells: Int, val rejectedAfterPaintCalls: Int)

/** Canvas submission counters only, NOT compositor visibility or human recognition evidence. */
internal class SemanticOverlayStats {
    private var stopped = false
    private var currentStamp = 0L
    private var lastPublished = 0L
    private var lastPublishedElapsedNs = 0L
    private var received = 0L
    private var published = 0L
    private var paintedCurrent = false
    private var publishedSamples = 0
    private var canvasSamples = 0
    private var canvasCalls = 0
    private var paintedCells = 0
    private var clearedWithoutDraw = 0
    private var submittedCalls = 0
    private var submittedCells = 0
    private var rejectedAfterPaintCalls = 0
    private val firstCanvasMs = ArrayList<Double>(60)

    @Synchronized fun publish(stampNs: Long, receivedNs: Long, nowNs: Long): Boolean {
        if (stopped || stampNs <= lastPublished || stampNs <= 0 || nowNs < lastPublishedElapsedNs ||
            !SemanticFusionMapping.fresh(receivedNs, nowNs, true, true)) return false
        clear()
        currentStamp = stampNs; lastPublished = stampNs
        received = receivedNs; published = nowNs; paintedCurrent = false
        lastPublishedElapsedNs = nowNs
        publishedSamples++
        return true
    }

    @Synchronized fun draw(stampNs: Long, nowNs: Long, allowed: Boolean, cells: Int): Boolean {
        if (stopped || stampNs != currentStamp || stampNs <= 0 || cells !in 1..64 || nowNs < published) return false
        // Called after drawRect: do not hide actual commands if age/revision changed DURING paint.
        submittedCalls++; submittedCells += cells
        if (!allowed || !SemanticFusionMapping.fresh(received, nowNs, true, true)) {
            rejectedAfterPaintCalls++
            return false
        }
        canvasCalls++; paintedCells += cells
        if (!paintedCurrent) {
            paintedCurrent = true; canvasSamples++
            if (firstCanvasMs.size < 60) firstCanvasMs += (nowNs - published) / 1_000_000.0
        }
        return true
    }

    @Synchronized fun clear() {
        if (currentStamp != 0L && !paintedCurrent) clearedWithoutDraw++
        currentStamp = 0L; paintedCurrent = false
    }
    @Synchronized fun stop() { clear(); stopped = true }
    @Synchronized fun snapshot(): SemanticOverlaySnapshot {
        val sorted = firstCanvasMs.sorted()
        fun p(q: Double): Double? = if (sorted.isEmpty()) null else sorted[(ceil(sorted.size*q).toInt()-1).coerceAtLeast(0)]
        return SemanticOverlaySnapshot(publishedSamples,canvasSamples,canvasCalls,paintedCells,
            clearedWithoutDraw,p(.5),p(.9),submittedCalls,submittedCells,rejectedAfterPaintCalls)
    }
}
