package com.triguide.ai.domain

import com.triguide.ai.sim.PlanarPose
import kotlinx.coroutines.flow.StateFlow

/**
 * On-device detector. Implementations must not invent objects when the model
 * is missing, still loading, or failing.
 */
interface ObjectDetectionEngine {
    val statusLabel: String
    val isRealDetector: Boolean
    val readiness: kotlinx.coroutines.flow.StateFlow<DetectorReadiness>
    val statusDetail: kotlinx.coroutines.flow.StateFlow<String>
    val latestFrame: kotlinx.coroutines.flow.StateFlow<DetectionFrame?>
    val inferenceError: kotlinx.coroutines.flow.StateFlow<String?>
    fun currentObservation(): EnvironmentObservation
    fun clearFrame()
    fun close()
}

data class EnvironmentObservation(
    val summary: String,
    val detections: List<String>,
    val synthetic: Boolean,
    val realDetector: Boolean,
)

interface NavigationDecisionPolicy {
    fun decide(observation: SceneObservation): NavigationDecision
}

data class NavigationDecision(
    val action: GuidanceAction,
    val reason: String,
    val confidence: Float,
    val linearSpeedMps: Double,
    val steeringRadians: Double,
    val spokenCue: String,
    val basedOnRealVision: Boolean,
    /** True only when authored synthetic positions were used. Camera decisions leave this false. */
    val metricDepthUsed: Boolean,
    val perceptionLabel: String,
    /** Detector label used in the spoken cue. Empty when the cue has no single object. */
    val subjectLabel: String = "",
    /** Image side of [subjectLabel]: left, right, center, or both. Empty when none. */
    val subjectSide: String = "",
) {
    fun toSteeringCommand(): SteeringCommand = SteeringCommand(linearSpeedMps, steeringRadians)

    fun withNarration(reason: String, confidence: Float): NavigationDecision {
        val bounded = confidence.coerceIn(0f, 1f)
        return copy(
            reason = reason,
            confidence = bounded,
            spokenCue = NavigationSpeech.of(action, reason, bounded, perceptionLabel),
        )
    }
}

data class SteeringCommand(
    val linearSpeedMps: Double,
    val steeringRadians: Double,
)

data class RobotState(
    val pose: PlanarPose = PlanarPose(0.0, 0.0, 0.0),
    val linearSpeedMps: Double = 0.0,
    val steeringRadians: Double = 0.0,
)

interface VirtualRobotController {
    val state: StateFlow<RobotState>
    fun setCommand(command: SteeringCommand)
    fun tick(dtSeconds: Double)
    fun reset()
}

/**
 * Placeholder for a future physical chassis. Phase 1–2 must not claim a link exists.
 */
interface FutureHardwareController {
    val connectionLabel: String
    val isMock: Boolean
    fun dispatch(command: SteeringCommand): HardwareDispatchResult
}

data class HardwareDispatchResult(
    val accepted: Boolean,
    val detail: String,
)

interface CameraSource {
    fun start(
        context: android.content.Context,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: androidx.camera.view.PreviewView,
        onFrameTimestampNs: (Long) -> Unit,
        onAnalysisFrame: (androidx.camera.core.ImageProxy, Boolean) -> Unit,
        onResult: (CameraStartResult) -> Unit,
    )

    fun stop()
}

sealed class CameraStartResult {
    data object Streaming : CameraStartResult()
    data class Unavailable(val reason: String) : CameraStartResult()
    data class Failed(val reason: String) : CameraStartResult()
}
