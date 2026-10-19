package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticSurfacesTest {
    @Test fun wallWithEmptyDetectorNeverAddsNothingAheadWording() {
        val line = DiagnosticSurfaces.combinedDescription(sample(),
            com.triguide.ai.domain.SceneLine("Nothing obvious ahead.", "لا شيء واضح أمامك."), false)
        assertTrue(line.english.contains("wall"))
        assertTrue(line.english.contains("no usable labels"))
        assertFalse(line.english.contains("Nothing obvious"))
        assertFalse(line.arabic.contains("لا شيء واضح"))
    }
    private fun map(id: Int = DiagnosticSurfaces.WALL) = IntArray(512 * 512) { id }
    private fun sample(pixels: IntArray = map(), width: Int = 512, height: Int = 512, time: Long = 100) =
        DiagnosticSurfaces.sample(pixels, width, height, false, time)

    @Test fun knownClassIdsMatchExistingPinnedTaxonomy() {
        assertEquals("wall", Ade20kTop31ClassIds.labelName(DiagnosticSurfaces.WALL))
        assertEquals("floor", Ade20kTop31ClassIds.labelName(DiagnosticSurfaces.FLOOR))
        assertEquals("rug", Ade20kTop31ClassIds.labelName(DiagnosticSurfaces.RUG))
    }
    @Test fun uniformWallRetainsSourceDimensionsAndBoundedGrid() {
        val result = sample(width = 408, height = 636)
        assertEquals(408, result.sourceWidth); assertEquals(636, result.sourceHeight)
        assertEquals(64 * 64, result.cells.size); assertEquals(1.0, result.fraction(1), 0.0)
    }
    @Test fun portraitLetterboxPaddingIsExcluded() {
        val pixels = map(0)
        for (y in 0 until 512) for (x in 128 until 384) pixels[y * 512 + x] = DiagnosticSurfaces.FLOOR
        assertEquals(1.0, sample(pixels, 100, 200).fraction(DiagnosticSurfaces.FLOOR), 0.0)
    }
    @Test fun landscapeLetterboxPaddingIsExcluded() {
        val pixels = map(0)
        for (y in 128 until 384) for (x in 0 until 512) pixels[y * 512 + x] = DiagnosticSurfaces.RUG
        assertEquals(1.0, sample(pixels, 200, 100).fraction(DiagnosticSurfaces.RUG), 0.0)
    }
    @Test fun leftRightAndMirrorMetadataArePreservedWithoutDoubleMirror() {
        val pixels = IntArray(512 * 512) { if (it % 512 < 256) 1 else 4 }
        val result = DiagnosticSurfaces.sample(pixels, 512, 512, true, 100)
        assertTrue(result.mirrored); assertEquals(1, result.cells[0]); assertEquals(4, result.cells[63])
    }
    @Test fun ageBoundaryStopAndFutureAreRejectedFailClosed() {
        val surface = sample()
        assertNotNull(DiagnosticSurfaces.fresh(surface, 1100, true))
        assertNull(DiagnosticSurfaces.fresh(surface, 1101, true))
        assertNull(DiagnosticSurfaces.fresh(surface, 99, true))
        assertNull(DiagnosticSurfaces.fresh(surface, 100, false))
        assertNull(DiagnosticSurfaces.fresh(null, 100, true))
    }
    @Test fun unsupportedAndUnknownClassesDoNotBecomeSurfaceNames() {
        val unknown = DiagnosticSurfaces.description(sample(map(0)))
        assertEquals("" to "", unknown)
        assertEquals("" to "", DiagnosticSurfaces.description(sample(map(20))))
    }
    @Test fun rugDescriptionNeverClaimsFlatOrTraversable() {
        val text = DiagnosticSurfaces.description(sample(map(DiagnosticSurfaces.RUG)))
        assertTrue(text.first.contains("edge height is not measured"))
        assertTrue(text.second.contains("ارتفاع حافتها غير مقاس"))
        listOf(text.first, text.second).forEach { line ->
            for (word in listOf("safe", "flat", "walkable", "آمن", "مستوية", "امش", "turn")) assertFalse(line.contains(word, true))
        }
    }
    @Test fun floorExplicitlyDoesNotExcludeSteps() {
        assertTrue(DiagnosticSurfaces.description(sample(map(4))).first.contains("steps are not ruled out"))
    }
    @Test fun minoritySpecklesDoNotProduceAConfidentSurfaceNarrative() {
        val pixels = map(0); pixels[0] = 1
        assertEquals("" to "", DiagnosticSurfaces.description(sample(pixels)))
    }
    @Test(expected = IllegalArgumentException::class) fun malformedMapIsRejected() { sample(IntArray(1)) }
    @Test(expected = IllegalArgumentException::class) fun invalidClassIsRejected() { sample(map(32)) }
    @Test(expected = IllegalArgumentException::class) fun invalidSourceSizeIsRejected() { sample(width = 0) }
}
