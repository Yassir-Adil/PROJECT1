package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticGroundUnknownTest {
    @Test fun noGroundReferenceDoesNotReportZeroUnknownHeightSamples() {
        val missing = List(64) { SemanticDepthCell(it % 8, it / 8, 0) }
        val reading = SemanticGroundDiagnostic.evaluate(missing)
        assertNull(reading.referenceYMeters)
        assertEquals(64, reading.unknownSamples)
        assertEquals(0, reading.raisedSamples + reading.flatRugSamples + reading.belowSamples)
    }
}
