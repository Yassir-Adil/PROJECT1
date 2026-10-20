package com.triguide.ai.diagnostics

import kotlin.math.abs

internal data class OrientedSemanticPixel(val width: Int, val height: Int, val x: Int, val y: Int)

/** SDK CPU→view axes, not a hardcoded phone sensor/display-rotation formula. Rear camera only. */
internal object SemanticImageOrientation {
    /** Transformed CPU normalized points (0,0),(1,0),(0,1); crop translations/scales allowed. */
    fun fromViewAxes(points: FloatArray): Int? {
        if (points.size != 6 || points.any { !it.isFinite() }) return null
        val ax = points[2].toDouble() - points[0]; val ay = points[3].toDouble() - points[1]
        val bx = points[4].toDouble() - points[0]; val by = points[5].toDouble() - points[1]
        fun horizontal(x: Double, y: Double) = abs(x) > 0 && abs(y) <= abs(x) * 0.00001
        fun vertical(x: Double, y: Double) = abs(y) > 0 && abs(x) <= abs(y) * 0.00001
        return when {
            horizontal(ax, ay) && vertical(bx, by) && ax > 0 && by > 0 -> 0
            vertical(ax, ay) && horizontal(bx, by) && ay > 0 && bx < 0 -> 1
            horizontal(ax, ay) && vertical(bx, by) && ax < 0 && by < 0 -> 2
            vertical(ax, ay) && horizontal(bx, by) && ay < 0 && bx > 0 -> 3
            else -> null // mirrored/sheared/unavailable transform must not become an orientation guess
        }
    }

    /** Nearest CPU pixel then exact integer rotation; no normalized1.0 clamping at rotated edges. */
    fun pixel(width: Int, height: Int, u: Double, v: Double, quarterTurns: Int): OrientedSemanticPixel? {
        if (quarterTurns !in 0..3) return null
        val (x, y) = SemanticFusionMapping.depthPixel(width, height, u, v) ?: return null
        return when (quarterTurns) {
            0 -> OrientedSemanticPixel(width, height, x, y)
            1 -> OrientedSemanticPixel(height, width, height - 1 - y, x)
            2 -> OrientedSemanticPixel(width, height, width - 1 - x, height - 1 - y)
            else -> OrientedSemanticPixel(height, width, y, width - 1 - x)
        }
    }
}
