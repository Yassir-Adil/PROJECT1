package com.triguide.ai.diagnostics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Four view-space probe markers, not object boxes or safe-path indicators. */
internal class SurfaceProbeOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        setShadowLayer(2f * density, 0f, 0f, Color.BLACK)
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 7f * density
        for (ray in SurfaceProbeLayout.points(width, height)) {
            canvas.drawCircle(ray.xPx, ray.yPx, radius, marker)
            canvas.drawLine(ray.xPx - radius, ray.yPx, ray.xPx + radius, ray.yPx, marker)
            canvas.drawLine(ray.xPx, ray.yPx - radius, ray.xPx, ray.yPx + radius, marker)
        }
    }
}
