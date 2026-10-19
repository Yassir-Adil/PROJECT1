package com.triguide.ai.diagnostics

import com.triguide.ai.domain.DetectionFrame
import com.triguide.ai.domain.DetectionReports
import com.triguide.ai.domain.DetectorConfig
import com.triguide.ai.domain.DetectorReadiness
import com.triguide.ai.domain.SceneDescription
import com.triguide.ai.domain.SceneLine
import com.triguide.ai.domain.SceneObservations
import com.triguide.ai.domain.VoiceSettings

/** Observation-only presentation; never constructs navigation evidence or a command. */
internal object DiagnosticObjectPresentation {
    const val MAXIMUM_RESULT_AGE_MS = 1_000L

    fun freshFrame(frame: DetectionFrame?, readiness: DetectorReadiness, nowMs: Long, running: Boolean): DetectionFrame? {
        if (!running || readiness != DetectorReadiness.READY || frame?.fromRealDetector != true) return null
        val completed = frame.completedAtElapsedMs ?: return null
        if (completed < 0L || nowMs < completed || nowMs - completed > MAXIMUM_RESULT_AGE_MS) return null
        if (frame.imageWidth <= 0 || frame.imageHeight <= 0) return null
        val objects = DetectionReports.forGuidance(frame.objects, DetectorConfig.SCORE_THRESHOLD).filter {
            it.left >= 0f && it.top >= 0f && it.right <= frame.imageWidth && it.bottom <= frame.imageHeight
        }.take(DetectorConfig.MAX_RESULTS)
        return frame.copy(objects = objects)
    }

    fun description(frame: DetectionFrame?, readiness: DetectorReadiness, nowMs: Long, running: Boolean): SceneLine? {
        val fresh = freshFrame(frame, readiness, nowMs, running) ?: return null
        return SceneDescription.fromCues(SceneObservations.fromCamera(readiness, fresh, nowMs).cues)
    }

    fun maySpeak(settings: VoiceSettings, running: Boolean): Boolean =
        running && settings.speechEnabled && !settings.quietMode
}
