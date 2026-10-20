/*
 * Copyright 2017 Google LLC
 * Copyright 2026 TriGuide-AI contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * OES camera-background draw pattern adapted from the official ARCore SDK
 * v1.57.0 shared_camera sample BackgroundRenderer.java and its screenquad
 * shader pattern:
 * https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/shared_camera_java/app/src/main/java/com/google/ar/core/examples/java/common/rendering/BackgroundRenderer.java
 *
 * TriGuide changes: injectable [CameraBackgroundGl] for JVM tests, UV
 * invalidation + finite/full checks (no stale UVs), forget-on-new-context /
 * clear-before-delete lifecycle, no texture ownership, no depth visualization
 * or shared-camera framework, no asset loads. Activity supplies the OES
 * texture id and Frame.transformCoordinates2d via [transformUv].
 */

package com.triguide.ai.diagnostics

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Minimal GL backend for [DiagnosticCameraBackground]. CPU tests inject a fake
 * with zero Android GL calls; [GlesCameraBackgroundGl] runs only on a thread
 * that already has a current GL context.
 */
internal interface CameraBackgroundGl {
    fun createProgram(): Int
    fun draw(
        program: Int,
        textureId: Int,
        positions: FloatBuffer,
        texCoords: FloatBuffer,
    )
    fun deleteProgram(program: Int)
}

/**
 * Debug-only camera background: NDC quad + caller-provided TEXTURE_NORMALIZED
 * UVs, drawn with an OES external sampler. Does not create or own the camera
 * texture. Diagnostic preview only — never navigation authority.
 */
