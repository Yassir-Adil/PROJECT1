package com.triguide.ai.diagnostics

import java.nio.ByteBuffer
import kotlin.math.ceil

/**
 * Diagnostic aggregates for one ARCore-style raw-depth + confidence plane pair.
 * CPU-only; does not own frames, claim walkability, or calibrate sensors.
 */
internal data class RawDepthSummary(
    val sampledPixels: Int,
    val validDepthPixels: Int,
    val confidentPixels: Int,
    val medianDepthMm: Int?,
    val p90DepthMm: Int?,
)

/**
 * Diagnostic counts for one depth plane without confidence.
 * CPU-only; does not own frames, claim walkability, or admit samples.
 */
internal data class DepthOnlySummary(
    val sampledPixels: Int,
    val validDepthPixels: Int,
)

/** One transient sampled pixel, never retained by the SDK adapter or written to logs. */
internal data class RawDepthPixel(val x: Int, val y: Int, val depthMm: Int)

/**
 * Decodes unsigned 16-bit little-endian depth (mm) and unsigned confidence 0..255
 * from caller-owned [ByteBuffer]s without mutating their state.
 */
internal object RawDepthFrameStats {
    private const val MAX_PIXELS = 1_048_576
    private const val DEPTH_BYTES = 2
    private const val CONFIDENCE_BYTES = 1

