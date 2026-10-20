package com.triguide.ai.diagnostics

import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RawDepthImageFormatTest {
    @Test fun acceptsActualRedmiRaw16BitFormat48() {
        // Runtime regression: acquireRawDepthImage16Bits returned48 on Redmi.
        assertEquals(48, HardwareBuffer.D_16)
        RawDepthImageFormat.requireRaw16BitDepth(48)
    }

    @Test fun rejectsPackedDepth16RatherThanTreatingItAsFullUnsignedDepth() {
        // ImageFormat.DEPTH16 packs confidence into the upper3 bits: a different contract.
        assertThrows(IllegalStateException::class.java) {
            RawDepthImageFormat.requireRaw16BitDepth(ImageFormat.DEPTH16)
        }
    }

    @Test fun rejectsConfidenceOrColorFormatsAsDepth() {
        for (format in listOf(ImageFormat.Y8, ImageFormat.YUV_420_888, ImageFormat.JPEG)) {
            assertThrows(IllegalStateException::class.java) {
                RawDepthImageFormat.requireRaw16BitDepth(format)
            }
        }
    }

    @Test fun rejectsUnknownAndInvalidDepthFormats() {
        for (format in listOf(-1, 0, 49, Int.MAX_VALUE)) {
            assertThrows(IllegalStateException::class.java) {
                RawDepthImageFormat.requireRaw16BitDepth(format)
            }
        }
    }
}
