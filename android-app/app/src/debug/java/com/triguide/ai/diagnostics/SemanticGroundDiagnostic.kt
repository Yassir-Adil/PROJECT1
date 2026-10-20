package com.triguide.ai.diagnostics

import kotlin.math.abs

/**
 * One cell of an 8×8 semantic + optional gravity-aligned world sample.
 *
 * Missing all three world coordinates means missing depth. Partial triples are
 * rejected by [SemanticGroundDiagnostic]. Labels follow ADE20K Top-31 IDs;
 * only floor (`4`) may anchor a diagnostic ground candidate.
 */
internal data class SemanticDepthCell(
    val column: Int,
    val row: Int,
    val label: Int,
    val worldX: Double? = null,
    val worldY: Double? = null,
    val worldZ: Double? = null,
)

/**
 * Single-frame diagnostic ground reading.
 *
 * `floor_like_candidate` is not confirmed floor, walkability, clearance, or
 * navigation authority. Thresholds are diagnostic assumptions only.
 */
internal data class SemanticGroundReading(
    val status: String,
    val referenceYMeters: Double?,
    val supportCount: Int,
    val raisedSamples: Int,
    val flatRugSamples: Int,
    val belowSamples: Int,
    val unknownSamples: Int,
)

/**
 * Pure / stateless semantic + geometry ground diagnostic on a fixed 8×8 grid.
 * No SDK types, temporal confirmation, confidence, or navigation output.
 */
internal object SemanticGroundDiagnostic {
    const val GRID: Int = 8
    const val CELL_COUNT: Int = GRID * GRID
    const val LABEL_FLOOR: Int = 4
    const val LABEL_RUG: Int = 29
    const val MIN_SUPPORTS: Int = 6
    const val MIN_XZ_SPREAD_M: Double = 0.20
    const val MIN_TRIANGLE_AREA_M2: Double = 0.01
    const val MAX_SUPPORT_Y_FROM_MEDIAN_M: Double = 0.04
    const val RAISED_OR_BELOW_M: Double = 0.08
    const val FLAT_RUG_ABS_M: Double = 0.04

    private const val STATUS_INVALID = "invalid"
    private const val STATUS_NO_FLOOR_COMPONENT = "no_floor_component"
    private const val STATUS_INSUFFICIENT_SUPPORT = "insufficient_support"
    private const val STATUS_FLOOR_LIKE_CANDIDATE = "floor_like_candidate"

    private val SEED_COLUMNS: IntArray = intArrayOf(3, 4)
    private const val SEED_ROW: Int = GRID - 1
    private val NEIGHBOR_DR: IntArray = intArrayOf(-1, 0, 1, 0)
    private val NEIGHBOR_DC: IntArray = intArrayOf(0, 1, 0, -1)

    fun evaluate(cells: List<SemanticDepthCell>): SemanticGroundReading {
        if (cells.size != CELL_COUNT) {
            return rejected(STATUS_INVALID)
        }

        val grid = arrayOfNulls<SemanticDepthCell>(CELL_COUNT)
        val occupied = BooleanArray(CELL_COUNT)
        val hasGeometry = BooleanArray(CELL_COUNT)
        val xs = DoubleArray(CELL_COUNT)
        val ys = DoubleArray(CELL_COUNT)
        val zs = DoubleArray(CELL_COUNT)

        for (cell in cells) {
            val column = cell.column
            val row = cell.row
            val label = cell.label
            if (column !in 0 until GRID || row !in 0 until GRID || label !in 0..31) {
                return rejected(STATUS_INVALID)
            }
            val index = indexOf(column, row)
            if (occupied[index]) {
                return rejected(STATUS_INVALID)
            }
            when (val geom = classifyGeometry(cell)) {
                GeometryClass.Missing -> Unit
                GeometryClass.PartialOrNonFinite -> return rejected(STATUS_INVALID)
                is GeometryClass.Finite -> {
                    hasGeometry[index] = true
                    xs[index] = geom.x
                    ys[index] = geom.y
                    zs[index] = geom.z
                }
            }
            occupied[index] = true
            grid[index] = cell
        }

        val inComponent = BooleanArray(CELL_COUNT)
        val queue = ArrayDeque<Int>()
        for (seedColumn in SEED_COLUMNS) {
            val seedIndex = indexOf(seedColumn, SEED_ROW)
            val seed = grid[seedIndex] ?: return rejected(STATUS_INVALID)
            if (seed.label == LABEL_FLOOR && !inComponent[seedIndex]) {
                inComponent[seedIndex] = true
                queue.addLast(seedIndex)
            }
        }
        if (queue.isEmpty()) {
            return rejected(STATUS_NO_FLOOR_COMPONENT)
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val column = current % GRID
            val row = current / GRID
            for (dir in 0 until 4) {
                val nextColumn = column + NEIGHBOR_DC[dir]
                val nextRow = row + NEIGHBOR_DR[dir]
                if (nextColumn !in 0 until GRID || nextRow !in 0 until GRID) continue
                val nextIndex = indexOf(nextColumn, nextRow)
                if (inComponent[nextIndex]) continue
                val neighbor = grid[nextIndex] ?: continue
                if (neighbor.label != LABEL_FLOOR) continue
                inComponent[nextIndex] = true
                queue.addLast(nextIndex)
            }
        }

        val supportIndices = ArrayList<Int>(CELL_COUNT)
        for (index in 0 until CELL_COUNT) {
            if (inComponent[index] && hasGeometry[index]) {
                supportIndices += index
            }
        }
        val supportCount = supportIndices.size

        if (supportCount < MIN_SUPPORTS) {
            return rejected(STATUS_INSUFFICIENT_SUPPORT, supportCount = supportCount)
        }

        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        val supportYs = DoubleArray(supportCount)
        for (i in 0 until supportCount) {
            val index = supportIndices[i]
            val x = xs[index]
            val y = ys[index]
            val z = zs[index]
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
                return rejected(STATUS_INSUFFICIENT_SUPPORT, supportCount = supportCount)
            }
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
            supportYs[i] = y
        }

