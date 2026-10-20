package com.triguide.ai.diagnostics

import java.io.IOException
import java.util.ArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED contract for [RawDepthPairScope.withFrame] same-Frame acquisition provenance.
 * CPU-only: generic [AutoCloseable] resources, no ARCore/Image types.
 */
class RawDepthPairScopeTest {

    private class MutableFrame(var timestampNs: Long)

    private class RecordingCloseable(
        private val label: String,
        private val closeLog: MutableList<String>,
        private val failOnClose: Boolean = false,
    ) : AutoCloseable {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount++
            closeLog.add(label)
            if (failOnClose) throw IOException("close_$label")
        }
    }

    @Test
    fun blockReceivesActiveProofSameFrameResourcesAndReturnValue() {
        val frame = MutableFrame(1_000_000_000L)
        val closeLog = ArrayList<String>()
        var seenDepth: RecordingCloseable? = null
        var seenConfidence: RecordingCloseable? = null
        var proofActiveInside = false

        val returned = RawDepthPairScope.withFrame(
            frame = frame,
            timestampOf = { it.timestampNs },
            acquireDepth = { f ->
                assertSame(frame, f)
                RecordingCloseable("depth", closeLog).also { seenDepth = it }
            },
            acquireConfidence = { f ->
                assertSame(frame, f)
                RecordingCloseable("confidence", closeLog).also { seenConfidence = it }
            },
            block = { proof, depth, confidence ->
                assertTrue(proof.isActive)
                proofActiveInside = true
                assertSame(seenDepth, depth)
                assertSame(seenConfidence, confidence)
                assertEquals(1_000_000_000L, proof.frameTimestampNs)
                "ok"
            },
        )

        assertEquals("ok", returned)
        assertTrue(proofActiveInside)
        assertEquals(listOf("confidence", "depth"), closeLog)
        assertEquals(1, seenDepth!!.closeCount)
        assertEquals(1, seenConfidence!!.closeCount)
    }

    @Test
    fun proofIsInvalidatedAfterBlockEvenOnSuccess() {
        val frame = MutableFrame(2_000_000_000L)
        var stolen: RawDepthPairProof? = null

        RawDepthPairScope.withFrame(
            frame = frame,
            timestampOf = { it.timestampNs },
            acquireDepth = { RecordingCloseable("depth", ArrayList()) },
            acquireConfidence = { RecordingCloseable("confidence", ArrayList()) },
            block = { proof, _, _ ->
                assertTrue(proof.isActive)
                stolen = proof
            },
        )

        assertFalse(stolen!!.isActive)
    }

    @Test
    fun nonPositiveFrameTimestampIsRejectedBeforeAcquire() {
        var depthAcquires = 0
        var confidenceAcquires = 0
        for (bad in listOf(0L, -1L, Long.MIN_VALUE)) {
            depthAcquires = 0
            confidenceAcquires = 0
            assertThrows(IllegalArgumentException::class.java) {
                RawDepthPairScope.withFrame(
                    frame = MutableFrame(bad),
                    timestampOf = { it.timestampNs },
                    acquireDepth = {
                        depthAcquires++
                        RecordingCloseable("depth", ArrayList())
                    },
                    acquireConfidence = {
                        confidenceAcquires++
                        RecordingCloseable("confidence", ArrayList())
                    },
                    block = { _, _, _ -> error("block must not run") },
                )
            }
            assertEquals(0, depthAcquires)
            assertEquals(0, confidenceAcquires)
        }
    }

    @Test
    fun changedFrameTimestampDuringAcquisitionRejectsWithoutBlockAndClosesConfidenceThenDepth() {
        // Bracket check: positive ts before acquires, same positive ts after both acquires.
        val frame = MutableFrame(3_000_000_000L)
        val closeLog = ArrayList<String>()
        var depth: RecordingCloseable? = null
        var confidence: RecordingCloseable? = null
        var blockRan = false

        assertThrows(IllegalStateException::class.java) {
            RawDepthPairScope.withFrame(
                frame = frame,
                timestampOf = { it.timestampNs },
                acquireDepth = {
                    depth = RecordingCloseable("depth", closeLog)
                    frame.timestampNs = 3_000_000_001L
                    depth!!
                },
                acquireConfidence = {
                    confidence = RecordingCloseable("confidence", closeLog)
                    confidence!!
                },
                block = { _, _, _ ->
                    blockRan = true
                },
            )
        }

        assertFalse(blockRan)
        assertEquals(listOf("confidence", "depth"), closeLog)
        assertEquals(1, depth!!.closeCount)
        assertEquals(1, confidence!!.closeCount)
    }

    @Test
    fun changedFrameTimestampDuringConfidenceAcquireRejectsAndClosesConfidenceThenDepth() {
        val frame = MutableFrame(4_000_000_000L)
        val closeLog = ArrayList<String>()
        var depth: RecordingCloseable? = null
        var confidence: RecordingCloseable? = null
        var blockRan = false

        assertThrows(IllegalStateException::class.java) {
            RawDepthPairScope.withFrame(
                frame = frame,
                timestampOf = { it.timestampNs },
                acquireDepth = {
                    depth = RecordingCloseable("depth", closeLog)
                    depth!!
                },
                acquireConfidence = {
                    confidence = RecordingCloseable("confidence", closeLog)
                    frame.timestampNs = 4_000_000_001L
                    confidence!!
                },
                block = { _, _, _ ->
                    blockRan = true
                },
            )
        }

        assertFalse(blockRan)
        assertEquals(listOf("confidence", "depth"), closeLog)
        assertEquals(1, depth!!.closeCount)
        assertEquals(1, confidence!!.closeCount)
    }

    @Test
    fun depthAcquireFailureDoesNotAcquireConfidenceOrInvokeBlock() {
        var confidenceAcquires = 0
        var blockRan = false

        assertThrows(IOException::class.java) {
            RawDepthPairScope.withFrame(
                frame = MutableFrame(5_000_000_000L),
                timestampOf = { it.timestampNs },
                acquireDepth = { throw IOException("depth_acquire") },
                acquireConfidence = {
                    confidenceAcquires++
                    RecordingCloseable("confidence", ArrayList())
                },
                block = { _, _, _ ->
                    blockRan = true
                },
            )
        }

        assertEquals(0, confidenceAcquires)
        assertFalse(blockRan)
    }

    @Test
    fun confidenceAcquireFailureClosesDepthOnceAndSkipsBlock() {
        val closeLog = ArrayList<String>()
        var depth: RecordingCloseable? = null
        var blockRan = false

        assertThrows(IOException::class.java) {
            RawDepthPairScope.withFrame(
                frame = MutableFrame(6_000_000_000L),
                timestampOf = { it.timestampNs },
                acquireDepth = {
                    depth = RecordingCloseable("depth", closeLog)
                    depth!!
                },
                acquireConfidence = { throw IOException("confidence_acquire") },
                block = { _, _, _ ->
                    blockRan = true
                },
            )
        }

        assertFalse(blockRan)
        assertEquals(listOf("depth"), closeLog)
        assertEquals(1, depth!!.closeCount)
    }

    @Test
    fun blockFailureStillClosesConfidenceThenDepthExactlyOnce() {
        val closeLog = ArrayList<String>()
        var depth: RecordingCloseable? = null
        var confidence: RecordingCloseable? = null
        var stolen: RawDepthPairProof? = null

        val thrown = assertThrows(IllegalStateException::class.java) {
            RawDepthPairScope.withFrame(
                frame = MutableFrame(7_000_000_000L),
                timestampOf = { it.timestampNs },
                acquireDepth = {
                    depth = RecordingCloseable("depth", closeLog)
                    depth!!
                },
                acquireConfidence = {
                    confidence = RecordingCloseable("confidence", closeLog)
                    confidence!!
                },
                block = { proof, _, _ ->
                    stolen = proof
                    assertTrue(proof.isActive)
                    throw IllegalStateException("process_failed")
                },
            )
        }

        assertEquals("process_failed", thrown.message)
        assertEquals(listOf("confidence", "depth"), closeLog)
        assertEquals(1, depth!!.closeCount)
        assertEquals(1, confidence!!.closeCount)
        assertFalse(stolen!!.isActive)
    }

    @Test
    fun confidenceCloseFailureStillClosesDepthExactlyOnce() {
        val closeLog = ArrayList<String>()
        var depth: RecordingCloseable? = null
        var confidence: RecordingCloseable? = null

        assertThrows(IOException::class.java) {
            RawDepthPairScope.withFrame(
                frame = MutableFrame(8_000_000_000L),
                timestampOf = { it.timestampNs },
                acquireDepth = {
                    depth = RecordingCloseable("depth", closeLog)
                    depth!!
                },
                acquireConfidence = {
                    confidence = RecordingCloseable("confidence", closeLog, failOnClose = true)
                    confidence!!
                },
                block = { _, _, _ -> Unit },
            )
        }

        assertEquals(listOf("confidence", "depth"), closeLog)
        assertEquals(1, depth!!.closeCount)
        assertEquals(1, confidence!!.closeCount)
    }

    @Test
    fun depthCloseFailurePropagatesAfterConfidenceClosedExactlyOnce() {
        val closeLog = ArrayList<String>()
        var depth: RecordingCloseable? = null
        var confidence: RecordingCloseable? = null

        assertThrows(IOException::class.java) {
            RawDepthPairScope.withFrame(
                frame = MutableFrame(9_000_000_000L),
                timestampOf = { it.timestampNs },
                acquireDepth = {
                    depth = RecordingCloseable("depth", closeLog, failOnClose = true)
                    depth!!
                },
                acquireConfidence = {
                    confidence = RecordingCloseable("confidence", closeLog)
                    confidence!!
                },
                block = { _, _, _ -> Unit },
            )
        }

        assertEquals(listOf("confidence", "depth"), closeLog)
        assertEquals(1, depth!!.closeCount)
        assertEquals(1, confidence!!.closeCount)
    }

    @Test
    fun successfulPathNeverDoubleClosesResources() {
        val closeLog = ArrayList<String>()
        val depth = RecordingCloseable("depth", closeLog)
        val confidence = RecordingCloseable("confidence", closeLog)

        RawDepthPairScope.withFrame(
            frame = MutableFrame(10_000_000_000L),
            timestampOf = { it.timestampNs },
            acquireDepth = { depth },
            acquireConfidence = { confidence },
            block = { _, _, _ -> Unit },
        )

        assertEquals(1, depth.closeCount)
        assertEquals(1, confidence.closeCount)
        assertEquals(listOf("confidence", "depth"), closeLog)
    }

    @Test
    fun frameObjectIdentityIsStableAcrossTimestampAndAcquireCallbacks() {
        val frame = MutableFrame(11_000_000_000L)
        val seen = ArrayList<Any>()

        RawDepthPairScope.withFrame(
            frame = frame,
            timestampOf = { f ->
                seen.add(f)
                f.timestampNs
            },
            acquireDepth = { f ->
                seen.add(f)
                RecordingCloseable("depth", ArrayList())
            },
            acquireConfidence = { f ->
                seen.add(f)
                RecordingCloseable("confidence", ArrayList())
            },
            block = { _, _, _ -> Unit },
        )

        assertTrue(seen.size >= 3)
        seen.forEach { assertSame(frame, it) }
    }
}
