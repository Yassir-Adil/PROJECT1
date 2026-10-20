package com.triguide.ai.diagnostics

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View

/** Coarse 8x8 semantic cells, NOT object boxes, confidence, obstacles or a safe-path overlay. */
internal class SemanticFusionOverlayView(context: Context,
    private val stats: SemanticOverlayStats,
    private val trace: SemanticDisplayTrace,
    private val displayDecision: (SemanticFusionResult, Long) -> SemanticDisplayDecision) : View(context) {
    private val painter = SemanticGridPainter()
    private var result: SemanticFusionResult? = null
    private var cells: List<SemanticPaintCell> = emptyList()
    private var expiry: Runnable? = null
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false; isFocusable = false }
    fun show(value: SemanticFusionResult?) {
        cancelExpiry()
        if (value == null) { stats.clear(); result = null; cells = emptyList() }
        else {
            val now = SystemClock.elapsedRealtimeNanos()
            var decision = displayDecision(value, now)
            if (decision == SemanticDisplayDecision.ALLOWED &&
                !stats.publish(value.frameTimestampNs, value.receivedNs, now)) {
                decision = SemanticDisplayDecision.PUBLICATION_REJECTED
            }
            trace.view(decision)
            if (decision != SemanticDisplayDecision.ALLOWED) {
                stats.clear(); result = null; cells = emptyList(); invalidate(); return
            }
            result = value
            cells = SemanticPaintGrid.fromDepth(value.cells)
        }
        invalidate()
        if (value != null) {
            val callback = Runnable { if (result === value) { expiry = null; invalidate() } }
            expiry = callback
            postDelayed(callback,
                ((ArCoreSemanticDiagnostic.MAX_AGE_NS - (SystemClock.elapsedRealtimeNanos() - value.receivedNs))
                    .coerceAtLeast(0) / 1_000_000L) + 1)
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = result ?: return
        val allowed = displayDecision(current, SystemClock.elapsedRealtimeNanos())
        val decision = when {
            allowed != SemanticDisplayDecision.ALLOWED -> allowed
            width <= 0 || height <= 0 -> SemanticDisplayDecision.INVALID_BOUNDS
            cells.none { SemanticPaintGrid.colour(it.label) != null } -> SemanticDisplayDecision.EMPTY_PAINT
            else -> SemanticDisplayDecision.ALLOWED
        }
        trace.draw(decision)
        if (decision != SemanticDisplayDecision.ALLOWED) {
            if (decision.clearsPublication) {
                cancelExpiry(); stats.clear(); result = null; cells = emptyList()
            }
            postInvalidateOnAnimation() // one empty redraw backstop, never a stale lifetime extension
            return
        }
        val painted = painter.draw(canvas, width, height, cells, resources.displayMetrics.density)
        // Recheck after drawing too: count only fresh, live canvas submissions.
        val ended = SystemClock.elapsedRealtimeNanos()
        val stillFresh = displayDecision(current, ended) == SemanticDisplayDecision.ALLOWED
        stats.draw(current.frameTimestampNs, ended, stillFresh, painted)
        if (!stillFresh) { cancelExpiry(); stats.clear(); result = null; cells = emptyList(); postInvalidateOnAnimation() }
    }

    private fun cancelExpiry() {
        expiry?.let { removeCallbacks(it) }
        expiry = null
    }

    override fun onDetachedFromWindow() {
        cancelExpiry(); stats.clear(); result = null; cells = emptyList()
        super.onDetachedFromWindow()
    }
}
