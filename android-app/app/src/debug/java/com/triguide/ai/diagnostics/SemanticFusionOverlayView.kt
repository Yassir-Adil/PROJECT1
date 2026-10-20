package com.triguide.ai.diagnostics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View

/** Coarse 8x8 semantic cells, NOT object boxes, confidence, obstacles or a safe-path overlay. */
internal class SemanticFusionOverlayView(context: Context,
    private val canDisplay: (SemanticFusionResult, Long) -> Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var result: SemanticFusionResult? = null
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false; isFocusable = false }
    fun show(value: SemanticFusionResult?) {
        result = value
        invalidate()
        if (value != null) postInvalidateDelayed(
            ((ArCoreSemanticDiagnostic.MAX_AGE_NS - (SystemClock.elapsedRealtimeNanos() - value.receivedNs))
                .coerceAtLeast(0) / 1_000_000L) + 1,
        )
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = result ?: return
        if (!canDisplay(current, SystemClock.elapsedRealtimeNanos())) {
            result = null
            return
        }
        for (cell in current.cells) {
            val color = when (cell.label) {
                1 -> Color.rgb(95, 170, 255) // wall-like blue
                4 -> Color.rgb(175, 145, 255) // floor-like purple; never safe-green
                29 -> Color.rgb(245, 190, 70)
                else -> continue
            }
            val l = cell.column * width / 8f; val t = cell.row * height / 8f
            paint.style = Paint.Style.FILL; paint.color = color; paint.alpha = 40
            canvas.drawRect(l, t, l + width / 8f, t + height / 8f, paint)
            paint.style = Paint.Style.STROKE; paint.alpha = 180; paint.strokeWidth = resources.displayMetrics.density
            canvas.drawRect(l, t, l + width / 8f, t + height / 8f, paint)
        }
    }
}
