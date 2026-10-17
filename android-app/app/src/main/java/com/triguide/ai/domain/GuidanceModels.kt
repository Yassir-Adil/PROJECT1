package com.triguide.ai.domain

import com.triguide.ai.sim.PlanarPose
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 1 does not run a detector. Implementations must not invent objects.
 */
interface ObjectDetectionEngine {
    val statusLabel: String
    val isRealDetector: Boolean
    fun currentObservation(): EnvironmentObservation
}

data class EnvironmentObservation(
    val summary: String,
    val detections: List<String>,
    val synthetic: Boolean,
    val realDetector: Boolean,
)

interface NavigationDecisionPolicy {
    fun decide(observation: EnvironmentObservation): NavigationDecision
}

data class NavigationDecision(
    val linearSpeedMps: Double,
    val steeringRadians: Double,
    val spokenCue: String,
    val basedOnRealVision: Boolean,
)

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
        onResult: (CameraStartResult) -> Unit,
    )

    fun stop()
}

sealed class CameraStartResult {
    data object Streaming : CameraStartResult()
    data class Unavailable(val reason: String) : CameraStartResult()
    data class Failed(val reason: String) : CameraStartResult()
}
