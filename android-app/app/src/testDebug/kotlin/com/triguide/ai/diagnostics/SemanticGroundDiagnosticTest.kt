package com.triguide.ai.diagnostics

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for pure-CPU semantic + gravity-aligned depth ground diagnostic.
 * Primitive 8×8 fixtures only — no ARCore / SDK / Activity / temporal state.
 *
 * Helpers under test: [SemanticDepthCell], [SemanticGroundReading],
 * [SemanticGroundDiagnostic].
 *
 * Diagnostic only — `floor_like_candidate` is not confirmed floor, walkability,
 * clearance, or navigation authority. Thresholds are test assumptions only.
 */
class SemanticGroundDiagnosticTest {

    private companion object {
        const val GRID = 8
        const val CELL_COUNT = 64
        const val LABEL_FLOOR = 4
        const val LABEL_RUG = 29
        const val LABEL_WALL = 1
        const val LABEL_TABLE = 16
        const val LABEL_CHAIR = 20
        const val LABEL_OTHER = 0
        const val MIN_SUPPORTS = 6
        const val MIN_XZ_SPREAD_M = 0.20
        const val MIN_TRIANGLE_AREA_M2 = 0.01
        const val MAX_SUPPORT_Y_FROM_MEDIAN_M = 0.04
        const val RAISED_OR_BELOW_M = 0.08
        const val FLAT_RUG_ABS_M = 0.04
    }

    private fun cell(
        column: Int,
        row: Int,
        label: Int,
        worldX: Double? = null,
        worldY: Double? = null,
        worldZ: Double? = null,
    ): SemanticDepthCell =
        SemanticDepthCell(
            column = column,
            row = row,
            label = label,
            worldX = worldX,
            worldY = worldY,
            worldZ = worldZ,
        )

    /**
     * Exact 8×8 grid, every (column, row) in 0..7 once.
     * Default label [LABEL_OTHER], all three world coords null (missing depth OK).
     */
    private fun grid8x8(
        labelAt: (column: Int, row: Int) -> Int = { _, _ -> LABEL_OTHER },
        geometryAt: (column: Int, row: Int) -> Triple<Double?, Double?, Double?>? = { _, _ -> null },
    ): List<SemanticDepthCell> {
        val cells = ArrayList<SemanticDepthCell>(CELL_COUNT)
        for (row in 0 until GRID) {
            for (column in 0 until GRID) {
                val geom = geometryAt(column, row)
                cells += cell(
                    column = column,
                    row = row,
                    label = labelAt(column, row),
                    worldX = geom?.first,
                    worldY = geom?.second,
                    worldZ = geom?.third,
                )
            }
        }
        return cells
    }

    /** Flat floor patch rows [minRow..7] × cols [minCol..maxCol], gravity Y = [floorY]. */
    private fun flatFloorGeometry(
        column: Int,
        row: Int,
        minCol: Int = 2,
        maxCol: Int = 5,
        minRow: Int = 4,
        floorY: Double = 0.0,
        xStep: Double = 0.15,
        zStep: Double = 0.15,
    ): Triple<Double?, Double?, Double?>? {
        if (column !in minCol..maxCol || row !in minRow until GRID) return null
        return Triple(column * xStep, floorY, (GRID - 1 - row) * zStep)
    }

    private fun flatFloorLabels(
        column: Int,
        row: Int,
        minCol: Int = 2,
        maxCol: Int = 5,
        minRow: Int = 4,
    ): Int =
        if (column in minCol..maxCol && row in minRow until GRID) LABEL_FLOOR else LABEL_OTHER

    private fun validFlatFloorGrid(
        floorY: Double = 0.0,
        extraLabelAt: (column: Int, row: Int) -> Int? = { _, _ -> null },
        extraGeomAt: (column: Int, row: Int) -> Triple<Double?, Double?, Double?>? = { _, _ -> null },
    ): List<SemanticDepthCell> =
        grid8x8(
            labelAt = { column, row ->
                extraLabelAt(column, row) ?: flatFloorLabels(column, row)
            },
            geometryAt = { column, row ->
                extraGeomAt(column, row) ?: flatFloorGeometry(column, row, floorY = floorY)
            },
        )

