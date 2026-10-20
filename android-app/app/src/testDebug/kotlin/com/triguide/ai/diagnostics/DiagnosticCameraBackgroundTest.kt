package com.triguide.ai.diagnostics

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.ArrayList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED contract for [DiagnosticCameraBackground] with an injected fake
 * [CameraBackgroundGl]. Pure JVM: zero Android / GLES calls.
 *
 * Source (`DiagnosticCameraBackground.kt`, `GlesCameraBackgroundGl`) is not
 * implemented in this phase; Codex records compile RED before release.
 */
class DiagnosticCameraBackgroundTest {

    private class FakeCameraBackgroundGl(
        private val programSequence: MutableList<Int> = mutableListOf(11, 22, 33, 44),
        private val failCreateWith: (() -> Throwable)? = null,
        private val nonPositiveProgram: Int? = null,
        private val failDrawWith: (() -> Throwable)? = null,
    ) : CameraBackgroundGl {
        val createCalls = ArrayList<Int>()
        val deleteCalls = ArrayList<Int>()
        val drawCalls = ArrayList<DrawCall>()
        var nextProgramIndex = 0

        data class DrawCall(
            val program: Int,
            val textureId: Int,
            val positions: FloatArray,
            val texCoords: FloatArray,
            val positionsBufferIdentity: FloatBuffer,
            val texCoordsBufferIdentity: FloatBuffer,
            val positionsPosition: Int,
            val texCoordsPosition: Int,
        )

        override fun createProgram(): Int {
            failCreateWith?.invoke()?.let { throw it }
            nonPositiveProgram?.let { return it }
            val program = programSequence[nextProgramIndex++]
            createCalls.add(program)
            return program
        }

        override fun draw(
            program: Int,
            textureId: Int,
            positions: FloatBuffer,
            texCoords: FloatBuffer,
        ) {
            failDrawWith?.invoke()?.let { throw it }
            drawCalls.add(
                DrawCall(
                    program = program,
                    textureId = textureId,
                    positions = snapshot(positions, 8),
                    texCoords = snapshot(texCoords, 8),
                    positionsBufferIdentity = positions,
                    texCoordsBufferIdentity = texCoords,
                    positionsPosition = positions.position(),
                    texCoordsPosition = texCoords.position(),
                ),
            )
        }

        override fun deleteProgram(program: Int) {
            deleteCalls.add(program)
        }

        private fun snapshot(buffer: FloatBuffer, count: Int): FloatArray {
            val out = FloatArray(count)
            val saved = buffer.position()
            buffer.position(0)
            buffer.get(out)
            buffer.position(saved)
            return out
        }
    }

    /** Standard Google BackgroundRenderer 2D NDC triangle-strip corners. */
    private val expectedNdcQuad = floatArrayOf(
        -1.0f, -1.0f,
        -1.0f, +1.0f,
        +1.0f, -1.0f,
        +1.0f, +1.0f,
    )

    private fun writeUv(dst: FloatBuffer, values: FloatArray) {
        require(values.size == 8)
        dst.clear()
        dst.put(values)
        dst.flip()
    }

    private fun identityUv(): (FloatBuffer, FloatBuffer) -> Unit = { src, dst ->
        assertArrayEquals(expectedNdcQuad, snapshotFloats(src, 8), 0.0f)
        writeUv(
            dst,
            floatArrayOf(
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 1.0f,
                1.0f, 0.0f,
            ),
        )
    }

    private fun snapshotFloats(buffer: FloatBuffer, count: Int): FloatArray {
        val out = FloatArray(count)
        val saved = buffer.position()
        buffer.position(0)
        buffer.get(out)
        buffer.position(saved)
        return out
    }

    private fun background(gl: FakeCameraBackgroundGl = FakeCameraBackgroundGl()): DiagnosticCameraBackground =
        DiagnosticCameraBackground(gl)