    /** SDK-selected texture pixels; same validator/decoder as the historical diagnostic grid. */
    fun sampleConfidentPixels(
        width: Int, height: Int,
        depth: ByteBuffer, depthRowStride: Int, depthPixelStride: Int,
        confidence: ByteBuffer, confidenceRowStride: Int, confidencePixelStride: Int,
        points: List<Pair<Int, Int>>, confidenceThreshold: Int = 128,
    ): List<RawDepthPixel> {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS)
        require(points.size <= 64 && confidenceThreshold in 0..255)
        require(points.all { (x, y) -> x in 0 until width && y in 0 until height })
        validatePlane(width, height, depth, depthRowStride, depthPixelStride, DEPTH_BYTES, "depth")
        validatePlane(width, height, confidence, confidenceRowStride, confidencePixelStride, CONFIDENCE_BYTES, "confidence")
        return points.mapNotNull { (x, y) ->
            val di = (depth.position().toLong() + y.toLong() * depthRowStride + x.toLong() * depthPixelStride).toInt()
            val ci = (confidence.position().toLong() + y.toLong() * confidenceRowStride + x.toLong() * confidencePixelStride).toInt()
            val mm = readUint16Le(depth, di)
            if (mm > 0 && readUint8(confidence, ci) >= confidenceThreshold) RawDepthPixel(x, y, mm) else null
        }
    }

    /** At most64 positions; missing or low-confidence pixels do not become geometry. */
    fun sampleConfidentGrid(
        width: Int, height: Int,
        depth: ByteBuffer, depthRowStride: Int, depthPixelStride: Int,
        confidence: ByteBuffer, confidenceRowStride: Int, confidencePixelStride: Int,
        gridSize: Int = 3, confidenceThreshold: Int = 128,
    ): List<RawDepthPixel> {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS)
        require(gridSize in 1..8 && confidenceThreshold in 0..255)
        validatePlane(width, height, depth, depthRowStride, depthPixelStride, DEPTH_BYTES, "depth")
        validatePlane(width, height, confidence, confidenceRowStride, confidencePixelStride, CONFIDENCE_BYTES, "confidence")
        val columns = minOf(gridSize, width)
        val rows = minOf(gridSize, height)
        fun coordinate(i: Int, count: Int, extent: Int) =
            if (count == 1) (extent - 1) / 2 else (i.toLong() * (extent - 1) / (count - 1)).toInt()
        return buildList(columns * rows) {
            for (row in 0 until rows) for (column in 0 until columns) {
                val x = coordinate(column, columns, width)
                val y = coordinate(row, rows, height)
                // validatePlane proves absolute indices fit Int and remaining buffer.
                val depthIndex = (depth.position().toLong() + y.toLong() * depthRowStride + x.toLong() * depthPixelStride).toInt()
                val confidenceIndex = (confidence.position().toLong() + y.toLong() * confidenceRowStride + x.toLong() * confidencePixelStride).toInt()
                val mm = readUint16Le(depth, depthIndex)
                if (mm > 0 && readUint8(confidence, confidenceIndex) >= confidenceThreshold) add(RawDepthPixel(x, y, mm))
            }
        }
    }

    fun summarize(
        width: Int,
        height: Int,
        depth: ByteBuffer,
        depthRowStride: Int,
        depthPixelStride: Int,
        confidence: ByteBuffer,
        confidenceRowStride: Int,
        confidencePixelStride: Int,
        confidenceThreshold: Int = 128,
    ): RawDepthSummary {
        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("width and height must be positive")
        }
        if (confidenceThreshold !in 0..255) {
            throw IllegalArgumentException("confidenceThreshold must be in 0..255")
        }

        val pixelCountLong = width.toLong() * height.toLong()
        if (pixelCountLong > MAX_PIXELS) {
            throw IllegalArgumentException("width*height exceeds $MAX_PIXELS")
        }
        val sampledPixels = pixelCountLong.toInt()

        validatePlane(
            width = width,
            height = height,
            buffer = depth,
            rowStride = depthRowStride,
            pixelStride = depthPixelStride,
            elementSize = DEPTH_BYTES,
            label = "depth",
        )
        validatePlane(
            width = width,
            height = height,
            buffer = confidence,
            rowStride = confidenceRowStride,
            pixelStride = confidencePixelStride,
            elementSize = CONFIDENCE_BYTES,
            label = "confidence",
        )

        val depthBase = depth.position()
        val confidenceBase = confidence.position()
        val confidentDepths = IntArray(sampledPixels)
        var validDepthPixels = 0
        var confidentPixels = 0

        for (y in 0 until height) {
            val depthRow = depthBase.toLong() + y.toLong() * depthRowStride
            val confidenceRow = confidenceBase.toLong() + y.toLong() * confidenceRowStride
            for (x in 0 until width) {
                val depthIndex = (depthRow + x.toLong() * depthPixelStride).toInt()
                val confidenceIndex = (confidenceRow + x.toLong() * confidencePixelStride).toInt()
                val depthMm = readUint16Le(depth, depthIndex)
                val conf = readUint8(confidence, confidenceIndex)
                if (depthMm != 0) {
                    validDepthPixels++
                    if (conf >= confidenceThreshold) {
                        confidentDepths[confidentPixels++] = depthMm
                    }
                }
            }
        }

        if (confidentPixels == 0) {
            return RawDepthSummary(
                sampledPixels = sampledPixels,
                validDepthPixels = validDepthPixels,
                confidentPixels = 0,
                medianDepthMm = null,
                p90DepthMm = null,
            )
        }

        confidentDepths.sort(fromIndex = 0, toIndex = confidentPixels)
        return RawDepthSummary(
            sampledPixels = sampledPixels,
            validDepthPixels = validDepthPixels,
            confidentPixels = confidentPixels,
            medianDepthMm = nearestRank(confidentDepths, confidentPixels, 0.5),
            p90DepthMm = nearestRank(confidentDepths, confidentPixels, 0.9),
        )
    }

    /**
     * Depth-plane counts only: nonzero UINT16 LE millimetres are valid.
     * No confidence plane, percentiles, admission, or floor/clear semantics.
     * Does not mutate [depth] position, limit, order, or bytes.
     */
    fun summarizeDepthOnly(
        width: Int,
        height: Int,
        depth: ByteBuffer,
        depthRowStride: Int,
        depthPixelStride: Int,
    ): DepthOnlySummary {
        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("width and height must be positive")
        }

        val pixelCountLong = width.toLong() * height.toLong()
        if (pixelCountLong > MAX_PIXELS) {
            throw IllegalArgumentException("width*height exceeds $MAX_PIXELS")
        }
        val sampledPixels = pixelCountLong.toInt()

        validatePlane(
            width = width,
            height = height,
            buffer = depth,
            rowStride = depthRowStride,
            pixelStride = depthPixelStride,
            elementSize = DEPTH_BYTES,
            label = "depth",
        )

        val depthBase = depth.position()
        var validDepthPixels = 0

        for (y in 0 until height) {
            val depthRow = depthBase.toLong() + y.toLong() * depthRowStride
            for (x in 0 until width) {
                val depthIndex = (depthRow + x.toLong() * depthPixelStride).toInt()
                val depthMm = readUint16Le(depth, depthIndex)
                if (depthMm != 0) {
                    validDepthPixels++
                }
            }
        }

        return DepthOnlySummary(
            sampledPixels = sampledPixels,
            validDepthPixels = validDepthPixels,
        )
    }

    private fun validatePlane(
        width: Int,
        height: Int,
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        elementSize: Int,
        label: String,
    ) {
        if (pixelStride < elementSize) {
            throw IllegalArgumentException("$label pixelStride overlaps samples")
        }
        if (rowStride < 0) {
            throw IllegalArgumentException("$label rowStride must be non-negative")
        }

        val minRowBytes = (width - 1L) * pixelStride + elementSize
        if (minRowBytes > Int.MAX_VALUE) {
            throw IllegalArgumentException("$label row layout overflows")
        }
        if (height > 1 && rowStride.toLong() < minRowBytes) {
            throw IllegalArgumentException("$label rowStride overlaps rows")
        }

        val required = requiredBytes(width, height, rowStride, pixelStride, elementSize)
        if (required > buffer.remaining().toLong()) {
            throw IllegalArgumentException("$label buffer truncated for strides")
        }

        val lastIndex = buffer.position().toLong() + required - 1L
        if (lastIndex > Int.MAX_VALUE) {
            throw IllegalArgumentException("$label absolute index overflows")
        }
    }

    private fun requiredBytes(
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        elementSize: Int,
    ): Long {
        val rows = height - 1L
        val cols = width - 1L
        if (rows > 0 && rowStride > 0 && rows > Long.MAX_VALUE / rowStride) {
            throw IllegalArgumentException("row span overflows")
        }
        val rowSpan = rows * rowStride
        if (cols > 0 && pixelStride > 0 && cols > Long.MAX_VALUE / pixelStride) {
            throw IllegalArgumentException("column span overflows")
        }
        val colSpan = cols * pixelStride
        if (rowSpan > Long.MAX_VALUE - colSpan - elementSize) {
            throw IllegalArgumentException("plane span overflows")
        }
        return rowSpan + colSpan + elementSize
    }

    /** Absolute little-endian UINT16; ignores [ByteBuffer.order]. */
    private fun readUint16Le(buffer: ByteBuffer, index: Int): Int {
        val lo = buffer.get(index).toInt() and 0xFF
        val hi = buffer.get(index + 1).toInt() and 0xFF
        return lo or (hi shl 8)
    }

    private fun readUint8(buffer: ByteBuffer, index: Int): Int =
        buffer.get(index).toInt() and 0xFF

    private fun nearestRank(sorted: IntArray, n: Int, q: Double): Int {
        val index = ceil(q * n).toInt() - 1
        return sorted[index]
    }
}
