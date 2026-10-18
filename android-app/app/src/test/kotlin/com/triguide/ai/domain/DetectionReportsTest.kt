package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionReportsTest {
    @Test
    fun centerCropMapsAMatchingViewWithoutOffset() {
        val rect = DetectionReports.mapCenterCrop(
            boxLeft = 10f,
            boxTop = 20f,
            boxRight = 30f,
            boxBottom = 40f,
            imageWidth = 100,
            imageHeight = 100,
            viewWidth = 100f,
            viewHeight = 100f,
            mirrorHorizontally = false,
        )
        requireNotNull(rect)
        assertEquals(10.0, rect.left.toDouble(), 0.01)
        assertEquals(20.0, rect.top.toDouble(), 0.01)
        assertEquals(30.0, rect.right.toDouble(), 0.01)
        assertEquals(40.0, rect.bottom.toDouble(), 0.01)
    }

    @Test
    fun centerCropOffsetsTheAxisThatIsCropped() {
        val rect = DetectionReports.mapCenterCrop(
            boxLeft = 10f,
            boxTop = 20f,
            boxRight = 30f,
            boxBottom = 40f,
            imageWidth = 100,
            imageHeight = 100,
            viewWidth = 200f,
            viewHeight = 100f,
            mirrorHorizontally = false,
        )
        requireNotNull(rect)
        assertEquals(20.0, rect.left.toDouble(), 0.01)
        assertEquals(-10.0, rect.top.toDouble(), 0.01)
        assertEquals(60.0, rect.right.toDouble(), 0.01)
        assertEquals(30.0, rect.bottom.toDouble(), 0.01)
    }

    @Test
    fun centerCropMirrorsTheFrontCameraHorizontally() {
        val rect = DetectionReports.mapCenterCrop(
            boxLeft = 10f,
            boxTop = 20f,
            boxRight = 30f,
            boxBottom = 40f,
            imageWidth = 100,
            imageHeight = 100,
            viewWidth = 100f,
            viewHeight = 100f,
            mirrorHorizontally = true,
        )
        requireNotNull(rect)
        assertEquals(70.0, rect.left.toDouble(), 0.01)
        assertEquals(20.0, rect.top.toDouble(), 0.01)
        assertEquals(90.0, rect.right.toDouble(), 0.01)
        assertEquals(40.0, rect.bottom.toDouble(), 0.01)
    }

    @Test
    fun centerCropRejectsAZeroView() {
        assertNull(
            DetectionReports.mapCenterCrop(
                boxLeft = 0f,
                boxTop = 0f,
                boxRight = 1f,
                boxBottom = 1f,
                imageWidth = 10,
                imageHeight = 10,
                viewWidth = 0f,
                viewHeight = 10f,
                mirrorHorizontally = false,
            ),
        )
    }

    @Test
    fun horizontalZoneUsesTheSameBandAsSteering() {
        assertEquals(HorizontalZone.LEFT, DetectionReports.horizontalZone(10f, 300))
        assertEquals(HorizontalZone.LEFT, DetectionReports.horizontalZone(120f, 300))
        assertEquals(HorizontalZone.CENTER, DetectionReports.horizontalZone(150f, 300))
        assertEquals(HorizontalZone.RIGHT, DetectionReports.horizontalZone(180f, 300))
        assertEquals(HorizontalZone.RIGHT, DetectionReports.horizontalZone(290f, 300))
    }

    @Test
    fun timestampsStayMonotonic() {
        assertEquals(1L, FrameTimestamps.next(0L, 0L))
        assertEquals(8L, FrameTimestamps.next(5L, 8L))
        assertEquals(6L, FrameTimestamps.next(5L, 4L))
        assertThrows(IllegalArgumentException::class.java) {
            FrameTimestamps.next(1L, -1L)
        }
    }

    @Test
    fun missingModelIgnoresAnyPlantedBoxes() {
        val planted = realFrame(label = "person")
        val observation = DetectionReports.observation(
            readiness = DetectorReadiness.MODEL_MISSING,
            detail = DetectorConfig.MODEL_ASSET_PATH,
            frame = planted,
            modelName = DetectorConfig.MODEL_DISPLAY_NAME,
            scoreThreshold = DetectorConfig.SCORE_THRESHOLD,
        )
        assertTrue(observation.detections.isEmpty())
        assertFalse(observation.realDetector)
        assertFalse(observation.synthetic)
        assertTrue(observation.summary.contains("missing"))
        assertFalse(observation.summary.contains("person"))
    }

    @Test
    fun loadFailureDoesNotBecomeADetection() {
        val observation = DetectionReports.observation(
            readiness = DetectorReadiness.LOAD_FAILED,
            detail = "UnsatisfiedLinkError: lib missing",
            frame = realFrame(label = "chair"),
            modelName = DetectorConfig.MODEL_DISPLAY_NAME,
            scoreThreshold = DetectorConfig.SCORE_THRESHOLD,
        )
        assertTrue(observation.detections.isEmpty())
        assertFalse(observation.realDetector)
        assertFalse(observation.synthetic)
        assertTrue(observation.summary.contains("failed to load"))
        assertFalse(observation.summary.contains("chair"))
    }

    @Test
    fun loadingStateIsNotAnObstacleReading() {
        val observation = DetectionReports.observation(
            readiness = DetectorReadiness.LOADING,
            detail = "Checking the on-device model.",
            frame = null,
            modelName = DetectorConfig.MODEL_DISPLAY_NAME,
            scoreThreshold = DetectorConfig.SCORE_THRESHOLD,
        )
        assertFalse(observation.realDetector)
        assertFalse(observation.synthetic)
        assertTrue(observation.summary.contains("not an obstacle reading"))
    }

    @Test
    fun readyWithoutARealFrameDoesNotListObjects() {
        val observation = DetectionReports.observation(
            readiness = DetectorReadiness.READY,
            detail = DetectorConfig.MODEL_DISPLAY_NAME,
            frame = realFrame(label = "dog").copy(fromRealDetector = false),
            modelName = DetectorConfig.MODEL_DISPLAY_NAME,
            scoreThreshold = DetectorConfig.SCORE_THRESHOLD,
        )
        assertTrue(observation.detections.isEmpty())
        assertFalse(observation.realDetector)
        assertFalse(observation.summary.contains("dog"))
    }

    @Test
    fun guidanceKeepsBoxesAtOrAboveTheScoreFloor() {
        val kept = DetectionReports.forGuidance(
            objects = listOf(
                DetectedObject("person", DetectorConfig.SCORE_THRESHOLD, 1f, 2f, 30f, 40f),
                DetectedObject("cup", DetectorConfig.SCORE_THRESHOLD - 0.01f, 1f, 2f, 10f, 12f),
                DetectedObject("chair", Float.NaN, 1f, 2f, 10f, 12f),
                DetectedObject("book", 0.9f, 5f, 5f, 5f, 9f),
            ),
            threshold = DetectorConfig.SCORE_THRESHOLD,
        )
        assertEquals(listOf("person"), kept.map { it.label })
    }

    @Test
    fun fullBufferCropStaysTheFullFrame() {
        val crop = FrameCrops.clamp(640, 480, 0, 0, 640, 480)
        requireNotNull(crop)
        assertTrue(crop.covers(640, 480))
        assertEquals(640, crop.width)
        assertEquals(480, crop.height)
    }

    @Test
    fun cropIsClampedToTheBuffer() {
        val crop = FrameCrops.clamp(100, 80, -10, 5, 40, 90)
        requireNotNull(crop)
        assertEquals(0, crop.left)
        assertEquals(5, crop.top)
        assertEquals(40, crop.width)
        assertEquals(75, crop.height)
        assertFalse(crop.covers(100, 80))
    }

    @Test
    fun emptyCropIsRejected() {
        assertNull(FrameCrops.clamp(100, 80, 10, 10, 10, 40))
        assertNull(FrameCrops.clamp(0, 80, 0, 0, 10, 10))
    }

    @Test
    fun emptyRealResultStaysEmptyAndMarkedReal() {
        val observation = DetectionReports.observation(
            readiness = DetectorReadiness.READY,
            detail = DetectorConfig.MODEL_DISPLAY_NAME,
            frame = realFrame(label = "person").copy(objects = emptyList()),
            modelName = DetectorConfig.MODEL_DISPLAY_NAME,
            scoreThreshold = 0.5f,
        )
        assertTrue(observation.detections.isEmpty())
        assertTrue(observation.realDetector)
        assertFalse(observation.synthetic)
        assertTrue(observation.summary.contains("no objects"))
        assertTrue(observation.summary.contains("0.50"))
    }

    @Test
    fun realResultListsLabelConfidenceAndZone() {
        val observation = DetectionReports.observation(
            readiness = DetectorReadiness.READY,
            detail = DetectorConfig.MODEL_DISPLAY_NAME,
            frame = DetectionFrame(
                objects = listOf(
                    DetectedObject("person", 0.91f, 0f, 0f, 20f, 40f),
                ),
                imageWidth = 300,
                imageHeight = 200,
                mirrorHorizontally = false,
                inferenceLatencyMs = 40.0,
                fromRealDetector = true,
            ),
            modelName = DetectorConfig.MODEL_DISPLAY_NAME,
            scoreThreshold = 0.5f,
        )
        assertEquals(listOf("person 0.91 left"), observation.detections)
        assertTrue(observation.realDetector)
        assertFalse(observation.synthetic)
        assertTrue(observation.summary.contains("1 object"))
    }

    private fun realFrame(label: String): DetectionFrame {
        return DetectionFrame(
            objects = listOf(DetectedObject(label, 0.99f, 0f, 0f, 10f, 10f)),
            imageWidth = 100,
            imageHeight = 100,
            mirrorHorizontally = false,
            inferenceLatencyMs = 12.0,
            fromRealDetector = true,
        )
    }
}
