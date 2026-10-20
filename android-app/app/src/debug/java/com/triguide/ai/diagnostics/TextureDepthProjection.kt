/*
 * Copyright 2021 Google LLC
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
 * Scalar depth unprojection formula adapted from the official ARCore SDK
 * v1.57.0 raw_depth_java sample PointCloudHelper.java (texture intrinsics
 * scaled to the depth image; integer depth pixel with no +0.5 offset;
 * OpenGL camera +X right, +Y up, −Z forward):
 * https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/raw_depth_java/app/src/main/java/com/google/ar/core/examples/java/rawdepth/PointCloudHelper.java
 *
 * TriGuide changes: pure JVM scalars only (no SDK types, Image buffers, world
 * pose, or point-cloud assembly); strict positive dimension / Long aspect /
 * depth-area cap / finite-positive scaled focal gates; reject non-finite
 * results without an arbitrary magnitude bound. Diagnostic geometry only —
 * never CLEAR, walkability, or navigation authority.
 */

package com.triguide.ai.diagnostics

/**
 * Current physical-camera **unrotated GPU texture** intrinsics plus the depth
 * frame size they are scaled onto. [fx]/[fy]/[cx]/[cy] are in texture pixels.
 * No CPU-image intrinsic path.
 */
internal data class TextureDepthCalibration(
    val textureWidth: Int,
    val textureHeight: Int,
    val depthWidth: Int,
    val depthHeight: Int,
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
)

/**
 * Camera-space point from a depth pixel (metres). OpenGL convention:
 * +X right, +Y up, −Z forward of the camera.
 */
internal data class DepthCameraPoint(
    val xMeters: Double,
    val yMeters: Double,
    val zMeters: Double,
)

/**
 * Pure scalar texture-intrinsic unprojection onto a depth pixel.
 * Stateless diagnostic helper — no buffers, pose, or navigation coupling.
 */
internal object TextureDepthProjection {
    /** Maximum allowed depthWidth × depthHeight (Long product). */
    private const val MAX_DEPTH_AREA: Long = 1_048_576L

    fun project(
        calibration: TextureDepthCalibration,
        x: Int,
        y: Int,
        depthMm: Int,
    ): DepthCameraPoint? {
        val textureWidth = calibration.textureWidth
        val textureHeight = calibration.textureHeight
        val depthWidth = calibration.depthWidth
        val depthHeight = calibration.depthHeight
        if (textureWidth <= 0 || textureHeight <= 0 || depthWidth <= 0 || depthHeight <= 0) {
            return null
        }

        val depthArea = depthWidth.toLong() * depthHeight.toLong()
        if (depthArea > MAX_DEPTH_AREA) {
            return null
        }

        // Exact equal aspect via Long cross-products (no Int overflow wrap).
        if (textureWidth.toLong() * depthHeight.toLong() !=
            textureHeight.toLong() * depthWidth.toLong()
        ) {
            return null
        }

        val fx = calibration.fx
        val fy = calibration.fy
        val cx = calibration.cx
        val cy = calibration.cy
        // Strictly positive focals; -0.0 is not > 0. Principal may be off-image.
        if (!(fx.isFinite() && fx > 0.0) || !(fy.isFinite() && fy > 0.0)) {
            return null
        }
        if (!cx.isFinite() || !cy.isFinite()) {
            return null
        }

        if (x < 0 || x >= depthWidth || y < 0 || y >= depthHeight) {
            return null
        }
        if (depthMm < 1 || depthMm > 65535) {
            return null
        }

        val scaleX = depthWidth.toDouble() / textureWidth.toDouble()
        val scaleY = depthHeight.toDouble() / textureHeight.toDouble()
        val fxScaled = fx * scaleX
        val fyScaled = fy * scaleY
        val cxScaled = cx * scaleX
        val cyScaled = cy * scaleY
        if (!(fxScaled.isFinite() && fxScaled > 0.0) ||
            !(fyScaled.isFinite() && fyScaled > 0.0) ||
            !cxScaled.isFinite() ||
            !cyScaled.isFinite()
        ) {
            return null
        }

        val metres = depthMm / 1000.0
        val xMeters = metres * (x - cxScaled) / fxScaled
        val yMeters = metres * (cyScaled - y) / fyScaled
        val zMeters = -metres
        if (!xMeters.isFinite() || !yMeters.isFinite() || !zMeters.isFinite()) {
            return null
        }

        return DepthCameraPoint(
            xMeters = xMeters,
            yMeters = yMeters,
            zMeters = zMeters,
        )
    }
}
