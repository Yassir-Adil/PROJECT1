package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeDepthModelContractTest {
    @Test
    fun missingAssetCannotEnterBenchmark() {
        val result = RelativeDepthModelContract.preflight(
            assetPresent = false,
            observedSha256Hex = null,
        )

        assertEquals(RelativeDepthModelReadiness.NOT_BUNDLED, result.readiness)
        assertFalse(result.mayEnterBenchmark)
    }

    @Test
    fun malformedHashCannotEnterBenchmark() {
        val result = RelativeDepthModelContract.preflight(
            assetPresent = true,
            observedSha256Hex = "not-a-sha256",
        )

        assertEquals(RelativeDepthModelReadiness.INVALID_METADATA, result.readiness)
        assertFalse(result.mayEnterBenchmark)
    }

    @Test
    fun wrongHashFailsClosed() {
        val result = RelativeDepthModelContract.preflight(
            assetPresent = true,
            observedSha256Hex = "0".repeat(64),
        )

        assertEquals(RelativeDepthModelReadiness.HASH_MISMATCH, result.readiness)
        assertFalse(result.mayEnterBenchmark)
    }

    @Test
    fun pinnedHashEnablesBenchmarkOnly() {
        val result = RelativeDepthModelContract.preflight(
            assetPresent = true,
            observedSha256Hex = "  ${RelativeDepthModelContract.EXPECTED_SHA256_HEX.uppercase()}  ",
        )

        assertEquals(RelativeDepthModelReadiness.READY_FOR_BENCHMARK, result.readiness)
        assertTrue(result.mayEnterBenchmark)
        assertTrue(RelativeDepthModelContract.REVIEW_NOTE.contains("cannot establish clearance"))
    }

    @Test
    fun contractPinsExpectedTensorShapeAndPreprocessing() {
        assertEquals(256, RelativeDepthModelContract.INPUT_WIDTH)
        assertEquals(256, RelativeDepthModelContract.INPUT_HEIGHT)
        assertEquals(3, RelativeDepthModelContract.INPUT_CHANNELS)
        assertEquals(1, RelativeDepthModelContract.OUTPUT_CHANNELS)
        assertEquals("relative_inverse_depth", RelativeDepthModelContract.OUTPUT_SEMANTICS)
        assertEquals(3, RelativeDepthModelContract.INPUT_MEAN.size)
        assertEquals(3, RelativeDepthModelContract.INPUT_STD.size)
    }
}
