package com.triguide.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triguide.ai.BuildConfig
import com.triguide.ai.camera.CameraXSource
import com.triguide.ai.domain.CameraStartResult
import com.triguide.ai.domain.DetectionReports
import com.triguide.ai.domain.DetectorConfig
import com.triguide.ai.domain.DetectorReadiness
import com.triguide.ai.domain.FloorSegmentationStatus
import com.triguide.ai.domain.GuidanceSession
import com.triguide.ai.domain.IllustrativePath
import com.triguide.ai.domain.MockHardwareController
import com.triguide.ai.domain.NavigationDecisions
import com.triguide.ai.domain.PerformanceMonitor
import com.triguide.ai.domain.SceneObservations
import com.triguide.ai.domain.SpeechDirective
import com.triguide.ai.domain.SteeringCommand
import com.triguide.ai.domain.SyntheticObstacleWorld
import com.triguide.ai.domain.VoiceAlertKind
import com.triguide.ai.domain.VoiceConfig
import com.triguide.ai.domain.VoiceCue
import com.triguide.ai.domain.VoiceCues
import com.triguide.ai.domain.VoiceGateInput
import com.triguide.ai.domain.VoiceGuidanceGate
import com.triguide.ai.robot.SimulatedTricycleController
import com.triguide.ai.sim.TricycleKinematics
import com.triguide.ai.vision.MediaPipeObjectDetectorEngine
import com.triguide.ai.voice.AndroidVoiceSpeaker
import com.triguide.ai.voice.VoiceSettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private enum class Screen {
    Home,
    Camera,
    Simulator,
    Scenarios,
    Diagnostics,
    Settings,
}

private enum class RobotDriveMode(val label: String, val description: String) {
    Manual(
        label = "Manual drive",
        description = "Buttons set the virtual speed and front wheel.",
    ),
    CameraInformed(
        label = "Camera-informed",
        description = "Uses the horizontal position of detection boxes. This screen does not open the camera.",
    ),
    SyntheticWorld(
        label = "Synthetic world",
        description = "Uses an authored obstacle map. Those positions are not from the camera.",
    ),
}

private class AppServices(context: android.content.Context) {
    val performance = PerformanceMonitor()
    val detection = MediaPipeObjectDetectorEngine(context, performance)
    val guidance = GuidanceSession()
    val robot = SimulatedTricycleController()
    val hardware = MockHardwareController()
    val camera = CameraXSource()
    val voiceSettings = VoiceSettingsStore(context)
    val voiceGate = VoiceGuidanceGate()
    val speaker = AndroidVoiceSpeaker(context)
}

private const val DISCLAIMER =
    "Research and Simulation Mode. TriGuide is not a certified mobility aid and does not replace a cane, a guide, or your own judgment."

