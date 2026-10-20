package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticPaintGridTest {
    @Test fun oversizedDepthListIsRejectedBeforeCopyOrIteration() {
        val oversized = object : AbstractList<SemanticDepthCell>() {
            override val size = 65
            override fun get(index: Int): SemanticDepthCell = error("Oversized list must not be read")
        }
        assertTrue(SemanticPaintGrid.fromDepth(oversized).isEmpty())
    }
    @Test fun syntheticPatternHasExactly64TypedPaintCellsAndOnlyLegendLabels() {
        val cells = SemanticPaintGrid.synthetic()
        assertEquals(64, cells.size)
        assertEquals(64, cells.map { it.column to it.row }.toSet().size)
        assertTrue(SemanticPaintGrid.valid(cells))
        assertEquals(setOf(1, 4, 29), cells.map { it.label }.toSet())
    }

    @Test fun colourPalettePreservesLiveStyleAndDoesNotInventSafetyGreen() {
        assertEquals(0xff5faaff.toInt(), SemanticPaintGrid.colour(1))
        assertEquals(0xffaf91ff.toInt(), SemanticPaintGrid.colour(4))
        assertEquals(0xfff5be46.toInt(), SemanticPaintGrid.colour(29))
        for (label in listOf(0, 2, 31, -1, 32)) assertNull(SemanticPaintGrid.colour(label))
    }

    @Test fun malformedGeometryOrLabelsCannotBecomePaintGrid() {
        val good = SemanticPaintGrid.synthetic()
        assertFalse(SemanticPaintGrid.valid(emptyList()))
        assertFalse(SemanticPaintGrid.valid(good.dropLast(1)))
        assertFalse(SemanticPaintGrid.valid(good + good.first()))
        assertFalse(SemanticPaintGrid.valid(good.toMutableList().apply { set(0, get(1)) }))
        for (bad in listOf(SemanticPaintCell(-1,0,1), SemanticPaintCell(0,8,1), SemanticPaintCell(0,0,32))) {
            assertFalse(SemanticPaintGrid.valid(good.toMutableList().apply { set(0,bad) }))
        }
    }

    @Test fun depthProjectionCopiesOnlyLabelsAndPreservesUnknowns() {
        val input = (0 until 64).map { SemanticDepthCell(it%8,it/8,0,1.0,2.0,3.0) }
        val paint = SemanticPaintGrid.fromDepth(input)
        assertEquals(64, paint.size)
        assertTrue(paint.all { it.label == 0 })
        assertTrue(SemanticPaintGrid.valid(paint))
        assertTrue(SemanticPaintGrid.fromDepth(input.dropLast(1)).isEmpty())
    }
}
