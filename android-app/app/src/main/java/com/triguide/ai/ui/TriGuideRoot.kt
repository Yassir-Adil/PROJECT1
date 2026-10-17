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
import com.triguide.ai.domain.HoldNavigationPolicy
import com.triguide.ai.domain.MockHardwareController
import com.triguide.ai.domain.PerformanceMonitor
import com.triguide.ai.domain.SteeringCommand
import com.triguide.ai.domain.UnavailableObjectDetectionEngine
import com.triguide.ai.robot.SimulatedTricycleController
import com.triguide.ai.sim.TricycleKinematics
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

private class AppServices {
    val detection = UnavailableObjectDetectionEngine()
    val navigation = HoldNavigationPolicy()
    val robot = SimulatedTricycleController()
    val hardware = MockHardwareController()
    val performance = PerformanceMonitor()
    val camera = CameraXSource()
}

private const val DISCLAIMER =
    "Research and Simulation Mode. TriGuide is not a certified mobility aid and does not replace a cane, a guide, or your own judgment."

@Composable
fun TriGuideRoot() {
    val services = remember { AppServices() }
    var screen by remember { mutableStateOf(Screen.Home) }
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
                text = "Phone camera, on-device guidance later, and a virtual three-wheel robot. No physical robot is connected.",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item { StatusLine("Mode", "Research and Simulation") }
        item { StatusLine("Object detection", services.detection.statusLabel) }
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
    val fps by services.performance.framesPerSecond.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Live Vision", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Camera preview only. Object detection is not running.",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 8.dp),
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
                        contentDescription = "Live camera preview. Object detection is off."
                    }
                }
                LaunchedEffect(previewView) {
                    cameraState = "Starting camera"
                    services.camera.start(
                        context = context,
                        lifecycleOwner = context as LifecycleOwner,
                        previewView = previewView,
                        onFrameTimestampNs = services.performance::onFrame,
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
                    "Status: $cameraState. Preview frames per second: ${fps.roundToInt()}.",
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (cameraState == "Starting camera") {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .semantics { contentDescription = "Camera is starting" },
                        )
                    }
                    if (cameraState.startsWith("Failed") || cameraState.startsWith("No camera")) {
                        Text(
                            cameraState,
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
        onDispose { services.camera.stop() }
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
            "These are labels only. None of them have been run, and none of them produce detection results.",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        ScenarioCard("Straight corridor", "NOT_STARTED. Placeholder for a later synthetic path.")
        ScenarioCard("Obstacle on the left", "NOT_STARTED. No detector is attached.")
        ScenarioCard("Blocked path", "NOT_STARTED. No detector is attached.")
        BackButton(onBack)
    }
}

@Composable
private fun DiagnosticsScreen(services: AppServices, onBack: () -> Unit) {
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val fps by services.performance.framesPerSecond.collectAsStateWithLifecycle()
    val frames by services.performance.framesSeen.collectAsStateWithLifecycle()
    val robot by services.robot.state.collectAsStateWithLifecycle()
    val decision = services.navigation.decide(services.detection.currentObservation())
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
        StatusLine("Detection engine", services.detection.statusLabel)
        StatusLine("Real detector", services.detection.isRealDetector.toString())
        StatusLine("Observation", services.detection.currentObservation().summary)
        StatusLine("Navigation cue", decision.spokenCue)
        StatusLine("Preview FPS", fps.roundToInt().toString())
        StatusLine("Preview frames seen", frames.toString())
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
        StatusLine("Cloud camera upload", "Off. This build does not request internet access.")
        StatusLine("TalkBack", "Buttons and the camera preview have spoken labels.")
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