@Composable
fun TriGuideRoot() {
    val context = LocalContext.current
    val services = remember { AppServices(context.applicationContext) }
    var screen by remember { mutableStateOf(Screen.Home) }
    DisposableEffect(services) {
        onDispose {
            services.detection.close()
            services.speaker.close()
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = DISCLAIMER,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = DISCLAIMER },
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.Home -> HomeScreen(services, onNavigate = { screen = it })
                    Screen.Camera -> CameraScreen(services, onBack = { screen = Screen.Home })
                    Screen.Simulator -> SimulatorScreen(services, onBack = { screen = Screen.Home })
                    Screen.Scenarios -> ScenariosScreen(onBack = { screen = Screen.Home })
                    Screen.Diagnostics -> DiagnosticsScreen(services, onBack = { screen = Screen.Home })
                    Screen.Settings -> SettingsScreen(services, onBack = { screen = Screen.Home })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(services: AppServices, onNavigate: (Screen) -> Unit) {
    val readiness by services.detection.readiness.collectAsStateWithLifecycle()
    val detail by services.detection.statusDetail.collectAsStateWithLifecycle()
    val decision by services.guidance.decision.collectAsStateWithLifecycle()
    val voiceSettings by services.voiceSettings.settings.collectAsStateWithLifecycle()
    val voiceStatus by services.speaker.status.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "TriGuide AI",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Phone camera, on-device object detection, and a virtual three-wheel robot. Camera boxes can steer the virtual robot from their position in the image. That is not a measured clearance. No physical robot is connected.",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item { StatusLine("Mode", "Research and Simulation") }
        item { StatusLine("Object detection", "$readiness. $detail") }
        item { StatusLine("Navigation", "${decision.action.userLabel}. ${decision.perceptionLabel}") }
        item { StatusLine("Floor segmentation", "${FloorSegmentationStatus.STATE}. ${FloorSegmentationStatus.DETAIL}") }
        item { StatusLine("Voice guidance", "${voiceSettings.summary()}. $voiceStatus") }
        item { StatusLine("Virtual robot", "Kinematic simulator ready") }
        item { StatusLine("Physical hardware", services.hardware.connectionLabel) }
        item { NavButton("Start Camera", "Open the live camera preview") { onNavigate(Screen.Camera) } }
        item { NavButton("Robot Simulation", "Open the manual three-wheel simulator") { onNavigate(Screen.Simulator) } }
        item { NavButton("Test Scenarios", "Open labeled scenario placeholders") { onNavigate(Screen.Scenarios) } }
        item { NavButton("Diagnostics", "Open camera and engine diagnostics") { onNavigate(Screen.Diagnostics) } }
        item { NavButton("Settings", "Open app settings and the safety notice") { onNavigate(Screen.Settings) } }
    }
}

@Composable
private fun CameraScreen(services: AppServices, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
    }
    var cameraState by remember { mutableStateOf("Idle") }
    val readiness by services.detection.readiness.collectAsStateWithLifecycle()
    val detail by services.detection.statusDetail.collectAsStateWithLifecycle()
    val frame by services.detection.latestFrame.collectAsStateWithLifecycle()
    val inferenceError by services.detection.inferenceError.collectAsStateWithLifecycle()
    val fps by services.performance.framesPerSecond.collectAsStateWithLifecycle()
    val frames by services.performance.framesSeen.collectAsStateWithLifecycle()
    val inferenceFps by services.performance.inferenceFramesPerSecond.collectAsStateWithLifecycle()
    val lastLatency by services.performance.lastInferenceLatencyMs.collectAsStateWithLifecycle()
    val averageLatency by services.performance.averageInferenceLatencyMs.collectAsStateWithLifecycle()
    val inferences by services.performance.inferencesCompleted.collectAsStateWithLifecycle()
    val observation = DetectionReports.observation(
        readiness = readiness,
        detail = detail,
        frame = frame,
        modelName = DetectorConfig.MODEL_DISPLAY_NAME,
        scoreThreshold = DetectorConfig.SCORE_THRESHOLD,
    )
    val detectorFailed = readiness == DetectorReadiness.MODEL_MISSING || readiness == DetectorReadiness.LOAD_FAILED
    val showBoxes = readiness == DetectorReadiness.READY && frame?.fromRealDetector == true
    val decision by services.guidance.decision.collectAsStateWithLifecycle()
    val robotState by services.robot.state.collectAsStateWithLifecycle()
    val lastVoice by services.voiceGate.lastSpokenText.collectAsStateWithLifecycle()
    val applied = SteeringCommand(robotState.linearSpeedMps, robotState.steeringRadians)
    val cameraBlocked = remember { mutableStateOf(false) }
    val cameraFailed = cameraState.startsWith("Failed") || cameraState.startsWith("No camera")
    SideEffect {
        cameraBlocked.value = !granted || cameraFailed
    }
    AssistCameraDrive(services, cameraBlocked)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Live Vision", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        GuidanceReadout(decision = decision, applied = applied, showPath = true)
        Spacer(Modifier.height(8.dp))
        VoiceCueLine(lastVoice)
        Spacer(Modifier.height(8.dp))
        Text(
            observation.summary,
            color = if (detectorFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .semantics { contentDescription = observation.summary },
        )
        when {
            !granted -> {
                Text(
                    "Camera permission is required to show a live preview. Nothing is uploaded.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = "Grant camera permission" },
                ) {
                    Text("Grant camera permission", fontSize = 18.sp)
                }
            }
            else -> {
                val previewView = remember {
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                }
                LaunchedEffect(previewView) {
                    cameraState = "Starting camera"
                    services.camera.start(
                        context = context,
                        lifecycleOwner = context as LifecycleOwner,
                        previewView = previewView,
                        onFrameTimestampNs = services.performance::onFrame,
                        onAnalysisFrame = services.detection::analyze,
                        onResult = { result ->
                            cameraState = when (result) {
                                CameraStartResult.Streaming -> "Streaming"
                                is CameraStartResult.Unavailable -> result.reason
                                is CameraStartResult.Failed -> "Failed: ${result.reason}"
                            }
                        },
                    )
                }
                Text(
                    "Camera: $cameraState. ${performanceLine(fps, frames, inferenceFps, lastLatency, averageLatency, inferences)}",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                )
                inferenceError?.let { message ->
                    val stale = if (showBoxes) {
                        " Last successful boxes are still shown."
                    } else {
                        " No boxes are shown for this error."
                    }
                    Text(
                        "Inference error: $message.$stale",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                ) {
                    AndroidView(
                        factory = { previewView },
                        update = { view ->
                            val guidance = decision.spokenCue
                            view.contentDescription = when (readiness) {
                                DetectorReadiness.READY ->
                                    "Live camera preview. ${observation.summary} $guidance"
                                DetectorReadiness.LOADING ->
                                    "Live camera preview. Detection model is loading. No boxes yet. $guidance"
                                DetectorReadiness.MODEL_MISSING ->
                                    "Live camera preview. Detection model is missing. No boxes. $guidance"
                                DetectorReadiness.LOAD_FAILED ->
                                    "Live camera preview. Detection model failed to load. No boxes. $guidance"
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    DetectionOverlay(
                        frame = frame,
                        showBoxes = showBoxes,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (cameraState == "Starting camera" || readiness == DetectorReadiness.LOADING) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .semantics { contentDescription = "Camera or detection model is starting" },
                        )
                    }
                    if (cameraState.startsWith("Failed") || cameraState.startsWith("No camera") || detectorFailed) {
                        Text(
                            if (detectorFailed) observation.summary else cameraState,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BackButton(onBack)
    }

    DisposableEffect(Unit) {
        onDispose {
            services.camera.stop()
            services.detection.clearFrame()
        }
    }
}

@Composable
private fun AssistCameraDrive(services: AppServices, cameraBlocked: MutableState<Boolean>) {
    LaunchedEffect(services) {
        var last = android.os.SystemClock.elapsedRealtime()
        val startedAt = last
        var logged = ""
        try {
            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                val dt = ((now - last) / 1000.0).coerceIn(0.0, 0.1)
                last = now
                val scene = SceneObservations.fromCamera(
                    readiness = services.detection.readiness.value,
                    frame = services.detection.latestFrame.value,
                    nowElapsedMs = now,
                )
                val decision = services.guidance.onCamera(scene)
                services.robot.setCommand(decision.toSteeringCommand())
                services.robot.tick(dt)
                val fromDecision = VoiceCues.fromDecision(decision)
                val cue = when {
                    cameraBlocked.value -> VoiceCues.cameraUnavailable()
                    fromDecision?.kind == VoiceAlertKind.CAMERA_UNAVAILABLE &&
                        now - startedAt < VoiceConfig.CAMERA_START_GRACE_MS -> null
                    else -> fromDecision
                }
                announce(services, cue, now)
                val key = "${decision.action.name}:${decision.reason}"
                if (key != logged) {
                    logged = key
                    android.util.Log.i("TriGuideNav", "${decision.perceptionLabel} ${decision.action} ${decision.reason}")
                }
                delay(50)
            }
        } finally {
            services.guidance.resetCamera()
            services.robot.setCommand(SteeringCommand(0.0, 0.0))
            services.guidance.markIdle("Camera preview is closed. The virtual robot is holding.")
            announce(services, VoiceCues.paused(), android.os.SystemClock.elapsedRealtime())
        }
    }
}

@Composable
private fun SimulatorScreen(services: AppServices, onBack: () -> Unit) {
    var mode by remember { mutableStateOf(RobotDriveMode.Manual) }
    val robotState by services.robot.state.collectAsStateWithLifecycle()
    val guided by services.guidance.decision.collectAsStateWithLifecycle()
    val lastVoice by services.voiceGate.lastSpokenText.collectAsStateWithLifecycle()
    val applied = SteeringCommand(robotState.linearSpeedMps, robotState.steeringRadians)
    val readout = if (mode == RobotDriveMode.Manual) NavigationDecisions.manual() else guided
    LaunchedEffect(mode) {
        var last = android.os.SystemClock.elapsedRealtime()
        val assisted = mode != RobotDriveMode.Manual
        try {
            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                val dt = ((now - last) / 1000.0).coerceIn(0.0, 0.1)
                last = now
                val cue = when (mode) {
                    RobotDriveMode.Manual -> {
                        services.robot.tick(dt)
                        null
                    }
                    RobotDriveMode.CameraInformed -> {
                        val scene = SceneObservations.fromCamera(
                            readiness = services.detection.readiness.value,
                            frame = services.detection.latestFrame.value,
                            nowElapsedMs = now,
                        )
                        val decision = services.guidance.onCamera(scene)
                        services.robot.setCommand(decision.toSteeringCommand())
                        services.robot.tick(dt)
                        VoiceCues.fromDecision(decision)
                    }
                    RobotDriveMode.SyntheticWorld -> {
                        val scene = SyntheticObstacleWorld.observe(services.robot.state.value.pose)
                        val decision = services.guidance.onSynthetic(scene)
                        services.robot.setCommand(decision.toSteeringCommand())
                        services.robot.tick(dt)
                        VoiceCues.fromDecision(decision)
                    }
                }
                if (assisted) {
                    announce(services, cue, now)
                }
                delay(50)
            }
        } finally {
            if (mode == RobotDriveMode.CameraInformed) {
                services.guidance.resetCamera()
            }
            if (mode == RobotDriveMode.SyntheticWorld) {
                services.guidance.resetSynthetic()
            }
            if (assisted) {
                services.robot.setCommand(SteeringCommand(0.0, 0.0))
                services.guidance.markIdle("Assist mode stopped. The virtual robot is holding.")
                announce(services, VoiceCues.paused(), android.os.SystemClock.elapsedRealtime())
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Robot Simulation", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Virtual tricycle. Wheelbase ${TricycleKinematics.DEFAULT_WHEELBASE_METERS} m. ${mode.description}",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        RobotDriveMode.entries.forEach { entry ->
            val selected = mode == entry
            if (selected) {
                Button(
                    onClick = { mode = entry },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = "${entry.label}, selected" },
                ) {
                    Text("${entry.label}, selected", fontSize = 18.sp)
                }
            } else {
                OutlinedButton(
                    onClick = { mode = entry },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = entry.label },
                ) {
                    Text(entry.label, fontSize = 18.sp)
                }
            }
        }
        GuidanceReadout(decision = readout, applied = applied, showPath = false)
        VoiceCueLine(lastVoice)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .semantics {
                    contentDescription = "Top-down drawing of the virtual robot. ${IllustrativePath.LABEL}"
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val scale = 70f
            drawCircle(color = androidx.compose.ui.graphics.Color(0xFF333333), radius = 8f, center = center)
            rotate(degrees = Math.toDegrees(-robotState.pose.headingRadians).toFloat(), pivot = center) {
                val sketch = IllustrativePath.preview(applied)
                val trail = Path()
                sketch.forEachIndexed { index, pose ->
                    val x = center.x - (pose.yMeters * scale).toFloat()
                    val y = center.y - (pose.xMeters * scale).toFloat()
                    if (index == 0) trail.moveTo(x, y) else trail.lineTo(x, y)
                }
                drawPath(
                    trail,
                    color = androidx.compose.ui.graphics.Color(0xFFBDBDBD),
                    style = Stroke(width = 4f),
                )
                val body = Path().apply {
                    moveTo(center.x, center.y - 70f)
                    lineTo(center.x - 28f, center.y + 36f)
                    lineTo(center.x + 28f, center.y + 36f)
                    close()
                }
                drawPath(body, color = androidx.compose.ui.graphics.Color(0xFFFFE14A))
            }
        }
        Text(
            "x ${format(robotState.pose.xMeters)} m, y ${format(robotState.pose.yMeters)} m, heading ${format(Math.toDegrees(robotState.pose.headingRadians))} degrees",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        if (mode == RobotDriveMode.Manual) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallAction("Forward", "Drive the virtual robot forward") {
                    services.robot.setCommand(SteeringCommand(0.6, robotState.steeringRadians))
                }
                SmallAction("Back", "Drive the virtual robot backward") {
                    services.robot.setCommand(SteeringCommand(-0.4, robotState.steeringRadians))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallAction("Steer left", "Steer the virtual front wheel left") {
                    services.robot.setCommand(SteeringCommand(robotState.linearSpeedMps, 0.35))
                }
                SmallAction("Steer right", "Steer the virtual front wheel right") {
                    services.robot.setCommand(SteeringCommand(robotState.linearSpeedMps, -0.35))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallAction("Stop", "Stop the virtual robot") {
                    services.robot.setCommand(SteeringCommand(0.0, 0.0))
                }
                SmallAction("Reset", "Reset the virtual robot pose") {
                    services.robot.reset()
                }
            }
        } else {
            Text(
                "Manual buttons are off while this mode is driving. Reset is still available.",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
            )
            Button(
                onClick = { services.robot.reset() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = "Reset the virtual robot pose" },
            ) {
                Text("Reset pose", fontSize = 18.sp)
            }
        }
        BackButton(onBack)
    }
}

@Composable
private fun ScenariosScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Test Scenarios", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "These cards are not an indoor evaluation. They do not inject detections. The synthetic map and the navigation unit tests are separate from this list.",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        ScenarioCard("Straight corridor", "NOT_STARTED. Not an evaluation set.")
        ScenarioCard("Obstacle on the left", "NOT_STARTED. This card does not inject an obstacle.")
        ScenarioCard("Blocked path", "NOT_STARTED. This card does not inject a detection.")
        BackButton(onBack)
    }
}

@Composable
private fun DiagnosticsScreen(services: AppServices, onBack: () -> Unit) {
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val readiness by services.detection.readiness.collectAsStateWithLifecycle()
    val detail by services.detection.statusDetail.collectAsStateWithLifecycle()
    val frame by services.detection.latestFrame.collectAsStateWithLifecycle()
    val inferenceError by services.detection.inferenceError.collectAsStateWithLifecycle()
    val fps by services.performance.framesPerSecond.collectAsStateWithLifecycle()
    val frames by services.performance.framesSeen.collectAsStateWithLifecycle()
    val inferenceFps by services.performance.inferenceFramesPerSecond.collectAsStateWithLifecycle()
    val lastLatency by services.performance.lastInferenceLatencyMs.collectAsStateWithLifecycle()
    val averageLatency by services.performance.averageInferenceLatencyMs.collectAsStateWithLifecycle()
    val inferences by services.performance.inferencesCompleted.collectAsStateWithLifecycle()
    val robot by services.robot.state.collectAsStateWithLifecycle()
    val observation = DetectionReports.observation(
        readiness = readiness,
        detail = detail,
        frame = frame,
        modelName = DetectorConfig.MODEL_DISPLAY_NAME,
        scoreThreshold = DetectorConfig.SCORE_THRESHOLD,
    )
    val smoothed by services.guidance.decision.collectAsStateWithLifecycle()
    val voiceSettings by services.voiceSettings.settings.collectAsStateWithLifecycle()
    val voiceStatus by services.speaker.status.collectAsStateWithLifecycle()
    val lastVoice by services.voiceGate.lastSpokenText.collectAsStateWithLifecycle()
    val raw = services.guidance.rawDecision(
        SceneObservations.fromCamera(
            readiness = readiness,
            frame = frame,
            nowElapsedMs = android.os.SystemClock.elapsedRealtime(),
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        StatusLine("Version", BuildConfig.VERSION_NAME)
        StatusLine("Camera permission", if (granted) "granted" else "not granted")
        StatusLine("Detection engine", readiness.name)
        StatusLine("Detection detail", detail)
        StatusLine("Real detector loaded", (readiness == DetectorReadiness.READY).toString())
        StatusLine("Observation", observation.summary)
        StatusLine("Observation from a real result", observation.realDetector.toString())
        StatusLine("Smoothed decision", "${smoothed.action.userLabel}. ${smoothed.reason}")
        StatusLine("Smoothed confidence", NavigationDecisions.formatConfidence(smoothed.confidence))
        StatusLine("Raw decision", "${raw.action.userLabel}. ${raw.reason}")
        StatusLine("Metric depth used", smoothed.metricDepthUsed.toString())
        StatusLine("Vision sample", smoothed.basedOnRealVision.toString())
        StatusLine("Floor segmentation", "${FloorSegmentationStatus.STATE}. ${FloorSegmentationStatus.DETAIL}")
        StatusLine("Voice guidance", voiceSettings.summary())
        StatusLine("Voice engine", voiceStatus)
        StatusLine("Last voice cue", lastVoice)
        StatusLine(
            "Performance",
            performanceLine(fps, frames, inferenceFps, lastLatency, averageLatency, inferences),
        )
        StatusLine("Preview frames seen", frames.toString())
        StatusLine("Inferences completed", inferences.toString())
        StatusLine("Inference error", inferenceError ?: "none")
        StatusLine("Hardware", services.hardware.connectionLabel)
        StatusLine("Hardware accepts commands", services.hardware.dispatch(SteeringCommand(0.0, 0.0)).accepted.toString())
        StatusLine("Sim speed m/s", format(robot.linearSpeedMps))
        BackButton(onBack)
    }
}

@Composable
private fun SettingsScreen(services: AppServices, onBack: () -> Unit) {
    val settings by services.voiceSettings.settings.collectAsStateWithLifecycle()
    val engineStatus by services.speaker.status.collectAsStateWithLifecycle()
    val lastVoice by services.voiceGate.lastSpokenText.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        StatusLine("Research and Simulation Mode", "On. This cannot be turned off in this build.")
        VoiceSettingsSection(
            settings = settings,
            engineStatus = engineStatus,
            lastSpoken = lastVoice,
            onSpeechEnabled = services.voiceSettings::setSpeechEnabled,
            onQuietMode = services.voiceSettings::setQuietMode,
            onSpeechRate = services.voiceSettings::setSpeechRate,
            onLanguage = services.voiceSettings::setLanguage,
            onPlaySample = { playSample(services) },
        )
        StatusLine("Object detection", "${DetectorConfig.MODEL_DISPLAY_NAME} on device. Confidence floor ${DetectorConfig.SCORE_THRESHOLD}.")
        StatusLine("Navigation", "Camera mode uses box position in the image. It does not measure distance.")
        StatusLine("Floor segmentation", "${FloorSegmentationStatus.STATE}. ${FloorSegmentationStatus.DETAIL}")
        StatusLine("Cloud camera upload", "Off. This build does not request internet access.")
        StatusLine("TalkBack", "Buttons, the camera preview, and detection boxes have spoken labels. Voice cues are separate from TalkBack.")
        StatusLine("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text(
            DISCLAIMER,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
        )
        BackButton(onBack)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        modifier = Modifier.semantics { contentDescription = "$label: $value" },
    )
}

@Composable
private fun NavButton(label: String, description: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description },
    ) {
        Text(label, fontSize = 18.sp)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "Back to home" },
    ) {
        Text("Back to home", fontSize = 18.sp)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SmallAction(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description },
    ) {
        Text(label, fontSize = 16.sp)
    }
}

@Composable
private fun ScenarioCard(title: String, body: String) {
    Column(modifier = Modifier.semantics { contentDescription = "$title. $body" }) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
        Text(body, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    }
}

private fun announce(services: AppServices, cue: VoiceCue?, nowElapsedMs: Long) {
    val settings = services.voiceSettings.current()
    val directive = services.voiceGate.consider(
        VoiceGateInput(
            cue = cue,
            settings = settings,
            nowElapsedMs = nowElapsedMs,
            resolvedLanguage = services.speaker.resolve(settings.language),
        ),
    )
    if (directive is SpeechDirective.Speak) {
        val accepted = services.speaker.speak(directive) {
            services.voiceGate.onUtteranceFinished(android.os.SystemClock.elapsedRealtime())
        }
        if (!accepted) {
            services.voiceGate.abandonLastUtterance(nowElapsedMs)
        }
    }
}

private fun playSample(services: AppServices) {
    val settings = services.voiceSettings.current().sanitized()
    if (!settings.speechEnabled) return
    val language = services.speaker.resolve(settings.language)
    services.speaker.speak(
        SpeechDirective.Speak(
            text = VoiceCues.sample(language),
            interrupt = true,
            speechRate = settings.speechRate,
            kind = VoiceAlertKind.OBSTACLE_DETECTED,
            language = language,
        ),
    ) {}
}

private fun format(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

private fun performanceLine(
    previewFps: Double,
    previewFrames: Long,
    inferenceFps: Double?,
    lastLatencyMs: Double?,
    averageLatencyMs: Double?,
    inferences: Long,
): String {
    val preview = when {
        previewFrames == 0L -> "Preview FPS not measured yet"
        previewFps == 0.0 -> "Preview frames seen: $previewFrames. FPS window not complete yet"
        else -> "Preview ${previewFps.roundToInt()} FPS"
    }
    val inference = when {
        inferences == 0L || lastLatencyMs == null -> "Inference latency not measured yet"
        inferenceFps == null ->
            "Last inference ${lastLatencyMs.roundToInt()} ms. Inference FPS not measured yet"
        else ->
            "Inference ${inferenceFps.roundToInt()} FPS, last ${lastLatencyMs.roundToInt()} ms, average ${averageLatencyMs?.roundToInt() ?: lastLatencyMs.roundToInt()} ms"
    }
    return "$preview. $inference."
}
