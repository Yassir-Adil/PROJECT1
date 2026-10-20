package com.triguide.ai.diagnostics

import kotlin.math.min

/** Consumes SDK-transformed CPU UV, never assumes CPU/depth/view share aspect or rotation. */
internal object SemanticFusionMapping {
    private const val MAX_PIXELS = 1_048_576L
    private fun dimensions(w: Int, h: Int) = w > 0 && h > 0 && w.toLong() * h <= MAX_PIXELS
    private fun uv(u: Double, v: Double) = u.isFinite() && v.isFinite() && u >= 0 && u < 1 && v >= 0 && v < 1

    /** Inverse of DiagnosticSegmenter.prepare's centered gray letterbox, pixel-index output. */
    fun modelIndex(width: Int, height: Int, imageU: Double, imageV: Double): Int? {
        if (!dimensions(width, height) || !uv(imageU, imageV)) return null
        val size = DiagnosticSegmenter.SIZE
        val scale = min(size.toDouble() / width, size.toDouble() / height)
        val contentW = width * scale
        val contentH = height * scale
        val x = ((size - contentW) / 2 + imageU * contentW).toInt()
        val y = ((size - contentH) / 2 + imageV * contentH).toInt()
        return y * size + x
    }

    fun depthPixel(width: Int, height: Int, textureU: Double, textureV: Double): Pair<Int, Int>? {
        if (!dimensions(width, height) || !uv(textureU, textureV)) return null
        return (textureU * width).toInt() to (textureV * height).toInt()
    }

    /** Local receipt age only; neither sensor timestamps nor completion time reset freshness. */
    fun fresh(receivedNs: Long, nowNs: Long, running: Boolean, tracking: Boolean,
        maximumAgeNs: Long = 250_000_000L): Boolean =
        running && tracking && receivedNs >= 0 && maximumAgeNs > 0 && nowNs >= receivedNs &&
            nowNs - receivedNs <= maximumAgeNs
}