    private fun assertRejected(
        reading: SemanticGroundReading,
        status: String,
        supportCount: Int = 0,
    ) {
        assertEquals(status, reading.status)
        assertNull(reading.referenceYMeters)
        assertEquals(supportCount, reading.supportCount)
        assertEquals(0, reading.raisedSamples)
        assertEquals(0, reading.flatRugSamples)
        assertEquals(0, reading.belowSamples)
        assertEquals(CELL_COUNT, reading.unknownSamples) // no reference leaves all grid heights unknown
    }

    // --- structure / geometry validity → invalid ---

    @Test
    fun emptyListIsInvalid() {
        assertRejected(SemanticGroundDiagnostic.evaluate(emptyList()), status = "invalid")
    }

    @Test
    fun tooManyCellsIsInvalid() {
        val cells = validFlatFloorGrid().toMutableList()
        cells += cell(column = 0, row = 0, label = LABEL_OTHER)
        assertEquals(CELL_COUNT + 1, cells.size)
        assertRejected(SemanticGroundDiagnostic.evaluate(cells), status = "invalid")
    }

    @Test
    fun tooFewCellsIsInvalid() {
        val cells = validFlatFloorGrid().dropLast(1)
        assertEquals(CELL_COUNT - 1, cells.size)
        assertRejected(SemanticGroundDiagnostic.evaluate(cells), status = "invalid")
    }

    @Test
    fun duplicateColumnRowIsInvalid() {
        val cells = validFlatFloorGrid().toMutableList()
        cells[0] = cell(column = 1, row = 1, label = LABEL_OTHER)
        cells[1] = cell(column = 1, row = 1, label = LABEL_WALL)
        assertRejected(SemanticGroundDiagnostic.evaluate(cells), status = "invalid")
    }

    @Test
    fun outOfRangeColumnIsInvalid() {
        val cells = validFlatFloorGrid().toMutableList()
        cells[0] = cell(column = 8, row = 0, label = LABEL_OTHER)
        // replace one in-range cell so size stays 64 but coords invalid / incomplete cover
        assertRejected(SemanticGroundDiagnostic.evaluate(cells), status = "invalid")
    }

    @Test
    fun outOfRangeRowIsInvalid() {
        val cells = validFlatFloorGrid().toMutableList()
        cells[10] = cell(column = 2, row = -1, label = LABEL_FLOOR)
        assertRejected(SemanticGroundDiagnostic.evaluate(cells), status = "invalid")
    }

    @Test
    fun outOfRangeLabelIsInvalid() {
        val cells = validFlatFloorGrid().toMutableList()
        cells[5] = cell(column = 0, row = 1, label = 32)
        assertRejected(SemanticGroundDiagnostic.evaluate(cells), status = "invalid")
    }

    @Test
    fun negativeLabelIsInvalid() {
        val cells = validFlatFloorGrid().toMutableList()
        cells[5] = cell(column = 0, row = 1, label = -1)
        assertRejected(SemanticGroundDiagnostic.evaluate(cells), status = "invalid")
    }

    @Test
    fun partialGeometryIsInvalid() {
        val cells = grid8x8(
            labelAt = { column, row -> flatFloorLabels(column, row) },
            geometryAt = { column, row ->
                if (column == 3 && row == 7) {
                    Triple(0.45, null, 0.0) // X+Z present, Y missing
                } else {
                    flatFloorGeometry(column, row)
                }
            },
        )
        assertRejected(SemanticGroundDiagnostic.evaluate(cells), status = "invalid")
    }

    @Test
    fun partialGeometryOnlyXIsInvalid() {
        val cells = grid8x8(
            geometryAt = { column, row ->
                if (column == 0 && row == 0) Triple(1.0, null, null) else null
            },
        )
        assertRejected(SemanticGroundDiagnostic.evaluate(cells), status = "invalid")
    }

