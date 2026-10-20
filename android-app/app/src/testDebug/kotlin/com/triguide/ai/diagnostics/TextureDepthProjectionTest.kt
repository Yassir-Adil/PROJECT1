package com.triguide.ai.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Contract for pure scalar texture-intrinsic → depth-camera unprojection.
 * Scalar fixtures only — no ARCore / Android / buffers / world pose / nav.
 *
 * Helpers: [TextureDepthCalibration], [DepthCameraPoint], [TextureDepthProjection].
 *
 * Convention (Google ARCore 1.57 PointCloudHelper-style):
 * intrinsics are for the unrotated GPU texture; scale to depth W/H; use integer
 * depth pixel (x,y) with no +0.5 center offset; OpenGL camera (+X right, +Y up,
 * −Z forward): z = −depthMm/1000, x = m·(x−cx)/fx, y = m·(cy−y)/fy.
 * Diagnostic geometry only — never CLEAR / walkability / calibrated accuracy claim.
 * Finite results of any magnitude are accepted; only non-finite / invalid inputs null.
 */
class TextureDepthProjectionTest {

    private fun calib(
        textureWidth: Int = 1920,
        textureHeight: Int = 1080,
        depthWidth: Int = 160,
        depthHeight: Int = 90,
        fx: Double = 1920.0,
        fy: Double = 1920.0,
        cx: Double = 960.0,
        cy: Double = 540.0,
    ): TextureDepthCalibration =
        TextureDepthCalibration(
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            depthWidth = depthWidth,
            depthHeight = depthHeight,
            fx = fx,
            fy = fy,
            cx = cx,
            cy = cy,
        )

    private fun project(
        calibration: TextureDepthCalibration = calib(),
        x: Int,
        y: Int,
        depthMm: Int,
    ): DepthCameraPoint? =
        TextureDepthProjection.project(calibration, x, y, depthMm)

    private fun assertPointClose(
        expectedX: Double,
        expectedY: Double,
        expectedZ: Double,
        actual: DepthCameraPoint?,
        absTol: Double = 1e-12,
    ) {
        assertNotNull(actual)
        assertEquals(expectedX, actual!!.xMeters, absTol)
        assertEquals(expectedY, actual.yMeters, absTol)
        assertEquals(expectedZ, actual.zMeters, absTol)
        assertTrue(actual.xMeters.isFinite())
        assertTrue(actual.yMeters.isFinite())
        assertTrue(actual.zMeters.isFinite())
    }

    // --- matching 16:9 texture/depth (actual Redmi-class sizes) ---

    @Test
    fun matching160x90Against1920x1080ProjectsPrincipalToOriginAtOneMeter() {
        // scale = 160/1920 = 90/1080 = 1/12 → fxS=fyS=160, cxS=80, cyS=45
        val p = project(x = 80, y = 45, depthMm = 1000)
        assertPointClose(0.0, 0.0, -1.0, p)
    }

    @Test
    fun matchingAspectCornerAndEdgeSignsFollowOpenGlCameraAxes() {
        // Top-left depth (0,0): left → −X, above principal → +Y, forward → −Z
        val topLeft = project(x = 0, y = 0, depthMm = 1000)
        assertPointClose(-0.5, 45.0 / 160.0, -1.0, topLeft)

        // Bottom-right (159,89): right → +X, below principal → −Y
        val bottomRight = project(x = 159, y = 89, depthMm = 1000)
        assertPointClose(79.0 / 160.0, -44.0 / 160.0, -1.0, bottomRight)

        assertTrue(topLeft!!.xMeters < 0.0)
        assertTrue(topLeft.yMeters > 0.0)
        assertTrue(topLeft.zMeters < 0.0)
        assertTrue(bottomRight!!.xMeters > 0.0)
        assertTrue(bottomRight.yMeters < 0.0)
    }

    @Test
    fun unequalFxFyScalesIndependentlyOnDepthAxes() {
        val c = calib(fx = 1920.0, fy = 960.0) // fy half of fx in texture pixels
        // fxS=160, fyS=80, cxS=80, cyS=45
        val p = project(c, x = 0, y = 0, depthMm = 2000)
        // m=2; x=2*(0-80)/160=-1; y=2*(45-0)/80=1.125; z=-2
        assertPointClose(-1.0, 1.125, -2.0, p)
    }

    @Test
    fun depthMmAboveSignedShortUsesFullUint16Range() {
        val p = project(x = 80, y = 45, depthMm = 40000)
        assertPointClose(0.0, 0.0, -40.0, p)

        val max = project(x = 80, y = 45, depthMm = 65535)
        assertPointClose(0.0, 0.0, -65.535, max, absTol = 1e-12)
    }

    @Test
    fun offImagePrincipalIsAcceptedWhenFinite() {
        // Negative texture principal is truly outside [0,W)×[0,H), not the left edge.
        // scale 1/12 → cxS=-160, cyS=-90
        val c = calib(cx = -1920.0, cy = -1080.0)
        val p = project(c, x = 80, y = 45, depthMm = 1000)
        // x=1*(80-(-160))/160=1.5; y=1*(-90-45)/160=-135/160; z=-1
        assertPointClose(1.5, -135.0 / 160.0, -1.0, p)
    }

