package com.triguide.ai.diagnostics

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED contract for [RawDepthFrameStats.summarize] / [RawDepthSummary].
 * Diagnostic decoder only: unknown mm + confidence stats, never walkability.
 */
class RawDepthFrameStatsTest {

    @Test
    fun unsignedDepthAboveSignedShortAndHighConfidenceArePreserved() {
        // 40000 mm must not be read as a signed short; confidence 200 is unsigned >127.
        val depth = depthBuffer(width = 1, height = 1, depthsMm = intArrayOf(40_000), pixelStride = 2, rowStride = 2)
        val confidence = confidenceBuffer(width = 1, height = 1, values = intArrayOf(200), pixelStride = 1, rowStride = 1)

        val summary = RawDepthFrameStats.summarize(
            width = 1,
            height = 1,
            depth = depth,
            depthRowStride = 2,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 1,
            confidencePixelStride = 1,
            confidenceThreshold = 128,
        )

        assertEquals(1, summary.sampledPixels)
        assertEquals(1, summary.validDepthPixels)
        assertEquals(1, summary.confidentPixels)
        assertEquals(40_000, summary.medianDepthMm)
        assertEquals(40_000, summary.p90DepthMm)
    }

    @Test
    fun doesNotApplyThirteenBitDepthMask() {
        // Full UINT16 millimetres; high bits must not be stripped to 13-bit packed depth.
        val depths = intArrayOf(0xFFFF, 0x2000, 0x1FFF)
        val depth = depthBuffer(width = 3, height = 1, depthsMm = depths, pixelStride = 2, rowStride = 6)
        val confidence = confidenceBuffer(
            width = 3,
            height = 1,
            values = intArrayOf(255, 255, 255),
            pixelStride = 1,
            rowStride = 3,
        )

        val summary = RawDepthFrameStats.summarize(
            width = 3,
            height = 1,
            depth = depth,
            depthRowStride = 6,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 3,
            confidencePixelStride = 1,
        )

        assertEquals(3, summary.validDepthPixels)
        // Sorted 0x1FFF, 0x2000, 0xFFFF; median ceil(0.5*3)-1 = 1 → 0x2000
        assertEquals(0x2000, summary.medianDepthMm)
        assertEquals(0xFFFF, summary.p90DepthMm)
    }

    @Test
    fun nonzeroBufferOffsetsAreHonoured() {
        val depthBacking = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        depthBacking.position(4)
        depthBacking.putShort(1_500.toShort())
        depthBacking.putShort(2_500.toShort())
        depthBacking.position(4)
        depthBacking.limit(8)

        val confidenceBacking = ByteBuffer.allocate(8)
        confidenceBacking.position(2)
        confidenceBacking.put(200.toByte())
        confidenceBacking.put(200.toByte())
        confidenceBacking.position(2)
        confidenceBacking.limit(4)

        val summary = RawDepthFrameStats.summarize(
            width = 2,
            height = 1,
            depth = depthBacking,
            depthRowStride = 4,
            depthPixelStride = 2,
            confidence = confidenceBacking,
            confidenceRowStride = 2,
            confidencePixelStride = 1,
        )

        assertEquals(2, summary.sampledPixels)
        assertEquals(2, summary.validDepthPixels)
        assertEquals(1_500, summary.medianDepthMm)
        assertEquals(2_500, summary.p90DepthMm)
        assertEquals(4, depthBacking.position())
        assertEquals(8, depthBacking.limit())
        assertEquals(2, confidenceBacking.position())
        assertEquals(4, confidenceBacking.limit())
    }

