package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneDemoStatusTest {
    @Test
    fun readyStateKeepsTheEngineDetail() {
        val line = DetectorStatusText.line(DetectorReadiness.READY, "Model loaded.")
        assertEquals("READY. Model loaded.", line)
    }

    @Test
    fun missingModelDoesNotClaimReady() {
        val line = DetectorStatusText.line(DetectorReadiness.MODEL_MISSING, "")
        assertEquals("Not ready. The model file is missing.", line)
    }

    @Test
    fun loadFailureDoesNotClaimReady() {
        val line = DetectorStatusText.line(DetectorReadiness.LOAD_FAILED, "init error")
        assertEquals("Not ready. The model failed to load. init error", line)
    }

    @Test
    fun firstCameraRequestStaysInTheApp() {
        assertEquals(
            CameraPermissionAction.RequestInApp,
            CameraPermissionPrompt.action(requestAttempted = false, shouldShowRationale = false),
        )
    }

    @Test
    fun rationaleDenialAsksAgainInTheApp() {
        assertEquals(
            CameraPermissionAction.RequestInApp,
            CameraPermissionPrompt.action(requestAttempted = true, shouldShowRationale = true),
        )
    }

    @Test
    fun permanentDenialOpensSettings() {
        assertEquals(
            CameraPermissionAction.OpenAppSettings,
            CameraPermissionPrompt.action(requestAttempted = true, shouldShowRationale = false),
        )
    }
}