    @Test
    fun drawReturnsFalseBeforeOnSurfaceCreated() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)

        assertFalse(bg.draw(1_000_000L, textureId = 7, transformUv = identityUv()))
        assertTrue(gl.createCalls.isEmpty())
        assertTrue(gl.drawCalls.isEmpty())
        assertTrue(gl.deleteCalls.isEmpty())
    }

    @Test
    fun onSurfaceCreatedCreatesPositiveProgram() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)

        bg.onSurfaceCreated()

        assertEquals(listOf(11), gl.createCalls)
        assertTrue(gl.deleteCalls.isEmpty())
        assertTrue(
            bg.draw(1L, textureId = 3, transformUv = identityUv()),
        )
        assertEquals(1, gl.drawCalls.size)
        assertEquals(11, gl.drawCalls[0].program)
        assertEquals(3, gl.drawCalls[0].textureId)
    }

    @Test
    fun onSurfaceCreatedRecreateForgetsOldHandleWithoutDeletingInNewContext() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)

        bg.onSurfaceCreated()
        assertEquals(listOf(11), gl.createCalls)

        // New EGL/GL context: previous program handle is invalid there.
        bg.onSurfaceCreated()

        assertEquals(listOf(11, 22), gl.createCalls)
        assertTrue(
            "old program must not be deleted inside a new GL context",
            gl.deleteCalls.isEmpty(),
        )
        assertTrue(bg.draw(2L, textureId = 5, transformUv = identityUv()))
        assertEquals(22, gl.drawCalls.single().program)
    }

    @Test
    fun onSurfaceCreatedThrowsWhenCreateProgramReturnsNonPositive() {
        for (bad in listOf(0, -1, Int.MIN_VALUE)) {
            val gl = FakeCameraBackgroundGl(nonPositiveProgram = bad)
            val bg = background(gl)
            assertThrows(IllegalStateException::class.java) {
                bg.onSurfaceCreated()
            }
            assertTrue(gl.drawCalls.isEmpty())
            assertTrue(gl.deleteCalls.isEmpty())
        }
    }

    @Test
    fun onSurfaceCreatedPropagatesCreateProgramFailure() {
        val boom = RuntimeException("shader_link_failed")
        val gl = FakeCameraBackgroundGl(failCreateWith = { boom })
        val bg = background(gl)

        val thrown = assertThrows(RuntimeException::class.java) {
            bg.onSurfaceCreated()
        }
        assertSame(boom, thrown)
        assertTrue(gl.drawCalls.isEmpty())
        assertTrue(gl.deleteCalls.isEmpty())
    }

    @Test
    fun drawRejectsNonPositiveFrameTimestampWithoutGlDraw() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()

        for (bad in listOf(0L, -1L, Long.MIN_VALUE)) {
            gl.drawCalls.clear()
            assertFalse(bg.draw(bad, textureId = 4, transformUv = identityUv()))
            assertTrue(gl.drawCalls.isEmpty())
        }
    }

    @Test
    fun drawRejectsNonPositiveTextureIdWithoutGlDraw() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()

        for (bad in listOf(0, -1, Int.MIN_VALUE)) {
            gl.drawCalls.clear()
            assertFalse(bg.draw(10L, textureId = bad, transformUv = identityUv()))
            assertTrue(gl.drawCalls.isEmpty())
        }
    }

    @Test
    fun drawPassesFourNdcCornersInGoogleTriangleStripOrder() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()

        var seenAtTransform: FloatArray? = null
        assertTrue(
            bg.draw(100L, textureId = 9) { src, dst ->
                seenAtTransform = snapshotFloats(src, 8)
                writeUv(
                    dst,
                    floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f),
                )
            },
        )

        assertArrayEquals(expectedNdcQuad, seenAtTransform, 0.0f)
        assertArrayEquals(expectedNdcQuad, gl.drawCalls.single().positions, 0.0f)
    }

    @Test
    fun drawReusesSameDirectBuffersAndRewindsBeforeTransformAndDraw() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()

        val positionsSeen = ArrayList<FloatBuffer>()
        val texSeen = ArrayList<FloatBuffer>()

        repeat(2) { index ->
            assertTrue(
                bg.draw((index + 1).toLong(), textureId = 2) { src, dst ->
                    assertTrue(src.isDirect)
                    assertTrue(dst.isDirect)
                    assertEquals(0, src.position())
                    assertEquals(0, dst.position())
                    positionsSeen.add(src)
                    texSeen.add(dst)
                    writeUv(
                        dst,
                        floatArrayOf(
                            index.toFloat(), 0f,
                            index.toFloat(), 1f,
                            index.toFloat() + 0.5f, 0f,
                            index.toFloat() + 0.5f, 1f,
                        ),
                    )
                    // Leave cursors away from zero; implementation must rewind before GL draw.
                    src.position(src.capacity())
                    dst.position(dst.capacity())
                },
            )
        }

        assertEquals(2, positionsSeen.size)
        assertSame(positionsSeen[0], positionsSeen[1])
        assertSame(texSeen[0], texSeen[1])
        assertNotEquals(
            "positions and texCoords must be distinct buffers",
            positionsSeen[0],
            texSeen[0],
        )

        for (call in gl.drawCalls) {
            assertSame(positionsSeen[0], call.positionsBufferIdentity)
            assertSame(texSeen[0], call.texCoordsBufferIdentity)
            assertEquals(0, call.positionsPosition)
            assertEquals(0, call.texCoordsPosition)
        }
        assertArrayEquals(
            floatArrayOf(0f, 0f, 0f, 1f, 0.5f, 0f, 0.5f, 1f),
            gl.drawCalls[0].texCoords,
            0.0f,
        )
        assertArrayEquals(
            floatArrayOf(1f, 0f, 1f, 1f, 1.5f, 0f, 1.5f, 1f),
            gl.drawCalls[1].texCoords,
            0.0f,
        )
    }

    @Test
    fun changedUvTransformsReachGlDrawEachFrame() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()

        val first = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f)
        val second = floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f)

        assertTrue(
            bg.draw(1L, 8) { _, dst -> writeUv(dst, first) },
        )
        assertTrue(
            bg.draw(2L, 8) { _, dst -> writeUv(dst, second) },
        )

        assertEquals(2, gl.drawCalls.size)
        assertArrayEquals(first, gl.drawCalls[0].texCoords, 0.0f)
        assertArrayEquals(second, gl.drawCalls[1].texCoords, 0.0f)
    }

    @Test
    fun incompleteUvWriteIsNotDrawnAndDoesNotReusePriorUvs() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()

        val good = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f)
        assertTrue(bg.draw(1L, 6) { _, dst -> writeUv(dst, good) })
        assertEquals(1, gl.drawCalls.size)

        assertFalse(
            bg.draw(2L, 6) { _, dst ->
                dst.clear()
                dst.put(0.5f)
                dst.put(0.5f)
                dst.put(0.5f)
                dst.put(0.5f)
                dst.flip()
            },
        )
        assertEquals(
            "incomplete UV write must not call GL draw",
            1,
            gl.drawCalls.size,
        )
        assertArrayEquals(good, gl.drawCalls[0].texCoords, 0.0f)

        val next = floatArrayOf(0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f)
        assertTrue(bg.draw(3L, 6) { _, dst -> writeUv(dst, next) })
        assertEquals(2, gl.drawCalls.size)
        assertArrayEquals(next, gl.drawCalls[1].texCoords, 0.0f)
    }

    @Test
    fun nonfiniteUvsAreNotDrawnAndAreNeverReused() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()

        val good = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f)
        assertTrue(bg.draw(1L, 2) { _, dst -> writeUv(dst, good) })

        for (bad in listOf(
            floatArrayOf(Float.NaN, 0f, 0f, 1f, 1f, 0f, 1f, 1f),
            floatArrayOf(0f, Float.POSITIVE_INFINITY, 0f, 1f, 1f, 0f, 1f, 1f),
            floatArrayOf(0f, 0f, 0f, 1f, 1f, Float.NEGATIVE_INFINITY, 1f, 1f),
        )) {
            gl.drawCalls.clear()
            // Keep one prior successful draw recorded separately; clear only rejects.
            assertFalse(bg.draw(10L, 2) { _, dst -> writeUv(dst, bad) })
            assertTrue(gl.drawCalls.isEmpty())
        }

        // Recover with a fresh finite transform; must not silently reuse the first UVs
        // without invoking transform (transform must run and supply the new values).
        var transformRuns = 0
        val recovered = floatArrayOf(0.11f, 0.22f, 0.33f, 0.44f, 0.55f, 0.66f, 0.77f, 0.88f)
        assertTrue(
            bg.draw(11L, 2) { _, dst ->
                transformRuns++
                writeUv(dst, recovered)
            },
        )
        assertEquals(1, transformRuns)
        assertEquals(1, gl.drawCalls.size)
        assertArrayEquals(recovered, gl.drawCalls[0].texCoords, 0.0f)
    }

    @Test
    fun transformUvErrorPropagatesWithoutGlDraw() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()
        val boom = IllegalArgumentException("transform_failed")

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            bg.draw(5L, 3) { _, _ -> throw boom }
        }
        assertSame(boom, thrown)
        assertTrue(gl.drawCalls.isEmpty())
    }

    @Test
    fun glDrawErrorPropagatesToCaller() {
        val boom = IllegalStateException("gl_draw_failed")
        val gl = FakeCameraBackgroundGl(failDrawWith = { boom })
        val bg = background(gl)
        bg.onSurfaceCreated()

        val thrown = assertThrows(IllegalStateException::class.java) {
            bg.draw(5L, 3, transformUv = identityUv())
        }
        assertSame(boom, thrown)
    }

    @Test
    fun releaseDeletesCurrentProgramExactlyOnceAndDisablesDraw() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()
        assertTrue(bg.draw(1L, 4, transformUv = identityUv()))

        bg.releaseOnGlThread()

        assertEquals(listOf(11), gl.deleteCalls)
        assertFalse(bg.draw(2L, 4, transformUv = identityUv()))
        assertEquals(1, gl.drawCalls.size)
        assertEquals(listOf(11), gl.deleteCalls)
    }

    @Test
    fun releaseIsIdempotent() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()

        bg.releaseOnGlThread()
        bg.releaseOnGlThread()
        bg.releaseOnGlThread()

        assertEquals(listOf(11), gl.deleteCalls)
        assertFalse(bg.draw(9L, 1, transformUv = identityUv()))
        assertTrue(gl.drawCalls.isEmpty())
    }

    @Test
    fun releaseWithoutInitIsSafeAndIdempotent() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)

        bg.releaseOnGlThread()
        bg.releaseOnGlThread()

        assertTrue(gl.deleteCalls.isEmpty())
        assertTrue(gl.createCalls.isEmpty())
        assertFalse(bg.draw(1L, 1, transformUv = identityUv()))
    }

    @Test
    fun reInitAfterReleaseCreatesNewProgramAndDrawsAgain() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)

        bg.onSurfaceCreated()
        assertTrue(bg.draw(1L, 7, transformUv = identityUv()))
        bg.releaseOnGlThread()
        assertEquals(listOf(11), gl.deleteCalls)
        assertFalse(bg.draw(2L, 7, transformUv = identityUv()))

        // New context after release.
        bg.onSurfaceCreated()
        assertEquals(listOf(11, 22), gl.createCalls)
        assertEquals(
            "re-init must not delete the already-released handle again",
            listOf(11),
            gl.deleteCalls,
        )
        assertTrue(bg.draw(3L, 7, transformUv = identityUv()))
        assertEquals(22, gl.drawCalls.last().program)
        assertEquals(2, gl.drawCalls.size)
    }

    @Test
    fun recreateThenReleaseDeletesOnlyLatestProgramHandle() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)

        bg.onSurfaceCreated() // 11
        bg.onSurfaceCreated() // 22, forget 11 without delete
        assertTrue(gl.deleteCalls.isEmpty())

        bg.releaseOnGlThread()
        assertEquals(listOf(22), gl.deleteCalls)

        bg.releaseOnGlThread()
        assertEquals(listOf(22), gl.deleteCalls)
    }

    @Test
    fun directBuffersHaveNativeByteOrderAndCapacityForFourCorners() {
        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()

        bg.draw(1L, 5) { src, dst ->
            assertTrue(src.isDirect)
            assertTrue(dst.isDirect)
            assertEquals(ByteOrder.nativeOrder(), src.order())
            assertEquals(ByteOrder.nativeOrder(), dst.order())
            assertTrue(src.capacity() >= 8)
            assertTrue(dst.capacity() >= 8)
            writeUv(dst, floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f))
        }
        assertEquals(1, gl.drawCalls.size)
    }

    @Test
    fun successfulDrawDoesNotAllocateFreshBuffersEachFrame() {
        // Contract aid: force direct allocation pattern visible to tests via identity.
        val probe = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer()
        assertTrue(probe.isDirect)

        val gl = FakeCameraBackgroundGl()
        val bg = background(gl)
        bg.onSurfaceCreated()

        lateinit var firstPos: FloatBuffer
        lateinit var firstTex: FloatBuffer
        assertTrue(
            bg.draw(1L, 1) { src, dst ->
                firstPos = src
                firstTex = dst
                writeUv(dst, floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f))
            },
        )
        assertTrue(
            bg.draw(2L, 1) { src, dst ->
                assertSame(firstPos, src)
                assertSame(firstTex, dst)
                writeUv(dst, floatArrayOf(1f, 1f, 1f, 0f, 0f, 1f, 0f, 0f))
            },
        )
    }
}
