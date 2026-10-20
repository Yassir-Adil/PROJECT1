package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticRgbGridTest {
    private fun centres() = (0 until 64).map { (it%8 + .5)/8 to (it/8 + .5)/8 }
    private fun map() = IntArray(512*512) { 4 }

    @Test fun validRgbOnlyGridNeedsNoDepthAndPreservesAll64Labels() {
        val result = SemanticRgbGrid.decode(640,480,0,centres(),map())
        assertEquals(64,result.cells.size)
        assertTrue(result.cells.all { it.label == 4 })
        assertEquals(0,result.unmappedCells)
    }

    @Test fun allQuarterTurnsUseSameIntegerOrientationAndLetterboxAsModelInput() {
        val points = centres()
        val labels = IntArray(512*512) { it%32 }
        for (turn in 0..3) {
            val result = SemanticRgbGrid.decode(640,480,turn,points,labels)
            points.forEachIndexed { index, point ->
                val oriented = SemanticImageOrientation.pixel(640,480,point.first,point.second,turn)!!
                val expected = SemanticFusionMapping.modelIndex(oriented.width,oriented.height,
                    oriented.x.toDouble()/oriented.width,oriented.y.toDouble()/oriented.height)!!
                assertEquals(labels[expected],result.cells[index].label)
                assertEquals(index%8,result.cells[index].column)
                assertEquals(index/8,result.cells[index].row)
            }
        }
    }

    @Test fun invalidSdkCoordinatesStayUnknownAndNeverClampIntoImage() {
        val points = centres().toMutableList()
        points[0] = Double.NaN to .5
        points[1] = 1.0 to .5
        points[2] = -.1 to .5
        points[3] = .5 to Double.POSITIVE_INFINITY
        val result = SemanticRgbGrid.decode(640,480,1,points,map())
        assertEquals(4,result.unmappedCells)
        assertTrue(result.cells.take(4).all { it.label == 0 })
        assertTrue(result.cells.drop(4).all { it.label == 4 })
    }

    @Test fun invalidShapesDimensionsTurnsAndClassIdsAreRejected() {
        for (dimensions in listOf(0 to 480,640 to -1,Int.MAX_VALUE to Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                SemanticRgbGrid.decode(dimensions.first,dimensions.second,0,centres(),map())
            }
        }
        assertThrows(IllegalArgumentException::class.java) { SemanticRgbGrid.decode(640,480,4,centres(),map()) }
        assertThrows(IllegalArgumentException::class.java) { SemanticRgbGrid.decode(640,480,0,centres().dropLast(1),map()) }
        assertThrows(IllegalArgumentException::class.java) { SemanticRgbGrid.decode(640,480,0,centres(),IntArray(2)) }
        assertThrows(IllegalArgumentException::class.java) { SemanticRgbGrid.decode(640,480,0,centres(),IntArray(512*512){32}) }
    }

    @Test fun oversizedSdkGridIsRejectedBeforeReadingAnyPoint() {
        val oversized = object : AbstractList<Pair<Double,Double>>() {
            override val size = 65
            override fun get(index: Int): Pair<Double,Double> = error("Must bound points first")
        }
        assertThrows(IllegalArgumentException::class.java) { SemanticRgbGrid.decode(640,480,0,oversized,map()) }
    }
}