    @Test
    fun paddedRowsAndWidePixelStridesSkipPaddingBytes() {
        // depth pixelStride 4 (UINT16 + 2 pad), rowStride 12 for width 2 (4 pad after last pixel).
        val depth = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        depth.putShort(0, 100)
        depth.putShort(4, 200)
        depth.putShort(12, 300)
        depth.putShort(16, 400)
        // Poison padding so a stride bug would read wrong values.
        depth.putShort(2, 9_999.toShort())
        depth.putShort(6, 9_999.toShort())
        depth.putShort(8, 9_999.toShort())
        depth.putShort(10, 9_999.toShort())
        depth.putShort(14, 9_999.toShort())
        depth.putShort(18, 9_999.toShort())
        depth.putShort(20, 9_999.toShort())
        depth.putShort(22, 9_999.toShort())

        val confidence = ByteBuffer.allocate(16)
        confidence.put(0, 255.toByte())
        confidence.put(2, 255.toByte())
        confidence.put(8, 255.toByte())
        confidence.put(10, 255.toByte())
        confidence.put(1, 1.toByte())
        confidence.put(3, 1.toByte())
        confidence.put(4, 1.toByte())
        confidence.put(5, 1.toByte())
        confidence.put(6, 1.toByte())
        confidence.put(7, 1.toByte())
        confidence.put(9, 1.toByte())
        confidence.put(11, 1.toByte())

        val summary = RawDepthFrameStats.summarize(
            width = 2,
            height = 2,
            depth = depth,
            depthRowStride = 12,
            depthPixelStride = 4,
            confidence = confidence,
            confidenceRowStride = 8,
            confidencePixelStride = 2,
        )

        assertEquals(4, summary.sampledPixels)
        assertEquals(4, summary.validDepthPixels)
        assertEquals(4, summary.confidentPixels)
        // Sorted: 100,200,300,400; median ceil(0.5*4)-1 = 1 → 200; p90 ceil(0.9*4)-1 = 3 → 400
        assertEquals(200, summary.medianDepthMm)
        assertEquals(400, summary.p90DepthMm)
    }

