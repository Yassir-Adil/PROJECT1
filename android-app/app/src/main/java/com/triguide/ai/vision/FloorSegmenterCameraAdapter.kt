package com.triguide.ai.vision

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.triguide.ai.domain.FloorSegmenterDiagnostic
import com.triguide.ai.domain.FloorSegmenterModelContract
import com.triguide.ai.domain.FloorSegmenterResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shares the existing CameraX analysis stream. Does not open a second camera.
 *
 * Phase 1: no model asset is bundled, so every offer records diagnostic
 * UNAVAILABLE / NO_MODEL without running MediaPipe ImageSegmenter and without
 * touching navigation or TTS.
 *
 * Pattern reference (not copied): MediaPipe samples Image Segmenter LIVE_STREAM
 * drop-when-busy scheduling.
 */
class FloorSegmenterCameraAdapter(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val closed = AtomicBoolean(false)

    private val assetPresent = assetExists(FloorSegmenterModelContract.EXPECTED_ASSET_PATH)
    // Phase 1: never treat a missing Phase-2 hash as valid even if a file appears.
    private val diagnostic = FloorSegmenterDiagnostic(
        modelAssetPresent = false,
        modelBytesValid = false,
        initSucceeded = false,
    )

    private val latestState = MutableStateFlow(diagnostic.latest)
    val latest: StateFlow<FloorSegmenterResult> = latestState.asStateFlow()

    val droppedFrameCount: Long get() = diagnostic.droppedFrameCount
    val assetListedButDisabled: Boolean get() = assetPresent

    /**
     * Called on the camera analyzer thread after/alongside the object detector.
     * Must return quickly. The caller closes [image].
     */
    fun offerAnalysisFrame(image: ImageProxy, @Suppress("UNUSED_PARAMETER") frontCamera: Boolean) {
        if (closed.get()) return
        val now = SystemClock.elapsedRealtime()
        // Intentionally ignore pixel buffers until a cleared model is registered.
        diagnostic.offerAnalysisSlot(nowElapsedMs = now)
        latestState.value = diagnostic.latest
        // Slot is never held open in Phase 1 (no async inference).
        diagnostic.releaseAnalysisSlot()
        // image is not copied; detector owns any bitmap work.
    }

    fun diagnosticReportSamples() = diagnostic.diagnosticSamples

    /** One-line Diagnostics status. Never claims walkability or clearance. */
    fun diagnosticSummaryLine(): String {
        val result = latestState.value
        val assetNote = if (assetPresent) {
            "asset_listed_but_disabled=true"
        } else {
            "asset_listed_but_disabled=false"
        }
        return "runtime=${result.runtimeState} evidence=${result.evidenceState} " +
            "mapping=${FloorSegmenterModelContract.classMappingStatus} " +
            "dropped=${droppedFrameCount} $assetNote. " +
            FloorSegmenterModelContract.MAPPING_NOTE
    }

    fun close() {
        closed.set(true)
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            appContext.assets.open(assetPath).close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
