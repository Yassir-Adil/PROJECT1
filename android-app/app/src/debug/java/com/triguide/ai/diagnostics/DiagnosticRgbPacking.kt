package com.triguide.ai.diagnostics

/** Bitmap.getPixels returns ARGB ints; the pinned UINT8 models require raw RGB bytes. */
internal object DiagnosticRgbPacking {
    fun pack(argb: IntArray, rgb: ByteArray) {
        require(argb.size <= Int.MAX_VALUE / 3 && rgb.size == argb.size * 3)
        var offset = 0
        for (pixel in argb) {
            rgb[offset++] = (pixel ushr 16).toByte()
            rgb[offset++] = (pixel ushr 8).toByte()
            rgb[offset++] = pixel.toByte()
        }
    }
}