    // --- aspect / size rejection ---

    @Test
    fun mismatched640x480Against1920x1080ReturnsNull() {
        val c = calib(depthWidth = 640, depthHeight = 480)
        assertNull(project(c, x = 320, y = 240, depthMm = 1000))
    }

    @Test
    fun aspectCrossProductMismatchRejectsEvenWhenRatiosLookClose() {
        // 1920x1080 vs 161x90: 1920*90=172800, 1080*161=173880
        val c = calib(depthWidth = 161, depthHeight = 90)
        assertNull(project(c, x = 80, y = 45, depthMm = 1000))
    }

    @Test
    fun depthAreaAboveCapReturnsNull() {
        // 1024*1024 = 1_048_576 allowed; 1025*1024 exceeds
        val over = calib(
            textureWidth = 2050,
            textureHeight = 1024,
            depthWidth = 1025,
            depthHeight = 1024,
            fx = 2050.0,
            fy = 1024.0,
            cx = 1025.0,
            cy = 512.0,
        )
        assertNull(project(over, x = 0, y = 0, depthMm = 1000))

        val atCap = calib(
            textureWidth = 1024,
            textureHeight = 1024,
            depthWidth = 1024,
            depthHeight = 1024,
            fx = 1024.0,
            fy = 1024.0,
            cx = 512.0,
            cy = 512.0,
        )
        assertNotNull(project(atCap, x = 512, y = 512, depthMm = 1000))
    }

    @Test
    fun nonPositiveOrOverflowingDimensionsReturnNull() {
        assertNull(project(calib(textureWidth = 0), x = 0, y = 0, depthMm = 1000))
        assertNull(project(calib(textureHeight = -1080), x = 0, y = 0, depthMm = 1000))
        assertNull(project(calib(depthWidth = 0), x = 0, y = 0, depthMm = 1000))
        assertNull(project(calib(depthHeight = -1), x = 0, y = 0, depthMm = 1000))
        // Int overflow if aspect used Int multiply: large equal-aspect pair must still work via Long
        val largeOk = calib(
            textureWidth = 65536,
            textureHeight = 32768,
            depthWidth = 2,
            depthHeight = 1,
            fx = 65536.0,
            fy = 32768.0,
            cx = 32768.0,
            cy = 16384.0,
        )
        assertNotNull(project(largeOk, x = 1, y = 0, depthMm = 1000))
    }

    // --- depth mm / pixel bounds ---

    @Test
    fun zeroNegativeAndAboveUint16DepthReturnNull() {
        assertNull(project(x = 80, y = 45, depthMm = 0))
        assertNull(project(x = 80, y = 45, depthMm = -1))
        assertNull(project(x = 80, y = 45, depthMm = 65536))
        assertNull(project(x = 80, y = 45, depthMm = Int.MIN_VALUE))
    }

    @Test
    fun depthMmOneIsMinimumAcceptedPositiveMillimetre() {
        val p = project(x = 80, y = 45, depthMm = 1)
        assertPointClose(0.0, 0.0, -0.001, p)
    }

    @Test
    fun pixelOutsideDepthFrameReturnsNull() {
        assertNull(project(x = -1, y = 45, depthMm = 1000))
        assertNull(project(x = 160, y = 45, depthMm = 1000))
        assertNull(project(x = 80, y = -1, depthMm = 1000))
        assertNull(project(x = 80, y = 90, depthMm = 1000))
    }

    @Test
    fun edgePixelsInsideFrameAreAccepted() {
        assertNotNull(project(x = 0, y = 0, depthMm = 1000))
        assertNotNull(project(x = 159, y = 89, depthMm = 1000))
    }

    // --- calibration non-finite / non-positive focals ---

    @Test
    fun nanInfAndNegativeZeroIntrinsicsReturnNull() {
        assertNull(project(calib(fx = Double.NaN), x = 80, y = 45, depthMm = 1000))
        assertNull(project(calib(fy = Double.POSITIVE_INFINITY), x = 80, y = 45, depthMm = 1000))
        assertNull(project(calib(cx = Double.NEGATIVE_INFINITY), x = 80, y = 45, depthMm = 1000))
        assertNull(project(calib(cy = Double.NaN), x = 80, y = 45, depthMm = 1000))
        assertNull(project(calib(fx = -0.0), x = 80, y = 45, depthMm = 1000))
        assertNull(project(calib(fy = -100.0), x = 80, y = 45, depthMm = 1000))
        assertNull(project(calib(fx = 0.0), x = 80, y = 45, depthMm = 1000))
    }

