package com.triguide.ai.diagnostics

import android.hardware.HardwareBuffer

/** Strict format boundary for ARCore Frame.acquireRawDepthImage16Bits(). */
internal object RawDepthImageFormat {
    fun requireRaw16BitDepth(format: Int) {
        // D_16=48 is a compile-time constant, not an API28 method call. ARCore's
        // returned full UINT16 plane is NOT ImageFormat.DEPTH16's packed format.
        // https://developers.google.com/ar/reference/java/com/google/ar/core/Frame#acquireRawDepthImage16Bits()
        if (format != HardwareBuffer.D_16) {
            throw IllegalStateException("unexpected_depth_format_$format expected_D_16_${HardwareBuffer.D_16}")
        }
    }
}