    @Test
    fun nonFiniteSuppliedGeometryIsInvalid() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val cells = grid8x8(
                labelAt = { column, row -> flatFloorLabels(column, row) },
                geometryAt = { column, row ->
                    if (column == 3 && row == 7) {
                        Triple(0.45, bad, 0.0)
                    } else {
                        flatFloorGeometry(column, row)
                    }
                },
            )
            assertRejected(SemanticGroundDiagnostic.evaluate(cells), status = "invalid")
        }
    }

    @Test
    fun missingAllThreeCoordinatesOnEveryCellIsAllowedStructurally() {
        // No floor seeds → no_floor_component, not invalid
        val cells = grid8x8()
        assertRejected(
            SemanticGroundDiagnostic.evaluate(cells),
            status = "no_floor_component",
        )
    }

    // --- bottom-centre floor component ---

    @Test
    fun neitherBottomSeedFloorIsNoFloorComponent() {
        val cells = grid8x8(
            labelAt = { column, row ->
                // Floor exists beside seeds but not at columns 3/4 on bottom row
                if (row == 7 && column in 2..2) LABEL_FLOOR else LABEL_WALL
            },
            geometryAt = { column, row ->
                if (row == 7 && column == 2) Triple(0.3, 0.0, 0.0) else null
            },
        )
        assertRejected(
            SemanticGroundDiagnostic.evaluate(cells),
            status = "no_floor_component",
        )
    }

    @Test
    fun rugOnlyBottomSeedsCannotAnchorGround() {
        val cells = grid8x8(
            labelAt = { _, _ -> LABEL_RUG },
            geometryAt = { column, row ->
                Triple(column * 0.15, 0.0, (GRID - 1 - row) * 0.15)
            },
        )
        assertRejected(
            SemanticGroundDiagnostic.evaluate(cells),
            status = "no_floor_component",
        )
    }

    @Test
    fun tableOnlyBottomSeedsCannotAnchorGround() {
        val cells = grid8x8(
            labelAt = { column, row ->
                if (row >= 4 && column in 2..5) LABEL_TABLE else LABEL_WALL
            },
            geometryAt = { column, row ->
                if (row >= 4 && column in 2..5) {
                    Triple(column * 0.15, 0.5, (GRID - 1 - row) * 0.15)
                } else {
                    null
                }
            },
        )
        assertRejected(
            SemanticGroundDiagnostic.evaluate(cells),
            status = "no_floor_component",
        )
    }

    @Test
    fun wallLabelIsNeverFloorAnchor() {
        val cells = grid8x8(
            labelAt = { column, row ->
                if (row == 7 && column in 3..4) LABEL_WALL else flatFloorLabels(column, row)
            },
            geometryAt = { column, row -> flatFloorGeometry(column, row) },
        )
        // Seeds are wall; remaining floor patch is not seeded from bottom centre
        assertRejected(
            SemanticGroundDiagnostic.evaluate(cells),
            status = "no_floor_component",
        )
    }

    @Test
    fun disconnectedUpperFloorIslandIsIgnoredForSeeding() {
        // Bottom seeds are floor but only a tiny connected patch; large upper island
        // is four-connected-disconnected and must not rescue support count.
        val cells = grid8x8(
            labelAt = { column, row ->
                when {
                    row == 7 && column in 3..4 -> LABEL_FLOOR
                    row in 0..2 && column in 1..6 -> LABEL_FLOOR
                    else -> LABEL_OTHER
                }
            },
            geometryAt = { column, row ->
                when {
                    row == 7 && column in 3..4 ->
                        Triple(column * 0.15, 0.0, 0.0)
                    row in 0..2 && column in 1..6 ->
                        Triple(column * 0.15, 0.0, (GRID - 1 - row) * 0.15)
                    else -> null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("insufficient_support", reading.status)
        assertNull(reading.referenceYMeters)
        assertEquals(2, reading.supportCount)
        assertEquals(0, reading.raisedSamples)
    }

    @Test
    fun diagonalOnlyTouchDoesNotConnectFloorComponent() {
        // Seed (3,7) floor; neighbour (4,6) only touches diagonally — must stay excluded.
        val cells = grid8x8(
            labelAt = { column, row ->
                when {
                    column == 3 && row == 7 -> LABEL_FLOOR
                    column == 4 && row == 6 -> LABEL_FLOOR
                    column == 5 && row == 5 -> LABEL_FLOOR
                    column == 6 && row == 4 -> LABEL_FLOOR
                    column == 2 && row == 6 -> LABEL_FLOOR
                    column == 1 && row == 5 -> LABEL_FLOOR
                    else -> LABEL_OTHER
                }
            },
            geometryAt = { column, row ->
                when {
                    column == 3 && row == 7 -> Triple(0.45, 0.0, 0.0)
                    column == 4 && row == 6 -> Triple(0.60, 0.0, 0.15)
                    column == 5 && row == 5 -> Triple(0.75, 0.0, 0.30)
                    column == 6 && row == 4 -> Triple(0.90, 0.0, 0.45)
                    column == 2 && row == 6 -> Triple(0.30, 0.0, 0.15)
                    column == 1 && row == 5 -> Triple(0.15, 0.0, 0.30)
                    else -> null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("insufficient_support", reading.status)
        assertEquals(1, reading.supportCount)
        assertNull(reading.referenceYMeters)
    }

    // --- geometric gates on connected floor supports ---

    @Test
    fun sparseFewerThanSixFiniteSupportsIsInsufficient() {
        val cells = grid8x8(
            labelAt = { column, row ->
                if (row >= 5 && column in 3..4) LABEL_FLOOR else LABEL_OTHER
            },
            geometryAt = { column, row ->
                // 2 cols × 3 rows = 6 floor labels, but only 5 with geometry
                if (row >= 5 && column in 3..4) {
                    if (column == 4 && row == 5) {
                        null
                    } else {
                        Triple(column * 0.2, 0.0, (GRID - 1 - row) * 0.2)
                    }
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("insufficient_support", reading.status)
        assertEquals(5, reading.supportCount)
        assertNull(reading.referenceYMeters)
        assertTrue(reading.supportCount < MIN_SUPPORTS)
    }

    @Test
    fun narrowXSpreadIsInsufficient() {
        // Six+ supports, z spread OK, x spread < 0.20
        val cells = grid8x8(
            labelAt = { column, row ->
                if (column in 3..4 && row >= 2) LABEL_FLOOR else LABEL_OTHER
            },
            geometryAt = { column, row ->
                if (column in 3..4 && row >= 2) {
                    Triple(
                        0.50 + (column - 3) * 0.05, // x spread 0.05
                        0.0,
                        (GRID - 1 - row) * 0.15, // z spread 0.75
                    )
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("insufficient_support", reading.status)
        assertTrue(reading.supportCount >= MIN_SUPPORTS)
        assertNull(reading.referenceYMeters)
    }

    @Test
    fun narrowZSpreadIsInsufficient() {
        val cells = grid8x8(
            labelAt = { column, row ->
                if (row in 6..7 && column in 1..6) LABEL_FLOOR else LABEL_OTHER
            },
            geometryAt = { column, row ->
                if (row in 6..7 && column in 1..6) {
                    Triple(
                        column * 0.15, // x spread 0.75
                        0.0,
                        0.10 + (7 - row) * 0.05, // z spread 0.05
                    )
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("insufficient_support", reading.status)
        assertTrue(reading.supportCount >= MIN_SUPPORTS)
        assertNull(reading.referenceYMeters)
    }

    @Test
    fun collinearSupportsDespiteSpreadAreInsufficient() {
        // Diagonal line: x and z spreads >= 0.20 but triangle area == 0
        val cells = grid8x8(
            labelAt = { column, row ->
                // (3,7),(4,6),(5,5),(6,4),(2,6),(1,5) — wait, must be 4-connected from seeds
                // Build a 4-connected corridor along a world diagonal via axis steps
                when {
                    row == 7 && column in 3..4 -> LABEL_FLOOR
                    row == 6 && column in 3..4 -> LABEL_FLOOR
                    row == 5 && column in 4..5 -> LABEL_FLOOR
                    row == 4 && column in 5..6 -> LABEL_FLOOR
                    else -> LABEL_OTHER
                }
            },
            geometryAt = { column, row ->
                if (when {
                        row == 7 && column in 3..4 -> true
                        row == 6 && column in 3..4 -> true
                        row == 5 && column in 4..5 -> true
                        row == 4 && column in 5..6 -> true
                        else -> false
                    }
                ) {
                    // Map to collinear xz: z = x
                    val x = (column - 1) * 0.08
                    Triple(x, 0.0, x)
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("insufficient_support", reading.status)
        assertTrue(reading.supportCount >= MIN_SUPPORTS)
        assertTrue(reading.supportCount >= 6)
        // Spreads along the diagonal exceed 0.20 in both axes for these points
        assertNull(reading.referenceYMeters)
    }

    @Test
    fun noisySupportYDispersionIsRejectedWithoutDroppingOutliers() {
        val cells = validFlatFloorGrid(
            extraGeomAt = { column, row ->
                if (column == 2 && row == 4) {
                    // One support 0.05 m above the others — beyond 0.04 median band
                    Triple(column * 0.15, 0.05, (GRID - 1 - row) * 0.15)
                } else {
                    null // fall through to default flat floor geom in validFlatFloorGrid
                }
            },
        )
        // validFlatFloorGrid merges extraGeomAt before default — override works
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("insufficient_support", reading.status)
        assertTrue(reading.supportCount >= MIN_SUPPORTS)
        assertNull(
            "must not manufacture a floor by discarding the noisy support",
            reading.referenceYMeters,
        )
    }

    @Test
    fun slopedFloorExceedingMedianBandIsInsufficient() {
        val cells = grid8x8(
            labelAt = { column, row -> flatFloorLabels(column, row) },
            geometryAt = { column, row ->
                if (column in 2..5 && row in 4 until GRID) {
                    val y = (column - 2) * 0.03 // 0.00 .. 0.09 across supports
                    Triple(column * 0.15, y, (GRID - 1 - row) * 0.15)
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("insufficient_support", reading.status)
        assertNull(reading.referenceYMeters)
        assertTrue(reading.supportCount >= MIN_SUPPORTS)
    }

    @Test
    fun stairStepYPatternIsInsufficientNotFloorCandidate() {
        val cells = grid8x8(
            labelAt = { column, row -> flatFloorLabels(column, row) },
            geometryAt = { column, row ->
                if (column in 2..5 && row in 4 until GRID) {
                    val y = (7 - row) * 0.05 // steps 0, 0.05, 0.10, 0.15
                    Triple(column * 0.15, y, (GRID - 1 - row) * 0.15)
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("insufficient_support", reading.status)
        assertNull(reading.referenceYMeters)
    }

    // --- happy path + height buckets ---

    @Test
    fun validFlatFloorIsFloorLikeCandidateWithMedianReferenceOnly() {
        val cells = validFlatFloorGrid(floorY = -1.25)
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        assertEquals(-1.25, reading.referenceYMeters!!, 0.0)
        assertTrue(reading.supportCount >= MIN_SUPPORTS)
        assertEquals(0, reading.raisedSamples)
        assertEquals(0, reading.flatRugSamples)
        assertEquals(0, reading.belowSamples)
        // Missing-depth non-floor cells count as unknown
        assertTrue(reading.unknownSamples > 0)
    }

    @Test
    fun exactlySixSupportsWithSpreadAndAreaPass() {
        // Minimal 2×3 patch at bottom centre with non-collinear xz
        val cells = grid8x8(
            labelAt = { column, row ->
                if (column in 3..4 && row in 5..7) LABEL_FLOOR else LABEL_OTHER
            },
            geometryAt = { column, row ->
                if (column in 3..4 && row in 5..7) {
                    Triple(
                        (column - 3) * 0.20, // exact 0..0.20, avoids translated subtraction rounding below threshold
                        0.0,
                        (GRID - 1 - row) * 0.20, // z: 0..0.40 spread 0.40
                    )
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        assertEquals(6, reading.supportCount)
        assertEquals(0.0, reading.referenceYMeters!!, 0.0)
        assertEquals(MIN_SUPPORTS, reading.supportCount)
        assertTrue(MIN_XZ_SPREAD_M <= 0.20)
        assertTrue(MIN_TRIANGLE_AREA_M2 <= 0.01)
    }

    @Test
    fun raisedObjectAboveReferenceCountsRaised() {
        val cells = validFlatFloorGrid(
            floorY = 0.0,
            extraLabelAt = { column, row ->
                if (column == 0 && row == 3) LABEL_CHAIR else null
            },
            extraGeomAt = { column, row ->
                if (column == 0 && row == 3) {
                    Triple(-0.5, 0.12, 1.0) // +0.12 > 0.08 above ref
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        assertEquals(0.0, reading.referenceYMeters!!, 0.0)
        assertEquals(1, reading.raisedSamples)
        assertEquals(0, reading.belowSamples)
        assertEquals(0, reading.flatRugSamples)
    }

    @Test
    fun belowGroundSampleCountsBelow() {
        val cells = validFlatFloorGrid(
            floorY = 0.0,
            extraLabelAt = { column, row ->
                if (column == 7 && row == 0) LABEL_OTHER else null
            },
            extraGeomAt = { column, row ->
                if (column == 7 && row == 0) {
                    Triple(1.2, -0.12, 2.0) // -0.12 < -0.08
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        assertEquals(1, reading.belowSamples)
        assertEquals(0, reading.raisedSamples)
    }

    @Test
    fun flatRugNearReferenceCountsFlatRugNotAnchor() {
        val cells = validFlatFloorGrid(
            floorY = 0.0,
            extraLabelAt = { column, row ->
                if (column == 1 && row == 3) LABEL_RUG else null
            },
            extraGeomAt = { column, row ->
                if (column == 1 && row == 3) {
                    Triple(0.1, 0.03, 0.8) // |Δ| = 0.03 ≤ 0.04
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        assertEquals(1, reading.flatRugSamples)
        assertEquals(0, reading.raisedSamples)
        // Rug must not have been used as a support anchor
        assertTrue(reading.supportCount >= MIN_SUPPORTS)
    }

    @Test
    fun foldedRugRaisedAboveBandCountsRaisedNotFlat() {
        val cells = validFlatFloorGrid(
            floorY = 0.0,
            extraLabelAt = { column, row ->
                if (column == 1 && row == 3) LABEL_RUG else null
            },
            extraGeomAt = { column, row ->
                if (column == 1 && row == 3) {
                    Triple(0.1, 0.12, 0.8) // folded / trip edge
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        assertEquals(1, reading.raisedSamples)
        assertEquals(0, reading.flatRugSamples)
    }

    @Test
    fun uncertainHeightBandCountsUnknown() {
        val cells = validFlatFloorGrid(
            floorY = 0.0,
            extraLabelAt = { column, row ->
                if (column == 7 && row == 2) LABEL_CHAIR else null
            },
            extraGeomAt = { column, row ->
                if (column == 7 && row == 2) {
                    Triple(1.0, 0.05, 0.5) // 0.04 < |Δ| ≤ 0.08 → unknown
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        assertEquals(0, reading.raisedSamples)
        assertEquals(0, reading.belowSamples)
        assertTrue(reading.unknownSamples >= 1)
    }

    @Test
    fun rugInUncertainBandCountsUnknownNotFlat() {
        val cells = validFlatFloorGrid(
            floorY = 0.0,
            extraLabelAt = { column, row ->
                if (column == 1 && row == 2) LABEL_RUG else null
            },
            extraGeomAt = { column, row ->
                if (column == 1 && row == 2) {
                    Triple(0.1, 0.06, 0.7) // 0.04 < |Δ| ≤ 0.08
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        assertEquals(0, reading.flatRugSamples)
        assertEquals(0, reading.raisedSamples)
        assertTrue(reading.unknownSamples >= 1)
    }

    @Test
    fun missingDepthOnNonAnchorCountsUnknown() {
        val cells = validFlatFloorGrid(floorY = 0.0)
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        // 64 - supportCount cells lack geometry (or are non-anchors without depth)
        assertEquals(CELL_COUNT - reading.supportCount, reading.unknownSamples)
        assertEquals(0, reading.raisedSamples)
        assertEquals(0, reading.flatRugSamples)
        assertEquals(0, reading.belowSamples)
    }

    @Test
    fun supportMedianUsesAllSupportsWithoutOutlierDrop() {
        // Even support count with two Y levels still inside ±0.04 of median
        val cells = grid8x8(
            labelAt = { column, row ->
                if (column in 3..4 && row in 5..7) LABEL_FLOOR else LABEL_OTHER
            },
            geometryAt = { column, row ->
                if (column in 3..4 && row in 5..7) {
                    val y = if (column == 3) 0.0 else 0.02
                    Triple((column - 3) * 0.20, y, (GRID - 1 - row) * 0.20)
                } else {
                    null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        assertEquals(6, reading.supportCount)
        // Sorted Y: 0,0,0,0.02,0.02,0.02 → median avg of 3rd+4th = 0.01
        assertEquals(0.01, reading.referenceYMeters!!, 1e-12)
    }

    // --- purity / wording / status contract ---

    @Test
    fun permutationOfValidGridPreservesReading() {
        val base = validFlatFloorGrid(floorY = 0.25)
        val shuffled = base.shuffled(Random(7))
        assertEquals(
            SemanticGroundDiagnostic.evaluate(base),
            SemanticGroundDiagnostic.evaluate(shuffled),
        )
    }

    @Test
    fun repeatedEvaluateIsStatelessAndEqual() {
        val cells = validFlatFloorGrid(
            floorY = 0.0,
            extraLabelAt = { column, row ->
                if (column == 0 && row == 0) LABEL_RUG else null
            },
            extraGeomAt = { column, row ->
                if (column == 0 && row == 0) Triple(-1.0, 0.01, 0.0) else null
            },
        )
        val first = SemanticGroundDiagnostic.evaluate(cells)
        val second = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals(first, second)
        assertEquals("floor_like_candidate", first.status)
        assertEquals(1, first.flatRugSamples)
    }

    @Test
    fun readingNeverClaimsSafeClearWalkableOrConfirmedFloor() {
        val readings = listOf(
            SemanticGroundDiagnostic.evaluate(emptyList()),
            SemanticGroundDiagnostic.evaluate(grid8x8()),
            SemanticGroundDiagnostic.evaluate(validFlatFloorGrid()),
            SemanticGroundDiagnostic.evaluate(
                validFlatFloorGrid(
                    extraLabelAt = { c, r -> if (c == 0 && r == 0) LABEL_CHAIR else null },
                    extraGeomAt = { c, r ->
                        if (c == 0 && r == 0) Triple(0.0, 0.2, 0.0) else null
                    },
                ),
            ),
        )
        for (reading in readings) {
            assertFalse(reading.status.equals("floor", ignoreCase = true))
            assertFalse(reading.status.equals("safe", ignoreCase = true))
            assertFalse(reading.status.contains("clear", ignoreCase = true))
            assertFalse(reading.status.contains("walkable", ignoreCase = true))
            assertFalse(reading.status.contains("path", ignoreCase = true))
            val blob = reading.toString()
            assertFalse(blob.contains("confidence", ignoreCase = true))
            assertFalse(blob.contains("navigat", ignoreCase = true))
        }
        assertEquals("invalid", readings[0].status)
        assertEquals("no_floor_component", readings[1].status)
        assertEquals("floor_like_candidate", readings[2].status)
        assertEquals("floor_like_candidate", readings[3].status)
        assertEquals(1, readings[3].raisedSamples)
    }

    @Test
    fun oneBottomSeedFloorIsEnoughToStartComponent() {
        val cells = grid8x8(
            labelAt = { column, row ->
                when {
                    column == 3 && row == 7 -> LABEL_FLOOR
                    column == 3 && row == 6 -> LABEL_FLOOR
                    column == 3 && row == 5 -> LABEL_FLOOR
                    column == 4 && row == 5 -> LABEL_FLOOR
                    column == 5 && row == 5 -> LABEL_FLOOR
                    column == 5 && row == 4 -> LABEL_FLOOR
                    else -> LABEL_OTHER
                }
            },
            geometryAt = { column, row ->
                when {
                    column == 3 && row in 5..7 ->
                        Triple(0.45, 0.0, (GRID - 1 - row) * 0.25)
                    column in 4..5 && row == 5 ->
                        Triple(column * 0.20, 0.0, 0.50)
                    column == 5 && row == 4 ->
                        Triple(1.00, 0.0, 0.75)
                    else -> null
                }
            },
        )
        val reading = SemanticGroundDiagnostic.evaluate(cells)
        assertEquals("floor_like_candidate", reading.status)
        assertEquals(6, reading.supportCount)
        assertEquals(0.0, reading.referenceYMeters!!, 0.0)
    }
}