    @Test
    fun readOnlyAndDirectBuffersAreSupportedWithoutMutation() {
        val depthBytes = byteArrayOf(0xE8.toByte(), 0x03, 0xD0.toByte(), 0x07) // 1000, 2000 LE
        val depthDirect = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN).put(depthBytes)
        depthDirect.clear()
        val depth = depthDirect.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)

        val confidenceDirect = ByteBuffer.allocateDirect(2).put(byteArrayOf(255.toByte(), 255.toByte()))
        confidenceDirect.clear()
        val confidence = confidenceDirect.asReadOnlyBuffer()

        val beforeDepth = snapshot(depth)
        val beforeConfidence = snapshot(confidence)

        val summary = RawDepthFrameStats.summarize(
            width = 2,
            height = 1,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 2,
            confidencePixelStride = 1,
        )

        assertEquals(2, summary.validDepthPixels)
        assertEquals(1_000, summary.medianDepthMm)
        assertEquals(2_000, summary.p90DepthMm)
        assertUnchanged(beforeDepth, depth)
        assertUnchanged(beforeConfidence, confidence)
        assertEquals(ByteOrder.LITTLE_ENDIAN, depth.order())
    }

    @Test
    fun lastRowWithoutTrailingPadIsAccepted() {
        // width=3, pixelStride=2 → content 6 bytes/row; rowStride=8 with pad on row0 only.
        // Remaining after position covers row0 full stride + last row's 6 bytes (no final pad).
        val depth = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
        depth.putShort(0, 10)
        depth.putShort(2, 20)
        depth.putShort(4, 30)
        depth.putShort(8, 40)
        depth.putShort(10, 50)
        depth.putShort(12, 60)

        val confidence = ByteBuffer.allocate(7)
        // conf rowStride 4 for width 3 pixelStride 1: last row needs only 3 bytes after first rowStride.
        confidence.put(0, 255.toByte())
        confidence.put(1, 255.toByte())
        confidence.put(2, 255.toByte())
        confidence.put(4, 255.toByte())
        confidence.put(5, 255.toByte())
        confidence.put(6, 255.toByte())

        val summary = RawDepthFrameStats.summarize(
            width = 3,
            height = 2,
            depth = depth,
            depthRowStride = 8,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 4,
            confidencePixelStride = 1,
        )

        assertEquals(6, summary.sampledPixels)
        assertEquals(6, summary.validDepthPixels)
        // Sorted 10..60; median ceil(3)-1=2 → 30; p90 ceil(5.4)-1=5 → 60
        assertEquals(30, summary.medianDepthMm)
        assertEquals(60, summary.p90DepthMm)
    }

    @Test
    fun truncatedLastRowIsRejected() {
        val depth = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN) // one byte short of 14
        val confidence = ByteBuffer.allocate(7)
        fillConfidence(confidence, 7, 255)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarize(
                width = 3,
                height = 2,
                depth = depth,
                depthRowStride = 8,
                depthPixelStride = 2,
                confidence = confidence,
                confidenceRowStride = 4,
                confidencePixelStride = 1,
            )
        }
    }

    @Test
    fun truncatedConfidenceLastRowIsRejected() {
        val depth = depthBuffer(
            width = 3,
            height = 2,
            depthsMm = intArrayOf(1, 2, 3, 4, 5, 6),
            pixelStride = 2,
            rowStride = 8,
        )
        val confidence = ByteBuffer.allocate(6) // needs 7 for last-row-without-pad layout

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarize(
                width = 3,
                height = 2,
                depth = depth,
                depthRowStride = 8,
                depthPixelStride = 2,
                confidence = confidence,
                confidenceRowStride = 4,
                confidencePixelStride = 1,
            )
        }
    }

    @Test
    fun invalidDimensionsAreRejected() {
        val depth = depthBuffer(1, 1, intArrayOf(100), 2, 2)
        val confidence = confidenceBuffer(1, 1, intArrayOf(255), 1, 1)

        assertThrows(IllegalArgumentException::class.java) {
            summarize(0, 1, depth, 2, 2, confidence, 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            summarize(1, 0, depth, 2, 2, confidence, 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            summarize(-1, 1, depth, 2, 2, confidence, 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            summarize(1, -1, depth, 2, 2, confidence, 1, 1)
        }
    }

    @Test
    fun pixelCountAboveCapIsRejectedWithoutOverflow() {
        // Cap is 1_048_576; product must use Long arithmetic (1024*1024+1).
        val depth = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        val confidence = ByteBuffer.allocate(1)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarize(
                width = 1_024,
                height = 1_025,
                depth = depth,
                depthRowStride = 2,
                depthPixelStride = 2,
                confidence = confidence,
                confidenceRowStride = 1,
                confidencePixelStride = 1,
            )
        }
    }

    @Test
    fun extremeDimensionsThatOverflowIntProductAreRejected() {
        val depth = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        val confidence = ByteBuffer.allocate(1)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarize(
                width = Int.MAX_VALUE,
                height = 2,
                depth = depth,
                depthRowStride = 2,
                depthPixelStride = 2,
                confidence = confidence,
                confidenceRowStride = 1,
                confidencePixelStride = 1,
            )
        }
    }

    @Test
    fun overlappingStridesAreRejected() {
        val depth = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        val confidence = ByteBuffer.allocate(4)

        // depth pixelStride smaller than UINT16 size → sample overlap
        assertThrows(IllegalArgumentException::class.java) {
            summarize(2, 1, depth, 4, 1, confidence, 2, 1)
        }
        // rowStride too small for width=2 pixelStride=2 (needs >= 4)
        assertThrows(IllegalArgumentException::class.java) {
            summarize(2, 2, depth, 3, 2, confidence, 2, 1)
        }
        // confidence pixelStride < 1 impossible; use 0
        assertThrows(IllegalArgumentException::class.java) {
            summarize(2, 1, depth, 4, 2, confidence, 2, 0)
        }
        // confidence rows overlap: width=3 needs >= 3 bytes content, rowStride 2
        assertThrows(IllegalArgumentException::class.java) {
            summarize(
                width = 3,
                height = 2,
                depth = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN),
                depthRowStride = 6,
                depthPixelStride = 2,
                confidence = ByteBuffer.allocate(6),
                confidenceRowStride = 2,
                confidencePixelStride = 1,
            )
        }
    }

    @Test
    fun invalidThresholdsAreRejected() {
        val depth = depthBuffer(1, 1, intArrayOf(100), 2, 2)
        val confidence = confidenceBuffer(1, 1, intArrayOf(200), 1, 1)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarize(
                width = 1,
                height = 1,
                depth = depth,
                depthRowStride = 2,
                depthPixelStride = 2,
                confidence = confidence,
                confidenceRowStride = 1,
                confidencePixelStride = 1,
                confidenceThreshold = -1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarize(
                width = 1,
                height = 1,
                depth = depth,
                depthRowStride = 2,
                depthPixelStride = 2,
                confidence = confidence,
                confidenceRowStride = 1,
                confidencePixelStride = 1,
                confidenceThreshold = 256,
            )
        }
    }

    @Test
    fun defaultConfidenceThresholdIsOneHundredTwentyEightInclusive() {
        val depth = depthBuffer(2, 1, intArrayOf(1_000, 2_000), 2, 4)
        val confidence = confidenceBuffer(2, 1, intArrayOf(127, 128), 1, 2)

        val summary = RawDepthFrameStats.summarize(
            width = 2,
            height = 1,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 2,
            confidencePixelStride = 1,
        )

        assertEquals(2, summary.sampledPixels)
        assertEquals(2, summary.validDepthPixels)
        assertEquals(1, summary.confidentPixels)
        assertEquals(2_000, summary.medianDepthMm)
        assertEquals(2_000, summary.p90DepthMm)
    }

    @Test
    fun zeroDepthIsMissingNotFreeSpaceAndYieldsNullAggregates() {
        val depth = depthBuffer(2, 2, intArrayOf(0, 0, 0, 0), 2, 4)
        val confidence = confidenceBuffer(2, 2, intArrayOf(255, 255, 255, 255), 1, 2)

        val summary = RawDepthFrameStats.summarize(
            width = 2,
            height = 2,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 2,
            confidencePixelStride = 1,
        )

        assertEquals(4, summary.sampledPixels)
        assertEquals(0, summary.validDepthPixels)
        assertEquals(0, summary.confidentPixels)
        assertNull(summary.medianDepthMm)
        assertNull(summary.p90DepthMm)
    }

    @Test
    fun emptyConfidenceSetYieldsNullPercentilesEvenWithValidDepth() {
        val depth = depthBuffer(2, 1, intArrayOf(500, 700), 2, 4)
        val confidence = confidenceBuffer(2, 1, intArrayOf(0, 50), 1, 2)

        val summary = RawDepthFrameStats.summarize(
            width = 2,
            height = 1,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 2,
            confidencePixelStride = 1,
            confidenceThreshold = 128,
        )

        assertEquals(2, summary.sampledPixels)
        assertEquals(2, summary.validDepthPixels)
        assertEquals(0, summary.confidentPixels)
        assertNull(summary.medianDepthMm)
        assertNull(summary.p90DepthMm)
    }

    @Test
    fun percentileUsesNearestRankCeilQnMinusOneOverConfidentNonzeroOnly() {
        // Depths: 10,0,30,40,50,60,70,80,90,100 with conf pattern mixing below/above threshold.
        val depths = intArrayOf(10, 0, 30, 40, 50, 60, 70, 80, 90, 100)
        val conf = intArrayOf(200, 255, 100, 128, 200, 200, 200, 200, 200, 200)
        val depth = depthBuffer(5, 2, depths, 2, 10)
        val confidence = confidenceBuffer(5, 2, conf, 1, 5)

        val summary = RawDepthFrameStats.summarize(
            width = 5,
            height = 2,
            depth = depth,
            depthRowStride = 10,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 5,
            confidencePixelStride = 1,
            confidenceThreshold = 128,
        )

        // Eligible sorted: 10,40,50,60,70,80,90,100 (n=8); 0 skipped; 30 conf 100 skipped
        assertEquals(10, summary.sampledPixels)
        assertEquals(9, summary.validDepthPixels)
        assertEquals(8, summary.confidentPixels)
        // median ceil(0.5*8)-1 = 3 → 60; p90 ceil(0.9*8)-1 = 7 → 100
        assertEquals(60, summary.medianDepthMm)
        assertEquals(100, summary.p90DepthMm)
    }

    @Test
    fun singleConfidentPixelSetsBothPercentiles() {
        val depth = depthBuffer(1, 1, intArrayOf(3_333), 2, 2)
        val confidence = confidenceBuffer(1, 1, intArrayOf(255), 1, 1)

        val summary = RawDepthFrameStats.summarize(
            width = 1,
            height = 1,
            depth = depth,
            depthRowStride = 2,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 1,
            confidencePixelStride = 1,
        )

        assertEquals(3_333, summary.medianDepthMm)
        assertEquals(3_333, summary.p90DepthMm)
    }

    @Test
    fun summarizeDoesNotMutateCallerBuffers() {
        val depth = depthBuffer(2, 2, intArrayOf(100, 200, 300, 400), 2, 4)
        depth.order(ByteOrder.BIG_ENDIAN) // caller order must be restored / left untouched
        // Re-write LE payload explicitly then leave order as BIG_ENDIAN to prove decode uses LE loads, not buffer order.
        depth.order(ByteOrder.LITTLE_ENDIAN)
        depth.putShort(0, 100)
        depth.putShort(2, 200)
        depth.putShort(4, 300)
        depth.putShort(6, 400)
        depth.order(ByteOrder.BIG_ENDIAN)
        depth.position(0)
        depth.limit(8)

        val confidence = confidenceBuffer(2, 2, intArrayOf(255, 255, 255, 255), 1, 2)
        confidence.position(0)
        confidence.limit(4)

        val depthBytesBefore = ByteArray(8).also { depth.duplicate().order(ByteOrder.LITTLE_ENDIAN).get(it) }
        val confBytesBefore = ByteArray(4).also { confidence.duplicate().get(it) }
        val depthPos = depth.position()
        val depthLimit = depth.limit()
        val depthOrder = depth.order()
        val confPos = confidence.position()
        val confLimit = confidence.limit()
        val confOrder = confidence.order()

        val summary = RawDepthFrameStats.summarize(
            width = 2,
            height = 2,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 2,
            confidencePixelStride = 1,
        )

        assertEquals(4, summary.confidentPixels)
        assertEquals(200, summary.medianDepthMm)
        assertEquals(400, summary.p90DepthMm)

        assertEquals(depthPos, depth.position())
        assertEquals(depthLimit, depth.limit())
        assertEquals(depthOrder, depth.order())
        assertEquals(confPos, confidence.position())
        assertEquals(confLimit, confidence.limit())
        assertEquals(confOrder, confidence.order())

        val depthBytesAfter = ByteArray(8).also {
            depth.duplicate().apply { clear(); order(ByteOrder.LITTLE_ENDIAN) }.get(it)
        }
        val confBytesAfter = ByteArray(4).also { confidence.duplicate().apply { clear() }.get(it) }
        assertArrayEquals(depthBytesBefore, depthBytesAfter)
        assertArrayEquals(confBytesBefore, confBytesAfter)
    }

    @Test
    fun remainingBytesAreMeasuredFromCurrentPosition() {
        val depth = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        depth.position(6)
        depth.putShort(100)
        depth.putShort(200)
        depth.position(6)

        val confidence = ByteBuffer.allocate(6)
        confidence.position(4)
        confidence.put(255.toByte())
        confidence.put(255.toByte())
        confidence.position(4)

        val summary = RawDepthFrameStats.summarize(
            width = 2,
            height = 1,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
            confidence = confidence,
            confidenceRowStride = 2,
            confidencePixelStride = 1,
        )
        assertEquals(2, summary.validDepthPixels)
        assertEquals(100, summary.medianDepthMm)
        assertEquals(200, summary.p90DepthMm)

        // Same layout but position leaves insufficient remaining → reject
        depth.position(7)
        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarize(
                width = 2,
                height = 1,
                depth = depth,
                depthRowStride = 4,
                depthPixelStride = 2,
                confidence = confidence,
                confidenceRowStride = 2,
                confidencePixelStride = 1,
            )
        }
        assertEquals(7, depth.position())
        assertEquals(4, confidence.position())
    }

    // -------------------------------------------------------------------------
    // Depth-only dense diagnostic: DepthOnlySummary / summarizeDepthOnly
    // No confidence, median, freshness, admission, floor/clear/turn fields.
    // -------------------------------------------------------------------------

    @Test
    fun depthOnlyAllZeroYieldsZeroValidPixels() {
        val depth = depthBuffer(2, 2, intArrayOf(0, 0, 0, 0), pixelStride = 2, rowStride = 4)

        val summary = RawDepthFrameStats.summarizeDepthOnly(
            width = 2,
            height = 2,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
        )

        assertEquals(4, summary.sampledPixels)
        assertEquals(0, summary.validDepthPixels)
    }

    @Test
    fun depthOnlyNonzeroCountsAreConfidenceIndependent() {
        // Same depths that pair-summarize would treat differently by confidence;
        // depth-only must count every nonzero UINT16 regardless of any confidence plane.
        val depths = intArrayOf(10, 0, 30, 40, 50, 60, 70, 80, 90, 100)
        val depth = depthBuffer(5, 2, depths, pixelStride = 2, rowStride = 10)

        val summary = RawDepthFrameStats.summarizeDepthOnly(
            width = 5,
            height = 2,
            depth = depth,
            depthRowStride = 10,
            depthPixelStride = 2,
        )

        assertEquals(10, summary.sampledPixels)
        assertEquals(9, summary.validDepthPixels)
    }

    @Test
    fun depthOnlyCountsFullUint16Including65535WithoutThirteenBitMask() {
        val depths = intArrayOf(0xFFFF, 0x2000, 0, 0x1FFF)
        val depth = depthBuffer(4, 1, depths, pixelStride = 2, rowStride = 8)

        val summary = RawDepthFrameStats.summarizeDepthOnly(
            width = 4,
            height = 1,
            depth = depth,
            depthRowStride = 8,
            depthPixelStride = 2,
        )

        assertEquals(4, summary.sampledPixels)
        assertEquals(3, summary.validDepthPixels)
    }

    @Test
    fun depthOnlyLittleEndianIndependentOfCallerByteOrder() {
        val depth = ByteBuffer.allocate(4)
        // Explicit LE payload: 1000 mm (0x03E8), 2000 mm (0x07D0)
        depth.put(0, 0xE8.toByte())
        depth.put(1, 0x03.toByte())
        depth.put(2, 0xD0.toByte())
        depth.put(3, 0x07.toByte())
        depth.order(ByteOrder.BIG_ENDIAN)
        depth.position(0)
        depth.limit(4)

        val summary = RawDepthFrameStats.summarizeDepthOnly(
            width = 2,
            height = 1,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
        )

        assertEquals(2, summary.sampledPixels)
        assertEquals(2, summary.validDepthPixels)
        assertEquals(ByteOrder.BIG_ENDIAN, depth.order())
        assertEquals(0, depth.position())
        assertEquals(4, depth.limit())
    }

    @Test
    fun depthOnlyPaddedRowsAndWidePixelStridesSkipPadding() {
        val depth = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        depth.putShort(0, 100)
        depth.putShort(4, 200)
        depth.putShort(12, 300)
        depth.putShort(16, 0) // zero remains invalid
        // Poison padding so a stride bug would invent nonzero valid counts.
        depth.putShort(2, 9_999.toShort())
        depth.putShort(6, 9_999.toShort())
        depth.putShort(8, 9_999.toShort())
        depth.putShort(10, 9_999.toShort())
        depth.putShort(14, 9_999.toShort())
        depth.putShort(18, 9_999.toShort())
        depth.putShort(20, 9_999.toShort())
        depth.putShort(22, 9_999.toShort())

        val summary = RawDepthFrameStats.summarizeDepthOnly(
            width = 2,
            height = 2,
            depth = depth,
            depthRowStride = 12,
            depthPixelStride = 4,
        )

        assertEquals(4, summary.sampledPixels)
        assertEquals(3, summary.validDepthPixels)
    }

    @Test
    fun depthOnlyNonzeroPositionAndLimitPreservedOnSuccess() {
        val depthBacking = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        depthBacking.position(4)
        depthBacking.putShort(1_500.toShort())
        depthBacking.putShort(0.toShort())
        depthBacking.position(4)
        depthBacking.limit(8)

        val summary = RawDepthFrameStats.summarizeDepthOnly(
            width = 2,
            height = 1,
            depth = depthBacking,
            depthRowStride = 4,
            depthPixelStride = 2,
        )

        assertEquals(2, summary.sampledPixels)
        assertEquals(1, summary.validDepthPixels)
        assertEquals(4, depthBacking.position())
        assertEquals(8, depthBacking.limit())
        assertEquals(ByteOrder.LITTLE_ENDIAN, depthBacking.order())
    }

    @Test
    fun depthOnlyNonzeroPositionAndLimitPreservedOnFailure() {
        val depth = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        depth.position(6)
        depth.putShort(100)
        depth.putShort(200)
        depth.position(7) // insufficient remaining for width=2
        depth.limit(10)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(
                width = 2,
                height = 1,
                depth = depth,
                depthRowStride = 4,
                depthPixelStride = 2,
            )
        }
        assertEquals(7, depth.position())
        assertEquals(10, depth.limit())
        assertEquals(ByteOrder.LITTLE_ENDIAN, depth.order())
    }

    @Test
    fun depthOnlyInvalidDimensionsAreRejected() {
        val depth = depthBuffer(1, 1, intArrayOf(100), 2, 2)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(0, 1, depth, 2, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(1, 0, depth, 2, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(-1, 1, depth, 2, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(1, -1, depth, 2, 2)
        }
    }

    @Test
    fun depthOnlyPixelCountAboveCapIsRejectedWithoutOverflow() {
        val depth = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(
                width = 1_024,
                height = 1_025,
                depth = depth,
                depthRowStride = 2,
                depthPixelStride = 2,
            )
        }
    }

    @Test
    fun depthOnlyExtremeDimensionsThatOverflowIntProductAreRejected() {
        val depth = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(
                width = Int.MAX_VALUE,
                height = 2,
                depth = depth,
                depthRowStride = 2,
                depthPixelStride = 2,
            )
        }
    }

    @Test
    fun depthOnlyOverlappingStridesAreRejected() {
        val depth = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(
                width = 2,
                height = 1,
                depth = depth,
                depthRowStride = 4,
                depthPixelStride = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(
                width = 2,
                height = 2,
                depth = depth,
                depthRowStride = 3,
                depthPixelStride = 2,
            )
        }
    }

    @Test
    fun depthOnlyTruncatedLastRowIsRejected() {
        val depth = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(
                width = 3,
                height = 2,
                depth = depth,
                depthRowStride = 8,
                depthPixelStride = 2,
            )
        }
    }

    @Test
    fun depthOnlyLastRowWithoutTrailingPadIsAccepted() {
        val depth = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
        depth.putShort(0, 10)
        depth.putShort(2, 20)
        depth.putShort(4, 0)
        depth.putShort(8, 40)
        depth.putShort(10, 50)
        depth.putShort(12, 60)

        val summary = RawDepthFrameStats.summarizeDepthOnly(
            width = 3,
            height = 2,
            depth = depth,
            depthRowStride = 8,
            depthPixelStride = 2,
        )

        assertEquals(6, summary.sampledPixels)
        assertEquals(5, summary.validDepthPixels)
    }

    @Test
    fun depthOnlyReadOnlyAndDirectBuffersSupportedWithoutMutation() {
        val depthBytes = byteArrayOf(0xE8.toByte(), 0x03, 0x00, 0x00) // 1000, 0 LE
        val depthDirect = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN).put(depthBytes)
        depthDirect.clear()
        val depth = depthDirect.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val before = snapshot(depth)

        val summary = RawDepthFrameStats.summarizeDepthOnly(
            width = 2,
            height = 1,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
        )

        assertEquals(2, summary.sampledPixels)
        assertEquals(1, summary.validDepthPixels)
        assertUnchanged(before, depth)
        assertEquals(ByteOrder.LITTLE_ENDIAN, depth.order())
    }

    @Test
    fun depthOnlyDoesNotMutateCallerBufferBytesOrOrder() {
        val depth = ByteBuffer.allocate(8)
        depth.order(ByteOrder.LITTLE_ENDIAN)
        depth.putShort(0, 100)
        depth.putShort(2, 0)
        depth.putShort(4, 300)
        depth.putShort(6, 400)
        depth.order(ByteOrder.BIG_ENDIAN)
        depth.position(0)
        depth.limit(8)

        val bytesBefore = ByteArray(8).also { depth.duplicate().order(ByteOrder.LITTLE_ENDIAN).get(it) }
        val pos = depth.position()
        val limit = depth.limit()
        val order = depth.order()

        val summary = RawDepthFrameStats.summarizeDepthOnly(
            width = 2,
            height = 2,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
        )

        assertEquals(4, summary.sampledPixels)
        assertEquals(3, summary.validDepthPixels)
        assertEquals(pos, depth.position())
        assertEquals(limit, depth.limit())
        assertEquals(order, depth.order())
        val bytesAfter = ByteArray(8).also {
            depth.duplicate().apply { clear(); order(ByteOrder.LITTLE_ENDIAN) }.get(it)
        }
        assertArrayEquals(bytesBefore, bytesAfter)
    }

    @Test
    fun depthOnlyRemainingBytesAreMeasuredFromCurrentPosition() {
        val depth = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        depth.position(6)
        depth.putShort(100)
        depth.putShort(200)
        depth.position(6)

        val summary = RawDepthFrameStats.summarizeDepthOnly(
            width = 2,
            height = 1,
            depth = depth,
            depthRowStride = 4,
            depthPixelStride = 2,
        )
        assertEquals(2, summary.sampledPixels)
        assertEquals(2, summary.validDepthPixels)
        assertEquals(6, depth.position())
    }

    @Test
    fun depthOnlyNegativeRowStrideIsRejected() {
        val depth = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

        assertThrows(IllegalArgumentException::class.java) {
            RawDepthFrameStats.summarizeDepthOnly(
                width = 1,
                height = 2,
                depth = depth,
                depthRowStride = -1,
                depthPixelStride = 2,
            )
        }
    }

    private fun summarize(
        width: Int,
        height: Int,
        depth: ByteBuffer,
        depthRowStride: Int,
        depthPixelStride: Int,
        confidence: ByteBuffer,
        confidenceRowStride: Int,
        confidencePixelStride: Int,
        confidenceThreshold: Int = 128,
    ): RawDepthSummary =
        RawDepthFrameStats.summarize(
            width = width,
            height = height,
            depth = depth,
            depthRowStride = depthRowStride,
            depthPixelStride = depthPixelStride,
            confidence = confidence,
            confidenceRowStride = confidenceRowStride,
            confidencePixelStride = confidencePixelStride,
            confidenceThreshold = confidenceThreshold,
        )

    private fun depthBuffer(
        width: Int,
        height: Int,
        depthsMm: IntArray,
        pixelStride: Int,
        rowStride: Int,
    ): ByteBuffer {
        require(depthsMm.size == width * height)
        val last = (height - 1).toLong() * rowStride + (width - 1).toLong() * pixelStride + 2
        val buffer = ByteBuffer.allocate(last.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = y * rowStride + x * pixelStride
                buffer.putShort(offset, (depthsMm[i] and 0xFFFF).toShort())
                i++
            }
        }
        return buffer
    }

    private fun confidenceBuffer(
        width: Int,
        height: Int,
        values: IntArray,
        pixelStride: Int,
        rowStride: Int,
    ): ByteBuffer {
        require(values.size == width * height)
        val last = (height - 1).toLong() * rowStride + (width - 1).toLong() * pixelStride + 1
        val buffer = ByteBuffer.allocate(last.toInt())
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = y * rowStride + x * pixelStride
                buffer.put(offset, (values[i] and 0xFF).toByte())
                i++
            }
        }
        return buffer
    }

    private fun fillConfidence(buffer: ByteBuffer, count: Int, value: Int) {
        for (i in 0 until count) {
            buffer.put(i, (value and 0xFF).toByte())
        }
    }

    private data class BufferSnapshot(
        val position: Int,
        val limit: Int,
        val order: ByteOrder,
        val bytes: ByteArray,
    )

    private fun snapshot(buffer: ByteBuffer): BufferSnapshot {
        val dup = buffer.duplicate()
        val bytes = ByteArray(dup.remaining())
        dup.get(bytes)
        return BufferSnapshot(buffer.position(), buffer.limit(), buffer.order(), bytes)
    }

    private fun assertUnchanged(before: BufferSnapshot, buffer: ByteBuffer) {
        assertEquals(before.position, buffer.position())
        assertEquals(before.limit, buffer.limit())
        assertEquals(before.order, buffer.order())
        val after = ByteArray(buffer.remaining())
        buffer.duplicate().get(after)
        assertArrayEquals(before.bytes, after)
        assertTrue(before.bytes.isNotEmpty() || after.isEmpty())
    }
}