    @Test
    fun scaledFocalUnderflowOrNonFiniteYieldsNull() {
        // After scale, fxS must be finite and > 0; underflow-to-0 or tiny×offset → null
        val underflow = calib(fx = 1e-320, fy = 1920.0)
        assertNull(project(underflow, x = 0, y = 45, depthMm = 1000))

        // Genuinely overflowing: tiny focal × huge principal → Inf lateral (not MAX/2 alone)
        val overflow = calib(fx = 1e-200, fy = 1920.0, cx = 1e200, cy = 540.0)
        assertNull(project(overflow, x = 80, y = 45, depthMm = 1000))
    }

    @Test
    fun finiteHugeLateralResultIsAcceptedWithoutArbitraryBound() {
        // cx=MAX/2 (and even MAX) at normal fx stays finite after scale — must NOT null.
        val halfMax = calib(cx = Double.MAX_VALUE / 2.0, cy = 540.0)
        val pHalf = project(halfMax, x = 80, y = 45, depthMm = 1000)
        assertNotNull(pHalf)
        assertTrue(pHalf!!.xMeters.isFinite())
        assertTrue(pHalf.yMeters.isFinite())
        assertTrue(pHalf.zMeters.isFinite())
        assertEquals(-1.0, pHalf.zMeters, 0.0)
        // scale 1/12 → cxS = MAX/24; x = (80 - MAX/24) / 160
        val expectedX = (80.0 - Double.MAX_VALUE / 24.0) / 160.0
        // Equivalent divide vs multiply order can round by one ULP near MAX_VALUE.
        assertEquals(expectedX, pHalf.xMeters, 2 * Math.ulp(expectedX))
        assertTrue(pHalf.xMeters < 0.0)

        val atMax = calib(cx = Double.MAX_VALUE, cy = 540.0)
        val pMax = project(atMax, x = 80, y = 45, depthMm = 1000)
        assertNotNull(pMax)
        assertTrue(pMax!!.xMeters.isFinite())
        val expectedMaxX = (80.0 - Double.MAX_VALUE / 12.0) / 160.0
        assertEquals(expectedMaxX, pMax.xMeters, 2 * Math.ulp(expectedMaxX))
    }

    @Test
    fun integerPixelConventionHasNoHalfPixelOffsetVersusPrincipal() {
        // If implementation wrongly used (x+0.5), centre would not be exactly origin.
        val p = project(x = 80, y = 45, depthMm = 1000)
        assertNotNull(p)
        assertEquals(0.0, p!!.xMeters, 0.0)
        assertEquals(0.0, p.yMeters, 0.0)
        assertEquals(-1.0, p.zMeters, 0.0)
    }

    @Test
    fun zMetersIsAlwaysNegatedMillimetresOverThousand() {
        val near = project(x = 10, y = 20, depthMm = 500)!!
        val far = project(x = 10, y = 20, depthMm = 2500)!!
        assertEquals(-0.5, near.zMeters, 0.0)
        assertEquals(-2.5, far.zMeters, 0.0)
        // Lateral scales with metres; same pixel → ratio of x equals depth ratio
        assertEquals(far.xMeters / near.xMeters, 5.0, 1e-12)
        assertEquals(far.yMeters / near.yMeters, 5.0, 1e-12)
        assertTrue(abs(near.xMeters) > 0.0 || abs(near.yMeters) > 0.0)
    }

    @Test
    fun equalAspectFourByThreePairProjectsWithoutCpuIntrinsicFallback() {
        // Distinct from 16:9; still valid when cross-products match (no CPU-image fallback).
        val c = calib(
            textureWidth = 1280,
            textureHeight = 960,
            depthWidth = 320,
            depthHeight = 240,
            fx = 1000.0,
            fy = 1000.0,
            cx = 640.0,
            cy = 480.0,
        )
        // scaleX=0.25, scaleY=0.25 → fxS=fyS=250, cxS=160, cyS=120
        val p = project(c, x = 160, y = 120, depthMm = 1000)
        assertPointClose(0.0, 0.0, -1.0, p)
    }

    @Test
    fun identicalTextureAndDepthSizesLeaveIntrinsicsUnscaled() {
        val c = calib(
            textureWidth = 160,
            textureHeight = 90,
            depthWidth = 160,
            depthHeight = 90,
            fx = 120.0,
            fy = 110.0,
            cx = 80.0,
            cy = 45.0,
        )
        val p = project(c, x = 40, y = 10, depthMm = 1000)
        // No scale: x=1*(40-80)/120=-1/3; y=1*(45-10)/110=35/110; z=-1
        assertPointClose(-1.0 / 3.0, 35.0 / 110.0, -1.0, p)
    }

    @Test
    fun hugePositiveDepthDimensionsRejectViaLongAreaNotIntWrap() {
        // 100_000² overflows signed 32-bit if area used Int; must still reject (cap / Long).
        val c = calib(
            textureWidth = 100_000,
            textureHeight = 100_000,
            depthWidth = 100_000,
            depthHeight = 100_000,
            fx = 50_000.0,
            fy = 50_000.0,
            cx = 50_000.0,
            cy = 50_000.0,
        )
        assertNull(project(c, x = 0, y = 0, depthMm = 1000))
    }
}
