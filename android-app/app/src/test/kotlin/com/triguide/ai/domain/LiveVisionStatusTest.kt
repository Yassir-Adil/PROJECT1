package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVisionStatusTest {

    @Test
    fun possibleObstacleIsNotPresentedAsConfirmedBlockage() {
        val status = LiveVisionStatus.priority(true, true, "Streaming", DetectorReadiness.READY,
            "", null, GuidanceAction.HOLD, PathRiskLevel.POSSIBLE_OBSTACLE)
        assertEquals("Possible obstacle · height unknown", status.label)
        assertEquals("عقبة محتملة؛ ارتفاعها غير مقاس", LiveVisionStatus.spokenStatus(status, ResolvedVoiceLanguage.ARABIC))
        assertFalse(status.isError)
    }

    @Test
    fun unknownOrAccidentalClearHoldDoesNotAssertBlockageOrPermission() {
        for (risk in listOf(PathRiskLevel.UNKNOWN, PathRiskLevel.CLEAR_OBSERVED)) {
            val status = LiveVisionStatus.priority(true, true, "Streaming", DetectorReadiness.READY,
                "", null, GuidanceAction.HOLD, risk)
            assertEquals("Path not confirmed", status.label)
        }
    }

    @Test
    fun safetyLineIsCompactAndDoesNotClaimClearanceOrCertification() {
        val line = LiveVisionStatus.SAFETY_LINE.lowercase()
        assertEquals("Research mode · Not a mobility aid", LiveVisionStatus.SAFETY_LINE)
        assertTrue(line.contains("research"))
        assertTrue(line.contains("not a mobility aid"))
        assertFalse(line.contains("clear"))
        assertFalse(line.contains("safe"))
        assertFalse(line.contains("certified"))
    }

    @Test
    fun holdMapsToStopAndCheckWhenStreaming() {
        val status = LiveVisionStatus.priority(
            running = true,
            permissionGranted = true,
            cameraState = "Streaming",
            readiness = DetectorReadiness.READY,
            detail = "Model loaded.",
            inferenceError = null,
            action = GuidanceAction.HOLD,
            pathRisk = PathRiskLevel.BLOCKED,
        )
        assertEquals("Stop and check", status.label)
        assertFalse(status.isError)
    }

    @Test
    fun holdWithoutRiskEvidenceDoesNotAssertBlockage() {
        val status = LiveVisionStatus.priority(true, true, "Streaming", DetectorReadiness.READY,
            "", null, GuidanceAction.HOLD)
        assertEquals("Path not confirmed", status.label)
    }

    @Test
    fun unknownAndSteeringActionsMapToPathNotConfirmed() {
        listOf(
            GuidanceAction.UNKNOWN,
            GuidanceAction.STRAIGHT,
            GuidanceAction.LEFT,
            GuidanceAction.RIGHT,
        ).forEach { action ->
            val status = LiveVisionStatus.priority(
                running = true,
                permissionGranted = true,
                cameraState = "Streaming",
                readiness = DetectorReadiness.READY,
                detail = "",
                inferenceError = null,
                action = action,
            )
            assertEquals("Path not confirmed", status.label)
            assertFalse(status.isError)
        }
    }

    @Test
    fun idleWithPermissionShowsStartingCameraNotStaleHold() {
        val status = LiveVisionStatus.priority(
            running = true,
            permissionGranted = true,
            cameraState = "Idle",
            readiness = DetectorReadiness.READY,
            detail = "",
            inferenceError = null,
            action = GuidanceAction.HOLD,
        )
        assertEquals("Starting camera", status.label)
        assertFalse(status.isError)
    }

    @Test
    fun conciseErrorsOutrankHoldAndDoNotEchoRawDetail() {
        val permission = LiveVisionStatus.priority(
            running = true,
            permissionGranted = false,
            cameraState = "Idle",
            readiness = DetectorReadiness.READY,
            detail = "",
            inferenceError = null,
            action = GuidanceAction.HOLD,
        )
        assertEquals("Camera permission needed", permission.label)
        assertTrue(permission.isError)

        val cameraFail = LiveVisionStatus.priority(
            running = true,
            permissionGranted = true,
            cameraState = "Failed: timeout",
            readiness = DetectorReadiness.READY,
            detail = "",
            inferenceError = null,
            action = GuidanceAction.HOLD,
        )
        assertEquals("Camera unavailable", cameraFail.label)
        assertTrue(cameraFail.isError)

        val noCamera = LiveVisionStatus.priority(
            running = true,
            permissionGranted = true,
            cameraState = "No camera found",
            readiness = DetectorReadiness.READY,
            detail = "",
            inferenceError = null,
            action = GuidanceAction.HOLD,
        )
        assertEquals("Camera unavailable", noCamera.label)

        val modelMissing = LiveVisionStatus.priority(
            running = true,
            permissionGranted = true,
            cameraState = "Streaming",
            readiness = DetectorReadiness.MODEL_MISSING,
            detail = "assets/foo.tflite",
            inferenceError = null,
            action = GuidanceAction.HOLD,
        )
        assertEquals("Detection unavailable", modelMissing.label)
        assertTrue(modelMissing.isError)

        val modelFailed = LiveVisionStatus.priority(
            running = true,
            permissionGranted = true,
            cameraState = "Streaming",
            readiness = DetectorReadiness.LOAD_FAILED,
            detail = "Native crash detail",
            inferenceError = null,
            action = GuidanceAction.HOLD,
        )
        assertEquals("Detection unavailable", modelFailed.label)
        assertFalse(modelFailed.label.contains("Native crash"))

        val inference = LiveVisionStatus.priority(
            running = true,
            permissionGranted = true,
            cameraState = "Streaming",
            readiness = DetectorReadiness.READY,
            detail = "",
            inferenceError = "Timeout in analyze",
            action = GuidanceAction.HOLD,
        )
        assertEquals("Vision temporarily unavailable", inference.label)
        assertFalse(inference.label.contains("Timeout"))
        assertTrue(inference.isError)
    }

    @Test
    fun startingCameraOutranksHoldWhileWarmUp() {
        val status = LiveVisionStatus.priority(
            running = true,
            permissionGranted = true,
            cameraState = "Starting camera",
            readiness = DetectorReadiness.LOADING,
            detail = "",
            inferenceError = null,
            action = GuidanceAction.HOLD,
        )
        assertEquals("Starting camera", status.label)
        assertFalse(status.isError)
    }

    @Test
    fun statusNeverClaimsClearanceOrProceed() {
        val labels = listOf(
            LiveVisionStatus.priority(
                running = true,
                permissionGranted = true,
                cameraState = "Streaming",
                readiness = DetectorReadiness.READY,
                detail = "",
                inferenceError = null,
                action = GuidanceAction.HOLD,
            ).label,
            LiveVisionStatus.priority(
                running = true,
                permissionGranted = true,
                cameraState = "Streaming",
                readiness = DetectorReadiness.READY,
                detail = "",
                inferenceError = null,
                action = GuidanceAction.STRAIGHT,
            ).label,
            LiveVisionStatus.priority(
                running = true,
                permissionGranted = true,
                cameraState = "Streaming",
                readiness = DetectorReadiness.READY,
                detail = "",
                inferenceError = null,
                action = GuidanceAction.UNKNOWN,
            ).label,
            LiveVisionStatus.SAFETY_LINE,
        ).joinToString(" ").lowercase()
        assertFalse(labels.contains("clear"))
        assertFalse(labels.contains("safe"))
        assertFalse(labels.contains("proceed"))
        assertFalse(labels.contains("turn left"))
        assertFalse(labels.contains("turn right"))
    }

    @Test
    fun previewDescriptionDoesNotEchoDetectorLabels() {
        val description = LiveVisionStatus.PREVIEW_DESCRIPTION.lowercase()
        assertFalse(description.contains("toilet"))
        assertFalse(description.contains("person 0."))
        assertTrue(description.contains("unverified"))
        assertTrue(description.contains("describe this frame"))
        assertTrue(description.contains("swipe up three times"))
        assertTrue(description.contains("talkback"))
        assertTrue(description.contains("emergency stop"))
        assertTrue(description.contains("tap once"))
        assertFalse(description.contains("hold three seconds"))
    }

    @Test
    fun guidancePromptsAreShortHonestCues() {
        assertTrue(LiveVisionStatus.guidancePrompt(GuidanceAction.UNKNOWN).contains("stop and check"))
        assertTrue(LiveVisionStatus.guidancePrompt(GuidanceAction.STRAIGHT).contains("Path not confirmed"))
        assertTrue(LiveVisionStatus.guidancePrompt(GuidanceAction.SLIGHT_RIGHT).contains("Path not confirmed"))
        assertTrue(LiveVisionStatus.guidancePrompt(GuidanceAction.HOLD).contains("Stop"))
        val joined = GuidanceAction.values().joinToString(" ") {
            LiveVisionStatus.guidancePrompt(it)
        }.lowercase()
        assertFalse(joined.contains("clear"))
        assertFalse(joined.contains("path is free"))
        assertFalse(joined.contains("keep walking"))
        assertFalse(joined.contains("walk carefully"))
        assertFalse(joined.contains("turn"))
        assertFalse(joined.contains("several paths"))
    }

    @Test
    fun explicitStatusSpeechIsShortAndUsesSelectedLanguage() {
        val stopped = LiveVisionPriority("Stop and check", isError = false)
        assertEquals("Stop and check", LiveVisionStatus.spokenStatus(stopped, ResolvedVoiceLanguage.ENGLISH))
        assertEquals("توقف وتحقق", LiveVisionStatus.spokenStatus(stopped, ResolvedVoiceLanguage.ARABIC))
        val unknown = LiveVisionPriority("Path not confirmed", isError = false)
        assertEquals("المسار غير مؤكد", LiveVisionStatus.spokenStatus(unknown, ResolvedVoiceLanguage.ARABIC))
        assertFalse(LiveVisionStatus.spokenStatus(stopped, ResolvedVoiceLanguage.ENGLISH).contains("Research mode"))
    }

    @Test
    fun shortcutTipsAreTwoCompactLinesAndDoNotDescribeAHold() {
        assertEquals(2, LiveVisionStatus.BRIEF_TIPS.lines().size)
        assertTrue(LiveVisionStatus.TIP_LINE_ONE.contains("Describe"))
        assertTrue(LiveVisionStatus.TIP_LINE_TWO.contains("Restart"))
        assertFalse(LiveVisionStatus.BRIEF_TIPS.contains("3s"))
        assertFalse(LiveVisionStatus.BRIEF_TIPS.contains("Hold"))
    }

    @Test
    fun everyCameraActionPriorityAvoidsMovementAndJunctionClaims() {
        val labels = GuidanceAction.values().joinToString(" ") { action ->
            LiveVisionStatus.priority(
                running = true,
                permissionGranted = true,
                cameraState = "Streaming",
                readiness = DetectorReadiness.READY,
                detail = "Real detector boxes only",
                inferenceError = null,
                action = action,
            ).label
        }.lowercase()
        listOf("walk", "continue", "turn", "left", "right", "several paths", "clear").forEach {
            assertFalse("Unexpected movement claim: $it", labels.contains(it))
        }
    }
}
