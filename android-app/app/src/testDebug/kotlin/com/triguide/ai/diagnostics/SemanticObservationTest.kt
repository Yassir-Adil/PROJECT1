package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticObservationTest {
    private fun cells() = (0 until 64).map { SemanticPaintCell(it%8,it/8,4) }

    @Test fun rgbOnlyObservationHasNoGroundOrWorldGeometryAuthority() {
        val observation = Semantic2DObservation(1,100,2,200,cells(),0)
        assertEquals("rgb_only",observation.kind)
        assertEquals(64,observation.paintCells.size)
        assertFalse((observation as Any) is SemanticFusionResult)
        val fieldNames = Semantic2DObservation::class.java.declaredFields.map { it.name }
        assertFalse(fieldNames.any { it.contains("ground",true) || it.startsWith("world") })
    }

    @Test fun callerMutationCannotAlterPublishedRgbOnlyGrid() {
        val input = cells().toMutableList()
        val observation = Semantic2DObservation(1,100,2,200,input,0)
        input[0] = SemanticPaintCell(0,0,1)
        assertEquals(4,observation.paintCells.first().label)
    }

    @Test fun malformedMetadataOrGeometryNeverBecomeRgbOnlyEvidence() {
        assertThrows(IllegalArgumentException::class.java) { Semantic2DObservation(-1,100,2,200,cells(),0) }
        assertThrows(IllegalArgumentException::class.java) { Semantic2DObservation(1,-1,2,200,cells(),0) }
        assertThrows(IllegalArgumentException::class.java) { Semantic2DObservation(1,100,0,200,cells(),0) }
        assertThrows(IllegalArgumentException::class.java) { Semantic2DObservation(1,100,2,99,cells(),0) }
        assertThrows(IllegalArgumentException::class.java) { Semantic2DObservation(1,100,2,200,cells(),65) }
        assertThrows(IllegalArgumentException::class.java) { Semantic2DObservation(1,100,2,200,cells().dropLast(1),0) }
    }

    @Test fun fusionKeepsGroundContractAndSharesOnlyTypedPaintView() {
        val depth = (0 until 64).map { SemanticDepthCell(it%8,it/8,4) }
        val ground = SemanticGroundDiagnostic.evaluate(depth)
        val observation: SemanticObservation = SemanticFusionResult(1,100,2,depth,ground,0,200)
        assertEquals("raw_fused",observation.kind)
        assertEquals(64,observation.paintCells.size)
        assertNull((observation as SemanticFusionResult).ground.referenceYMeters)
        assertEquals(64,observation.ground.unknownSamples)
    }
}