internal class DiagnosticCameraBackground(
    private val gl: CameraBackgroundGl = GlesCameraBackgroundGl(),
) {
    private var program: Int = 0

    private val positions: FloatBuffer =
        ByteBuffer.allocateDirect(FLOATS_PER_QUAD * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    private val texCoords: FloatBuffer =
        ByteBuffer.allocateDirect(FLOATS_PER_QUAD * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    init {
        positions.put(QUAD_COORDS)
        positions.position(0)
        invalidateTexCoords()
    }

    /**
     * New GL context. Forgets any prior program handle without deleting it
     * (handles are invalid in the new context). State is cleared to 0 before
     * [CameraBackgroundGl.createProgram] so a create failure leaves draw disabled.
     */
    fun onSurfaceCreated() {
        program = 0
        val created = gl.createProgram()
        if (created <= 0) {
            throw IllegalStateException("camera background createProgram returned $created")
        }
        program = created
    }

    /**
     * @return false before init / after release, or when timestamp, texture id,
     * or UV payload is unusable; true after a successful GL draw.
     */
    fun draw(
        frameTimestampNs: Long,
        textureId: Int,
        transformUv: (FloatBuffer, FloatBuffer) -> Unit,
    ): Boolean {
        if (program <= 0) return false
        if (frameTimestampNs <= 0L) return false
        if (textureId <= 0) return false

        positions.clear()
        positions.put(QUAD_COORDS)
        positions.position(0)

        invalidateTexCoords()
        texCoords.position(0)

        transformUv(positions, texCoords)

        if (!texCoordsAreCompleteAndFinite()) {
            invalidateTexCoords()
            return false
        }

        positions.position(0)
        texCoords.position(0)
        gl.draw(program, textureId, positions, texCoords)
        return true
    }

    /**
     * Clears local state before deleting the GL program so a delete failure
     * cannot leave a drawable handle and cannot double-delete on retry.
     */
    fun releaseOnGlThread() {
        val toDelete = program
        program = 0
        invalidateTexCoords()
        if (toDelete > 0) {
            gl.deleteProgram(toDelete)
        }
    }

    private fun invalidateTexCoords() {
        texCoords.clear()
        for (i in 0 until FLOATS_PER_QUAD) {
            texCoords.put(Float.NaN)
        }
        texCoords.position(0)
    }

    private fun texCoordsAreCompleteAndFinite(): Boolean {
        val count = FLOATS_PER_QUAD
        if (texCoords.remaining() >= count) {
            val start = texCoords.position()
            for (i in 0 until count) {
                if (!texCoords.get(start + i).isFinite()) return false
            }
            return true
        }
        // transformCoordinates2d-style writers may advance position to 8.
        if (texCoords.position() >= count && texCoords.capacity() >= count) {
            for (i in 0 until count) {
                if (!texCoords.get(i).isFinite()) return false
            }
            return true
        }
        return false
    }

    companion object {
        private const val BYTES_PER_FLOAT = 4
        private const val FLOATS_PER_QUAD = 8

        /** Google BackgroundRenderer 2D NDC triangle-strip corners. */
        private val QUAD_COORDS: FloatArray =
            floatArrayOf(
                -1.0f, -1.0f,
                -1.0f, +1.0f,
                +1.0f, -1.0f,
                +1.0f, +1.0f,
            )
    }
}

/**
 * GLES2 implementation of the shared_camera OES background program. Call only
 * with a current GL context. Does not generate or delete the OES texture.
 */
internal class GlesCameraBackgroundGl : CameraBackgroundGl {
    private var positionAttrib: Int = -1
    private var texCoordAttrib: Int = -1
    private var textureUniform: Int = -1
    private val depthMask = BooleanArray(1)

    override fun createProgram(): Int {
        positionAttrib = -1
        texCoordAttrib = -1
        textureUniform = -1

        var vertexShader = 0
        var fragmentShader = 0
        var program = 0
        try {
            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

            program = GLES20.glCreateProgram()
            if (program == 0) {
                throw IllegalStateException("glCreateProgram failed: ${glErrorHex()}")
            }
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val log = GLES20.glGetProgramInfoLog(program)
                throw IllegalStateException("camera background program link failed: $log")
            }

            positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
            texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
            textureUniform = GLES20.glGetUniformLocation(program, "sTexture")
            if (positionAttrib < 0 || texCoordAttrib < 0 || textureUniform < 0) {
                throw IllegalStateException(
                    "camera background locations missing: a_Position=$positionAttrib " +
                        "a_TexCoord=$texCoordAttrib sTexture=$textureUniform",
                )
            }

            checkGlError("createProgram")
            return program
        } catch (error: Throwable) {
            if (program != 0) {
                GLES20.glDeleteProgram(program)
            }
            positionAttrib = -1
            texCoordAttrib = -1
            textureUniform = -1
            throw error
        } finally {
            if (vertexShader != 0) {
                GLES20.glDeleteShader(vertexShader)
            }
            if (fragmentShader != 0) {
                GLES20.glDeleteShader(fragmentShader)
            }
        }
    }

    override fun draw(
        program: Int,
        textureId: Int,
        positions: FloatBuffer,
        texCoords: FloatBuffer,
    ) {
        if (program <= 0) {
            throw IllegalStateException("draw with non-positive program")
        }
        if (textureId <= 0) {
            throw IllegalStateException("draw with non-positive textureId")
        }
        if (positionAttrib < 0 || texCoordAttrib < 0 || textureUniform < 0) {
            throw IllegalStateException("draw before successful createProgram")
        }

        val depthTestEnabled = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST)
        GLES20.glGetBooleanv(GLES20.GL_DEPTH_WRITEMASK, depthMask, 0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        try {
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(textureUniform, 0)

            positions.position(0)
            GLES20.glVertexAttribPointer(
                positionAttrib,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                0,
                positions,
            )
            GLES20.glEnableVertexAttribArray(positionAttrib)

            texCoords.position(0)
            GLES20.glVertexAttribPointer(
                texCoordAttrib,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                0,
                texCoords,
            )
            GLES20.glEnableVertexAttribArray(texCoordAttrib)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)

            checkGlError("draw")
        } finally {
            GLES20.glDisableVertexAttribArray(positionAttrib)
            GLES20.glDisableVertexAttribArray(texCoordAttrib)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            GLES20.glUseProgram(0)
            GLES20.glDepthMask(depthMask[0])
            if (depthTestEnabled) {
                GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            } else {
                GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            }
        }
    }

    override fun deleteProgram(program: Int) {
        if (program > 0) {
            GLES20.glDeleteProgram(program)
        }
        positionAttrib = -1
        texCoordAttrib = -1
        textureUniform = -1
        checkGlError("deleteProgram")
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            throw IllegalStateException("glCreateShader($type) failed: ${glErrorHex()}")
        }
        try {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            check(compiled[0] == GLES20.GL_TRUE) {
                "shader compile failed (type=$type): ${GLES20.glGetShaderInfoLog(shader)}"
            }
            return shader
        } catch (error: Throwable) {
            GLES20.glDeleteShader(shader)
            throw error
        }
    }

    private fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            throw IllegalStateException("GL error after $op: 0x${Integer.toHexString(error)}")
        }
    }

    private fun glErrorHex(): String = "0x${Integer.toHexString(GLES20.glGetError())}"

    companion object {
        private const val COORDS_PER_VERTEX = 2
        private const val VERTEX_COUNT = 4

        // screenquad-style attributes; samplerExternalOES background (shared_camera).
        private const val VERTEX_SHADER =
            "attribute vec4 a_Position;\n" +
                "attribute vec2 a_TexCoord;\n" +
                "varying vec2 v_TexCoord;\n" +
                "void main() {\n" +
                "  gl_Position = a_Position;\n" +
                "  v_TexCoord = a_TexCoord;\n" +
                "}\n"

        private const val FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 v_TexCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "  gl_FragColor = texture2D(sTexture, v_TexCoord);\n" +
                "}\n"
    }
}
