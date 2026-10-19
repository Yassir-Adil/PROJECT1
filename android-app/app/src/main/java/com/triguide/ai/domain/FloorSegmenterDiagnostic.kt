package com.triguide.ai.domain

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic-only segmentation adapter.
 *
 * Adapts raw LIVE_STREAM-style outputs into [FloorMaskEvidenceEvaluator] and
 * [EvidenceReplayHarness]. Never calls navigation, TTS, or movement code.
 *
 * Inspired by MediaPipe Image Segmenter LIVE_STREAM scheduling (drop when busy)
 * and Sailens-style model provenance — TriGuide-owned code; no upstream copy.
 */
class FloorSegmenterDiagnostic(
    private val modelAssetPresent: Boolean = false,
    private val modelBytesValid: Boolean = false,
    private val initSucceeded: Boolean = false,
) {
    private val accepting = AtomicBoolean(true)
    private val nextSyntheticFrameId = AtomicLong(1)
    private val droppedFrames = AtomicLong(0)
    private val replaySamples = mutableListOf<EvidenceReplaySample>()
    private var previousAccepted: AcceptedFloorMaskFrame? = null
    private var latestResult: FloorSegmenterResult = unavailableNoModel(frameId = 0, frameElapsedMs = 0)

    val droppedFrameCount: Long get() = droppedFrames.get()
    val latest: FloorSegmenterResult get() = latestResult
    val diagnosticSamples: List<EvidenceReplaySample> get() = replaySamples.toList()

    fun runtimeState(): FloorSegmenterRuntimeState = when {
        !modelAssetPresent -> FloorSegmenterRuntimeState.NO_MODEL
        !modelBytesValid -> FloorSegmenterRuntimeState.INVALID_MODEL
        !initSucceeded -> FloorSegmenterRuntimeState.INIT_FAILED
        else -> FloorSegmenterRuntimeState.READY_DIAGNOSTIC
    }

    /**
     * Camera analysis hook without a second CameraX owner.
     * When no cleared model is present, records UNAVAILABLE and returns immediately.
     * When busy, counts a dropped frame (KEEP_ONLY_LATEST style).
     */
    fun offerAnalysisSlot(nowElapsedMs: Long): Boolean {
        if (runtimeState() != FloorSegmenterRuntimeState.READY_DIAGNOSTIC) {
            val result = when (runtimeState()) {
                FloorSegmenterRuntimeState.NO_MODEL ->
                    unavailableNoModel(nextSyntheticFrameId.getAndIncrement(), nowElapsedMs)
                FloorSegmenterRuntimeState.INVALID_MODEL ->
                    rejected(
                        runtime = FloorSegmenterRuntimeState.INVALID_MODEL,
                        evidence = FloorMaskEvidenceState.INVALID,
                        issue = FloorMaskEvidenceIssue.INVALID_SOURCE,
                        frameId = nextSyntheticFrameId.getAndIncrement(),
                        frameElapsedMs = nowElapsedMs,
                        detail = "Model asset present but bytes/hash invalid.",
                    )
                FloorSegmenterRuntimeState.INIT_FAILED ->
                    rejected(
                        runtime = FloorSegmenterRuntimeState.INIT_FAILED,
                        evidence = FloorMaskEvidenceState.UNAVAILABLE,
                        issue = FloorMaskEvidenceIssue.NO_FRAME,
                        frameId = nextSyntheticFrameId.getAndIncrement(),
                        frameElapsedMs = nowElapsedMs,
                        detail = "Segmenter initialization failed.",
                    )
                else -> unavailableNoModel(nextSyntheticFrameId.getAndIncrement(), nowElapsedMs)
            }
            publish(
                result,
                sceneId = "live-diagnostic",
                decisionKey = null,
                voiceEventKey = null,
                evaluatedAtElapsedMs = nowElapsedMs,
            )
            return false
        }
        if (!accepting.compareAndSet(true, false)) {
            droppedFrames.incrementAndGet()
            latestResult = latestResult.copy(
                runtimeState = FloorSegmenterRuntimeState.DROPPED_FRAME,
                dropped = true,
                detail = "Frame dropped; prior diagnostic work still in flight.",
            )
            return false
        }
        return true
    }

    fun releaseAnalysisSlot() {
        accepting.set(true)
    }

    /**
     * Validates a raw producer output (fixtures or future MediaPipe listener).
     * Category masks are never mapped to floor confidence while class mapping is UNMAPPED.
     */
    fun ingest(
        raw: RawFloorSegmenterOutput,
        nowElapsedMs: Long,
        sceneId: String = "fixture",
        decisionKey: String? = null,
        voiceEventKey: String? = null,
    ): FloorSegmenterResult {
        val result = validateAndEvaluate(raw, nowElapsedMs)
        publish(result, sceneId, decisionKey, voiceEventKey, evaluatedAtElapsedMs = nowElapsedMs)
        return result
    }

    fun replayDiagnostics(): EvidenceReplayReport = EvidenceReplayHarness.replay(replaySamples.toList())

    fun clearDiagnostics() {
        replaySamples.clear()
        previousAccepted = null
    }

    private fun validateAndEvaluate(
        raw: RawFloorSegmenterOutput,
        nowElapsedMs: Long,
    ): FloorSegmenterResult {
        if (!raw.modelPresent) {
            return unavailableNoModel(raw.frameId, raw.frameElapsedMs)
        }
        if (!raw.modelValid) {
            return rejected(
                runtime = FloorSegmenterRuntimeState.INVALID_MODEL,
                evidence = FloorMaskEvidenceState.INVALID,
                issue = FloorMaskEvidenceIssue.INVALID_SOURCE,
                frameId = raw.frameId,
                frameElapsedMs = raw.frameElapsedMs,
                modelId = raw.modelId,
                modelSha256 = raw.modelSha256,
                detail = "Malformed or uncleared model.",
                latency = raw.inferenceLatencyMs,
                delegate = raw.delegate,
            )
        }
        if (!raw.initSucceeded) {
            return rejected(
                runtime = FloorSegmenterRuntimeState.INIT_FAILED,
                evidence = FloorMaskEvidenceState.UNAVAILABLE,
                issue = FloorMaskEvidenceIssue.NO_FRAME,
                frameId = raw.frameId,
                frameElapsedMs = raw.frameElapsedMs,
                modelId = raw.modelId,
                modelSha256 = raw.modelSha256,
                detail = "Initialization failed.",
                latency = raw.inferenceLatencyMs,
                delegate = raw.delegate,
            )
        }
        if (raw.maskWidth <= 0 || raw.maskHeight <= 0) {
            return rejected(
                runtime = FloorSegmenterRuntimeState.ERROR,
                evidence = FloorMaskEvidenceState.INVALID,
                issue = FloorMaskEvidenceIssue.INVALID_MAPPING,
                frameId = raw.frameId,
                frameElapsedMs = raw.frameElapsedMs,
                modelId = raw.modelId,
                modelSha256 = raw.modelSha256,
                detail = "Wrong mask dimensions.",
                latency = raw.inferenceLatencyMs,
                delegate = raw.delegate,
                maskW = raw.maskWidth,
                maskH = raw.maskHeight,
                outputType = raw.outputType,
            )
        }
        if (raw.mapping == null) {
            return rejected(
                runtime = FloorSegmenterRuntimeState.ERROR,
                evidence = FloorMaskEvidenceState.INVALID,
                issue = FloorMaskEvidenceIssue.INVALID_MAPPING,
                frameId = raw.frameId,
                frameElapsedMs = raw.frameElapsedMs,
                modelId = raw.modelId,
                modelSha256 = raw.modelSha256,
                detail = "Missing crop/rotation/mirror mapping.",
                latency = raw.inferenceLatencyMs,
                delegate = raw.delegate,
                maskW = raw.maskWidth,
                maskH = raw.maskHeight,
                outputType = raw.outputType,
                inputW = raw.inputWidthPx,
                inputH = raw.inputHeightPx,
            )
        }
        if (raw.mapping.maskWidthCells != raw.maskWidth || raw.mapping.maskHeightCells != raw.maskHeight) {
            return rejected(
                runtime = FloorSegmenterRuntimeState.ERROR,
                evidence = FloorMaskEvidenceState.INVALID,
                issue = FloorMaskEvidenceIssue.INVALID_MAPPING,
                frameId = raw.frameId,
                frameElapsedMs = raw.frameElapsedMs,
                modelId = raw.modelId,
                modelSha256 = raw.modelSha256,
                detail = "Mapping size does not match mask size.",
                latency = raw.inferenceLatencyMs,
                delegate = raw.delegate,
                maskW = raw.maskWidth,
                maskH = raw.maskHeight,
                outputType = raw.outputType,
                mapping = raw.mapping,
                inputW = raw.inputWidthPx,
                inputH = raw.inputHeightPx,
            )
        }

        when (raw.outputType) {
            FloorSegmenterOutputType.EMPTY, FloorSegmenterOutputType.UNKNOWN ->
                return rejected(
                    runtime = FloorSegmenterRuntimeState.ERROR,
                    evidence = FloorMaskEvidenceState.UNAVAILABLE,
                    issue = FloorMaskEvidenceIssue.NO_FRAME,
                    frameId = raw.frameId,
                    frameElapsedMs = raw.frameElapsedMs,
                    modelId = raw.modelId,
                    modelSha256 = raw.modelSha256,
                    detail = "Empty or unknown mask output.",
                    latency = raw.inferenceLatencyMs,
                    delegate = raw.delegate,
                    maskW = raw.maskWidth,
                    maskH = raw.maskHeight,
                    outputType = raw.outputType,
                    mapping = raw.mapping,
                    inputW = raw.inputWidthPx,
                    inputH = raw.inputHeightPx,
                )
            FloorSegmenterOutputType.CATEGORY_MASK -> {
                // UNMAPPED: category indices must not become floor confidence.
                if (raw.categoryValues.isEmpty() ||
                    raw.categoryValues.size != raw.maskWidth * raw.maskHeight
                ) {
                    return rejected(
                        runtime = FloorSegmenterRuntimeState.ERROR,
                        evidence = FloorMaskEvidenceState.INVALID,
                        issue = FloorMaskEvidenceIssue.INVALID_GRID,
                        frameId = raw.frameId,
                        frameElapsedMs = raw.frameElapsedMs,
                        modelId = raw.modelId,
                        modelSha256 = raw.modelSha256,
                        detail = "Malformed category mask.",
                        latency = raw.inferenceLatencyMs,
                        delegate = raw.delegate,
                        maskW = raw.maskWidth,
                        maskH = raw.maskHeight,
                        outputType = raw.outputType,
                        mapping = raw.mapping,
                        inputW = raw.inputWidthPx,
                        inputH = raw.inputHeightPx,
                        categories = raw.categoryValues,
                    )
                }
                return rejected(
                    runtime = FloorSegmenterRuntimeState.READY_DIAGNOSTIC,
                    evidence = FloorMaskEvidenceState.INVALID,
                    issue = FloorMaskEvidenceIssue.INVALID_CONFIDENCE,
                    frameId = raw.frameId,
                    frameElapsedMs = raw.frameElapsedMs,
                    modelId = raw.modelId,
                    modelSha256 = raw.modelSha256,
                    detail = FloorSegmenterModelContract.MAPPING_NOTE,
                    latency = raw.inferenceLatencyMs,
                    delegate = raw.delegate,
                    maskW = raw.maskWidth,
                    maskH = raw.maskHeight,
                    outputType = raw.outputType,
                    mapping = raw.mapping,
                    inputW = raw.inputWidthPx,
                    inputH = raw.inputHeightPx,
                    categories = raw.categoryValues,
                )
            }
            FloorSegmenterOutputType.CONFIDENCE_MASK -> Unit
        }

        if (raw.confidenceValues.isEmpty()) {
            return rejected(
                runtime = FloorSegmenterRuntimeState.ERROR,
                evidence = FloorMaskEvidenceState.UNAVAILABLE,
                issue = FloorMaskEvidenceIssue.NO_FRAME,
                frameId = raw.frameId,
                frameElapsedMs = raw.frameElapsedMs,
                modelId = raw.modelId,
                modelSha256 = raw.modelSha256,
                detail = "Missing confidence mask.",
                latency = raw.inferenceLatencyMs,
                delegate = raw.delegate,
                maskW = raw.maskWidth,
                maskH = raw.maskHeight,
                outputType = raw.outputType,
                mapping = raw.mapping,
                inputW = raw.inputWidthPx,
                inputH = raw.inputHeightPx,
            )
        }
        if (raw.confidenceValues.size != raw.maskWidth * raw.maskHeight) {
            return rejected(
                runtime = FloorSegmenterRuntimeState.ERROR,
                evidence = FloorMaskEvidenceState.INVALID,
                issue = FloorMaskEvidenceIssue.INVALID_GRID,
                frameId = raw.frameId,
                frameElapsedMs = raw.frameElapsedMs,
                modelId = raw.modelId,
                modelSha256 = raw.modelSha256,
                detail = "Confidence grid size mismatch.",
                latency = raw.inferenceLatencyMs,
                delegate = raw.delegate,
                maskW = raw.maskWidth,
                maskH = raw.maskHeight,
                outputType = raw.outputType,
                mapping = raw.mapping,
                inputW = raw.inputWidthPx,
                inputH = raw.inputHeightPx,
                confidence = raw.confidenceValues,
            )
        }
        if (raw.confidenceValues.any { !it.isFinite() || it < 0f || it > 1f }) {
            return rejected(
                runtime = FloorSegmenterRuntimeState.ERROR,
                evidence = FloorMaskEvidenceState.INVALID,
                issue = FloorMaskEvidenceIssue.INVALID_CONFIDENCE,
                frameId = raw.frameId,
                frameElapsedMs = raw.frameElapsedMs,
                modelId = raw.modelId,
                modelSha256 = raw.modelSha256,
                detail = "Non-finite or out-of-range confidence.",
                latency = raw.inferenceLatencyMs,
                delegate = raw.delegate,
                maskW = raw.maskWidth,
                maskH = raw.maskHeight,
                outputType = raw.outputType,
                mapping = raw.mapping,
                inputW = raw.inputWidthPx,
                inputH = raw.inputHeightPx,
                confidence = raw.confidenceValues,
            )
        }
        if (!raw.inferenceLatencyMs.isFinite() || raw.inferenceLatencyMs < 0.0) {
            return rejected(
                runtime = FloorSegmenterRuntimeState.ERROR,
                evidence = FloorMaskEvidenceState.INVALID,
                issue = FloorMaskEvidenceIssue.INVALID_FRAME,
                frameId = raw.frameId,
                frameElapsedMs = raw.frameElapsedMs,
                modelId = raw.modelId,
                modelSha256 = raw.modelSha256,
                detail = "Invalid inference latency.",
                latency = raw.inferenceLatencyMs,
                delegate = raw.delegate,
                maskW = raw.maskWidth,
                maskH = raw.maskHeight,
                outputType = raw.outputType,
                mapping = raw.mapping,
                inputW = raw.inputWidthPx,
                inputH = raw.inputHeightPx,
                confidence = raw.confidenceValues,
            )
        }

        val candidate = CandidateFloorMaskEvidence(
            source = FloorMaskSource(
                sourceId = FloorSegmenterModelContract.SOURCE_ID,
                modelId = raw.modelId,
                modelSha256 = FloorMaskModelSha256(raw.modelSha256),
            ),
            frameId = raw.frameId,
            frameElapsedMs = raw.frameElapsedMs,
            mapping = raw.mapping,
            floorConfidence = raw.confidenceValues,
        )
        val evidence = FloorMaskEvidenceEvaluator.evaluate(
            candidate = candidate,
            nowElapsedMs = nowElapsedMs,
            previousAcceptedFrame = previousAccepted,
        )
        evidence.acceptedFrame?.let { previousAccepted = it }

        return FloorSegmenterResult(
            modelId = raw.modelId,
            modelSha256 = raw.modelSha256,
            frameId = raw.frameId,
            frameElapsedMs = raw.frameElapsedMs,
            inputWidthPx = raw.inputWidthPx,
            inputHeightPx = raw.inputHeightPx,
            mapping = raw.mapping,
            maskWidth = raw.maskWidth,
            maskHeight = raw.maskHeight,
            outputType = raw.outputType,
            confidenceValues = raw.confidenceValues,
            categoryValues = emptyList(),
            inferenceLatencyMs = raw.inferenceLatencyMs,
            delegate = raw.delegate,
            runtimeState = FloorSegmenterRuntimeState.READY_DIAGNOSTIC,
            issue = evidence.issue,
            evidenceState = evidence.state,
            dropped = false,
            detail = when (evidence.state) {
                FloorMaskEvidenceState.DIAGNOSTIC ->
                    "Diagnostic mask recorded. Not navigation evidence."
                else -> evidence.issue?.name ?: "Rejected by FloorMaskEvidenceEvaluator."
            },
        )
    }

    private fun publish(
        result: FloorSegmenterResult,
        sceneId: String,
        decisionKey: String?,
        voiceEventKey: String?,
        evaluatedAtElapsedMs: Long,
    ) {
        latestResult = result
        replaySamples += EvidenceReplaySample(
            sceneId = sceneId,
            candidate = result.toCandidateOrNull(),
            evaluatedAtElapsedMs = evaluatedAtElapsedMs.coerceAtLeast(0L),
            inferenceLatencyMs = result.inferenceLatencyMs.takeIf { it.isFinite() && it >= 0.0 },
            decisionKey = decisionKey,
            voiceEventKey = voiceEventKey,
        )
    }

    private fun unavailableNoModel(frameId: Long, frameElapsedMs: Long) = rejected(
        runtime = FloorSegmenterRuntimeState.NO_MODEL,
        evidence = FloorMaskEvidenceState.UNAVAILABLE,
        issue = FloorMaskEvidenceIssue.NO_FRAME,
        frameId = frameId,
        frameElapsedMs = frameElapsedMs,
        detail = "No floor segmenter model is bundled.",
        delegate = FloorSegmenterDelegate.NONE,
    )

    private fun rejected(
        runtime: FloorSegmenterRuntimeState,
        evidence: FloorMaskEvidenceState,
        issue: FloorMaskEvidenceIssue?,
        frameId: Long,
        frameElapsedMs: Long,
        modelId: String = FloorSegmenterModelContract.MODEL_ID,
        modelSha256: String = FloorSegmenterModelContract.MODEL_SHA256_HEX,
        detail: String = "",
        latency: Double = 0.0,
        delegate: FloorSegmenterDelegate = FloorSegmenterDelegate.NONE,
        maskW: Int = 0,
        maskH: Int = 0,
        outputType: FloorSegmenterOutputType = FloorSegmenterOutputType.EMPTY,
        mapping: ImageToFloorMaskMapping? = null,
        inputW: Int = 0,
        inputH: Int = 0,
        confidence: List<Float> = emptyList(),
        categories: List<Int> = emptyList(),
    ) = FloorSegmenterResult(
        modelId = modelId,
        modelSha256 = modelSha256,
        frameId = frameId,
        frameElapsedMs = frameElapsedMs,
        inputWidthPx = inputW,
        inputHeightPx = inputH,
        mapping = mapping,
        maskWidth = maskW,
        maskHeight = maskH,
        outputType = outputType,
        confidenceValues = confidence,
        categoryValues = categories,
        inferenceLatencyMs = latency,
        delegate = delegate,
        runtimeState = runtime,
        issue = issue,
        evidenceState = evidence,
        detail = detail,
    )
}

fun FloorSegmenterResult.toCandidateOrNull(): CandidateFloorMaskEvidence? {
    if (evidenceState == FloorMaskEvidenceState.UNAVAILABLE) return null
    if (outputType != FloorSegmenterOutputType.CONFIDENCE_MASK) return null
    val map = mapping ?: return null
    if (confidenceValues.size != maskWidth * maskHeight) return null
    if (modelSha256.length != 64) return null
    return CandidateFloorMaskEvidence(
        source = FloorMaskSource(
            sourceId = FloorSegmenterModelContract.SOURCE_ID,
            modelId = modelId,
            modelSha256 = FloorMaskModelSha256(modelSha256),
        ),
        frameId = frameId,
        frameElapsedMs = frameElapsedMs,
        mapping = map,
        floorConfidence = confidenceValues,
    )
}
