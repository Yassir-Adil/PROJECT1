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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
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
import com.triguide.ai.domain.HoldNavigationPolicy
import com.triguide.ai.domain.MockHardwareController
import com.triguide.ai.domain.PerformanceMonitor
import com.triguide.ai.domain.SteeringCommand
import com.triguide.ai.robot.SimulatedTricycleController
import com.triguide.ai.sim.TricycleKinematics
import com.triguide.ai.vision.MediaPipeObjectDetectorEngine
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class Screen {
    Home,
    Camera,
    Simulator,
    Scenarios,
    Diagnostics,
    Settings,
}

private class AppServices(context: android.content.Context) {
    val performance = PerformanceMonitor()
    val detection = MediaPipeObjectDetectorEngine(context, performance)
    val navigation = HoldNavigationPolicy()
    val robot = SimulatedTricycleController()
    val hardware = MockHardwareController()
    val camera = CameraXSource()
}

private const val DISCLAIMER =
    "Research and Simulation Mode. TriGuide is not a certified mobility aid and does not replace a cane, a guide, or your own judgment."

@Composable
fun TriGuideRoot() {
    val context = LocalContext.current
    val services = remember { AppServices(context.applicationContext) }
    var screen by remember { mutableStateOf(Screen.Home) }
    DisposableEffect(services) {
        onDispose { services.detection.close() }
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
                    Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(services: AppServices, onNavigate: (Screen) -> Unit) {
    val readiness by services.detection.readiness.collectAsStateWithLifecycle()
    val detail by services.detection.statusDetail.collectAsStateWithLifecycle()
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
                text = "Phone camera, on-device object detection, and a virtual three-wheel robot. Detection does not drive the robot. No physical robot is connected.",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item { StatusLine("Mode", "Research and Simulation") }
        item { StatusLine("Object detection", "$readiness. $detail") }
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Live Vision", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
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
                        .weight(1f),
                ) {
                    AndroidView(
                        factory = { previewView },
                        update = { view ->
                            view.contentDescription = when (readiness) {
                                DetectorReadiness.READY ->
                                    "Live camera preview. ${observation.summary}"
                                DetectorReadiness.LOADING ->
                                    "Live camera preview. Detection model is loading. No boxes yet."
                                DetectorReadiness.MODEL_MISSING ->
                                    "Live camera preview. Detection model is missing. No boxes."
                                DetectorReadiness.LOAD_FAILED ->
                                    "Live camera preview. Detection model failed to load. No boxes."
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
private fun SimulatorScreen(services: AppServices, onBack: () -> Unit) {
    val robotState by services.robot.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            services.robot.tick(0.05)
            delay(50)
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
            "Manual tricycle model. Vision is not driving this robot. Wheelbase ${TricycleKinematics.DEFAULT_WHEELBASE_METERS} m.",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .semantics {
                    contentDescription = "Top-down drawing of the virtual robot heading"
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = androidx.compose.ui.graphics.Color(0xFF333333), radius = 8f, center = center)
            rotate(degrees = Math.toDegrees(-robotState.pose.headingRadians).toFloat(), pivot = center) {
                val path = Path().apply {
                    moveTo(center.x, center.y - 70f)
                    lineTo(center.x - 28f, center.y + 36f)
                    lineTo(center.x + 28f, center.y + 36f)
                    close()
                }
                drawPath(path, color = androidx.compose.ui.graphics.Color(0xFFFFE14A))
            }
        }
        Text(
            "x ${format(robotState.pose.xMeters)} m, y ${format(robotState.pose.yMeters)} m, heading ${format(Math.toDegrees(robotState.pose.headingRadians))} degrees",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        Text(
            "speed ${format(robotState.linearSpeedMps)} m/s, steer ${format(Math.toDegrees(robotState.steeringRadians))} degrees",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
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
            "These labels have not been run. Live Vision can show real boxes when the model loads. These cards do not inject detections.",
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
    val decision = services.navigation.decide(observation)
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
        StatusLine("Navigation cue", decision.spokenCue)
        StatusLine("Vision steers the robot", decision.basedOnRealVision.toString())
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
private fun SettingsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        StatusLine("Research and Simulation Mode", "On. This cannot be turned off in this build.")
        StatusLine("Object detection", "${DetectorConfig.MODEL_DISPLAY_NAME} on device. Confidence floor ${DetectorConfig.SCORE_THRESHOLD}.")
        StatusLine("Cloud camera upload", "Off. This build does not request internet access.")
        StatusLine("TalkBack", "Buttons, the camera preview, and detection boxes have spoken labels.")
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