        val xSpread = maxX - minX
        val zSpread = maxZ - minZ
        if (!xSpread.isFinite() || !zSpread.isFinite() ||
            xSpread < MIN_XZ_SPREAD_M || zSpread < MIN_XZ_SPREAD_M
        ) {
            return rejected(STATUS_INSUFFICIENT_SUPPORT, supportCount = supportCount)
        }

        if (!hasNonCollinearXzCoverage(supportIndices, xs, zs)) {
            return rejected(STATUS_INSUFFICIENT_SUPPORT, supportCount = supportCount)
        }

        supportYs.sort()
        val medianY = medianSorted(supportYs)
        if (!medianY.isFinite()) {
            return rejected(STATUS_INSUFFICIENT_SUPPORT, supportCount = supportCount)
        }
        for (y in supportYs) {
            val delta = y - medianY
            if (!delta.isFinite() || abs(delta) > MAX_SUPPORT_Y_FROM_MEDIAN_M) {
                return rejected(STATUS_INSUFFICIENT_SUPPORT, supportCount = supportCount)
            }
        }

        val isSupport = BooleanArray(CELL_COUNT)
        for (index in supportIndices) {
            isSupport[index] = true
        }

        var raised = 0
        var flatRug = 0
        var below = 0
        var unknown = 0
        for (index in 0 until CELL_COUNT) {
            if (isSupport[index]) continue
            if (!hasGeometry[index]) {
                unknown++
                continue
            }
            val delta = ys[index] - medianY
            if (!delta.isFinite()) {
                unknown++
                continue
            }
            when {
                delta > RAISED_OR_BELOW_M -> raised++
                delta < -RAISED_OR_BELOW_M -> below++
                grid[index]!!.label == LABEL_RUG && abs(delta) <= FLAT_RUG_ABS_M -> flatRug++
                else -> unknown++
            }
        }

        return SemanticGroundReading(
            status = STATUS_FLOOR_LIKE_CANDIDATE,
            referenceYMeters = medianY,
            supportCount = supportCount,
            raisedSamples = raised,
            flatRugSamples = flatRug,
            belowSamples = below,
            unknownSamples = unknown,
        )
    }

    private fun indexOf(column: Int, row: Int): Int = row * GRID + column

    private fun rejected(
        status: String,
        supportCount: Int = 0,
    ): SemanticGroundReading =
        SemanticGroundReading(
            status = status,
            referenceYMeters = null,
            supportCount = supportCount,
            raisedSamples = 0,
            flatRugSamples = 0,
            belowSamples = 0,
            unknownSamples = CELL_COUNT, // without a reference, every grid height remains unknown
        )

    private sealed class GeometryClass {
        data object Missing : GeometryClass()
        data object PartialOrNonFinite : GeometryClass()
        data class Finite(val x: Double, val y: Double, val z: Double) : GeometryClass()
    }

    private fun classifyGeometry(cell: SemanticDepthCell): GeometryClass {
        val x = cell.worldX
        val y = cell.worldY
        val z = cell.worldZ
        val present = (if (x != null) 1 else 0) + (if (y != null) 1 else 0) + (if (z != null) 1 else 0)
        return when (present) {
            0 -> GeometryClass.Missing
            3 -> {
                if (!x!!.isFinite() || !y!!.isFinite() || !z!!.isFinite()) {
                    GeometryClass.PartialOrNonFinite
                } else {
                    GeometryClass.Finite(x, y, z)
                }
            }
            else -> GeometryClass.PartialOrNonFinite
        }
    }

    private fun medianSorted(sortedYs: DoubleArray): Double {
        val n = sortedYs.size
        val mid = n / 2
        return if (n % 2 == 1) {
            sortedYs[mid]
        } else {
            (sortedYs[mid - 1] + sortedYs[mid]) / 2.0
        }
    }

    /**
     * True when some triple of supports has xz triangle area >= [MIN_TRIANGLE_AREA_M2].
     * Rejects non-finite shoelace results rather than treating them as coverage.
     */
    private fun hasNonCollinearXzCoverage(
        supportIndices: List<Int>,
        xs: DoubleArray,
        zs: DoubleArray,
    ): Boolean {
        val n = supportIndices.size
        for (i in 0 until n - 2) {
            val iIndex = supportIndices[i]
            val xi = xs[iIndex]
            val zi = zs[iIndex]
            for (j in i + 1 until n - 1) {
                val jIndex = supportIndices[j]
                val xj = xs[jIndex]
                val zj = zs[jIndex]
                for (k in j + 1 until n) {
                    val kIndex = supportIndices[k]
                    val xk = xs[kIndex]
                    val zk = zs[kIndex]
                    val cross = (xj - xi) * (zk - zi) - (xk - xi) * (zj - zi)
                    if (!cross.isFinite()) continue
                    val area = 0.5 * abs(cross)
                    if (area.isFinite() && area >= MIN_TRIANGLE_AREA_M2) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
