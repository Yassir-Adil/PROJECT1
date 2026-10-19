package com.triguide.ai.domain

/** Backend used for a diagnostic segmentation attempt. */
enum class FloorSegmenterDelegate {
    NONE,
    CPU,
    GPU,
}

/** Output tensor kind reported by the producer. */
enum class FloorSegmenterOutputType {
    CONFIDENCE_MASK,
    CATEGORY_MASK,
    EMPTY,
    UNKNOWN,
}

enum class FloorSegmenterRuntimeState {
    NO_MODEL,
    INVALID_MODEL,
    INIT_FAILED,
    READY_DIAGNOSTIC,
    DROPPED_FRAME,
    ERROR,
}

/**
 * One diagnostic segmentation observation.
 *
 * Never a navigation, TTS, clearance, or movement instruction.
 */
data class FloorSegmenterResult(
    val modelId: String,
    val modelSha256: String,
    val frameId: Long,
    val frameElapsedMs: Long,
    val inputWidthPx: Int,
    val inputHeightPx: Int,
    val mapping: ImageToFloorMaskMapping?,
    val maskWidth: Int,
    val maskHeight: Int,
    val outputType: FloorSegmenterOutputType,
    /** Row-major confidence in [0,1] when [outputType] is CONFIDENCE_MASK; otherwise empty. */
    val confidenceValues: List<Float>,
    /** Row-major category indices when [outputType] is CATEGORY_MASK; otherwise empty. */
    val categoryValues: List<Int>,
    val inferenceLatencyMs: Double,
    val delegate: FloorSegmenterDelegate,
    val runtimeState: FloorSegmenterRuntimeState,
    val issue: FloorMaskEvidenceIssue?,
    val evidenceState: FloorMaskEvidenceState,
    val dropped: Boolean = false,
    val detail: String = "",
)

/**
 * Untyped producer payload for JVM fixtures and future MediaPipe listeners.
 * Validated by [FloorSegmenterDiagnostic] before any FloorMaskEvidence use.
 */
data class RawFloorSegmenterOutput(
    val modelId: String = FloorSegmenterModelContract.MODEL_ID,
    val modelSha256: String = FloorSegmenterModelContract.MODEL_SHA256_HEX,
    val frameId: Long,
    val frameElapsedMs: Long,
    val inputWidthPx: Int,
    val inputHeightPx: Int,
    val mapping: ImageToFloorMaskMapping?,
    val maskWidth: Int,
    val maskHeight: Int,
    val outputType: FloorSegmenterOutputType,
    val confidenceValues: List<Float> = emptyList(),
    val categoryValues: List<Int> = emptyList(),
    val inferenceLatencyMs: Double = 0.0,
    val delegate: FloorSegmenterDelegate = FloorSegmenterDelegate.CPU,
    val modelPresent: Boolean = true,
    val modelValid: Boolean = true,
    val initSucceeded: Boolean = true,
)
