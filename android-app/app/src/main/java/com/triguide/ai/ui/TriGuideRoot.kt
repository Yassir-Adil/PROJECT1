package com.triguide.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triguide.ai.BuildConfig
import com.triguide.ai.camera.CameraXSource
import com.triguide.ai.domain.DiagnosticsReport
import com.triguide.ai.domain.DiagnosticsSnapshot
import com.triguide.ai.domain.CameraPermissionAction
import com.triguide.ai.domain.CameraPermissionPrompt
import com.triguide.ai.domain.CameraStartResult
import com.triguide.ai.domain.GuidanceSpeech
import com.triguide.ai.domain.SessionSpeechTracker
import com.triguide.ai.domain.DetectionReports
import com.triguide.ai.domain.DetectorStatusText
import com.triguide.ai.domain.DetectorConfig
import com.triguide.ai.domain.DetectorReadiness
import com.triguide.ai.domain.FloorSegmentationStatus
import com.triguide.ai.domain.GuidanceScenarios
import com.triguide.ai.domain.GuidanceSession
import com.triguide.ai.domain.IllustrativePath
import com.triguide.ai.domain.MockHardwareController
import com.triguide.ai.domain.navigationCueForSpeech
import com.triguide.ai.domain.NavigationDecisions
import com.triguide.ai.domain.PerformanceMonitor
import com.triguide.ai.domain.SceneObservations
import com.triguide.ai.domain.SpeechDirective
import com.triguide.ai.domain.SteeringCommand
import com.triguide.ai.domain.SyntheticObstacleWorld
import com.triguide.ai.domain.TripleTapDetector
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAP_LOG = "TriGuideTap"

private enum class Screen {
    LiveVision,
    More,
    Simulator,
    Scenarios,
    Diagnostics,
    Settings,
}

private data class CameraPrompt(val serial: Int = 0, val fromGesture: Boolean = false)

/**
 * Held as one object so key-event collection does not close over a stale screen value.
 * Cold start stays on Live Vision with epoch 0. Later starts increment the epoch.
 */
private class LiveNavState {
    var screen by mutableStateOf(Screen.LiveVision)
    var running by mutableStateOf(true)
    var epoch by mutableIntStateOf(0)
    var prompt by mutableStateOf(CameraPrompt())

    fun start(fromGesture: Boolean) {
        running = true
        screen = Screen.LiveVision
        epoch += 1
        prompt = CameraPrompt(serial = prompt.serial + 1, fromGesture = fromGesture)
    }

    fun pause() {
        running = false
    }

    fun showMore() {
        screen = Screen.More
        running = false
    }
}

private enum class RobotDriveMode(val label: String, val description: String) {
    Manual(
        label = "Manual drive",
        description = "Simulated. Buttons set speed and the front wheel. Not the camera, and not a physical robot.",
    ),
    CameraInformed(
        label = "Camera-informed",
        description = "Simulated robot. Uses detection boxes from Live Vision when that camera is already running. This screen does not open the camera. A box is not a measured clearance.",
    ),
    SyntheticWorld(
        label = "Synthetic world",
        description = "Simulated map with authored obstacles. Those positions are not from the camera and not from a physical robot.",
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

@Composable
fun TriGuideRoot() {
    val context = LocalContext.current
    val services = remember { AppServices(context.applicationContext) }
    val sessionSpeech = remember { SessionSpeechTracker() }
    val nav = remember { LiveNavState() }
    val scope = rememberCoroutineScope()
    fun speakPaused() {
        scope.launch {
            speakWhenReady(
                speaker = services.speaker,
                settings = services.voiceSettings::current,
                line = GuidanceSpeech.guidancePaused,
                interrupt = true,
            )
        }
    }
    BackHandler(enabled = nav.screen != Screen.LiveVision) {
        nav.start(fromGesture = false)
    }
    DisposableEffect(services) {
        onDispose {
            services.detection.close()
            services.speaker.close()
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (nav.screen) {
                Screen.LiveVision -> CameraScreen(
                    services = services,
                    running = nav.running,
                    epoch = nav.epoch,
                    prompt = nav.prompt,
                    sessionSpeech = sessionSpeech,
                    onMore = {
                        val wasRunning = nav.running
                        nav.showMore()
                        if (wasRunning) speakPaused()
                    },
                    onRestartGuidance = {
                        Log.i(TAP_LOG, "triple-tap accepted; restarting guidance")
                        confirmPulse(context)
                        nav.start(fromGesture = true)
                    },
                )
                Screen.More -> MoreScreen(
                    services = services,
                    onLiveGuidance = { nav.start(fromGesture = false) },
                    onNavigate = { nav.screen = it },
                )
                Screen.Simulator -> SimulatorScreen(services, onBack = { nav.start(fromGesture = false) })
                Screen.Scenarios -> ScenariosScreen(onBack = { nav.start(fromGesture = false) })
                Screen.Diagnostics -> DiagnosticsScreen(services, onBack = { nav.start(fromGesture = false) })
                Screen.Settings -> SettingsScreen(services, onBack = { nav.start(fromGesture = false) })
            }
        }
    }
}

@Composable
private fun MoreScreen(
    services: AppServices,
    onLiveGuidance: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val readiness by services.detection.readiness.collectAsStateWithLifecycle()
    val detail by services.detection.statusDetail.collectAsStateWithLifecycle()
    val decision by services.guidance.decision.collectAsStateWithLifecycle()
    val voiceSettings by services.voiceSettings.settings.collectAsStateWithLifecycle()
    val voiceStatus by services.speaker.status.collectAsStateWithLifecycle()
    val robot by services.robot.state.collectAsStateWithLifecycle()
    val detectionLine = DetectorStatusText.line(readiness, detail)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "More",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics {
                    contentDescription = "More. Simulator, settings, and diagnostics."
                },
            )
            Text(
                text = "Live guidance is the main screen. It opens the rear camera and speaks cues. This screen is for the simulator, settings, and diagnostics. A box is a position in the image. It is not a measured clearance. No physical robot is connected.",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item { StatusLine("Mode", "Research and Simulation") }
        item { StatusLine("Detection", detectionLine) }
        item { StatusLine("Decision", decision.action.userLabel) }
        item { StatusLine("Reason", decision.reason) }
        item { StatusLine("Front wheel", "${formatWheelDegrees(robot.steeringRadians)} degrees") }
        item { StatusLine("Floor segmentation", "${FloorSegmentationStatus.STATE}. ${FloorSegmentationStatus.DETAIL}") }
        item { StatusLine("Voice", "${voiceSettings.summary()}. $voiceStatus") }
        item { StatusLine("Virtual robot", "Kinematic simulator ready. Speed ${formatSpeedMps(robot.linearSpeedMps)} m/s.") }
        item { StatusLine("Physical hardware", services.hardware.connectionLabel) }
        item {
            Text(
                text = GuidanceSpeech.GESTURE_ON_SCREEN,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                modifier = Modifier.semantics { contentDescription = GuidanceSpeech.GESTURE_DESCRIPTION },
            )
        }
        item { NavButton("Live guidance", "Return to live guidance and start the camera", primary = true) { onLiveGuidance() } }
        item { NavButton("Robot Simulation", "Open the three-wheel simulator", primary = false) { onNavigate(Screen.Simulator) } }
        item { NavButton("Test Scenarios", "Open simulator scenarios. They use an authored map, not the camera.", primary = false) { onNavigate(Screen.Scenarios) } }
        item { NavButton("Diagnostics", "Open camera, detection, decision, and voice status", primary = false) { onNavigate(Screen.Diagnostics) } }
        item { NavButton("Settings", "Open voice, quiet mode, and the safety notice", primary = false) { onNavigate(Screen.Settings) } }
    }
}

@Composable
private fun CameraScreen(
    services: AppServices,
    running: Boolean,
    epoch: Int,
    prompt: CameraPrompt,
    sessionSpeech: SessionSpeechTracker,
    onMore: () -> Unit,
    onRestartGuidance: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var requestAttempted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        requestAttempted = true
        granted = allowed
        if (allowed) return@rememberLauncherForActivityResult
        val rationale = (context as? android.app.Activity)
            ?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
        val action = CameraPermissionPrompt.action(requestAttempted = true, shouldShowRationale = rationale)
        scope.launch {
            if (action == CameraPermissionAction.OpenAppSettings) {
                speakWhenReady(
                    speaker = services.speaker,
                    settings = services.voiceSettings::current,
                    line = GuidanceSpeech.openingSettings,
                    interrupt = true,
                )
                openAppSettings(context)
            } else {
                speakWhenReady(
                    speaker = services.speaker,
                    settings = services.voiceSettings::current,
                    line = GuidanceSpeech.permissionDenied,
                    interrupt = true,
                )
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasCameraPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val tapDetector = remember { TripleTapDetector() }
    val activity = context as? android.app.Activity
    val permissionAction = CameraPermissionPrompt.action(
        requestAttempted = requestAttempted,
        shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true,
    )
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
        cameraBlocked.value = !granted || !running || cameraFailed
    }
    if (running && granted) {
        SideEffect {
            if (!sessionSpeech.disclaimerDelivered && sessionSpeech.suppressNavigationUntilMs == 0L) {
                sessionSpeech.suppressNavigationUntilMs = Long.MAX_VALUE
            }
        }
        AssistCameraDrive(services, cameraBlocked, sessionSpeech)
    }
    LaunchedEffect(prompt.serial, running) {
        if (!running || hasCameraPermission(context)) return@LaunchedEffect
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
        val action = CameraPermissionPrompt.action(requestAttempted, rationale)
        if (action == CameraPermissionAction.OpenAppSettings) {
            speakWhenReady(
                speaker = services.speaker,
                settings = services.voiceSettings::current,
                line = GuidanceSpeech.openingSettings,
                interrupt = true,
            )
            openAppSettings(context)
            return@LaunchedEffect
        }
        val ask = scope.launch {
            val line = if (requestAttempted) {
                GuidanceSpeech.permissionDenied
            } else {
                GuidanceSpeech.permissionRequired
            }
            speakWhenReady(
                speaker = services.speaker,
                settings = services.voiceSettings::current,
                line = line,
                interrupt = true,
            )
        }
        launcher.launch(Manifest.permission.CAMERA)
        if (granted || requestAttempted) ask.cancel()
    }
    LaunchedEffect(epoch, granted, running) {
        if (!running || !granted) return@LaunchedEffect
        if (!prompt.fromGesture) confirmPulse(context)
        sessionSpeech.suppressNavigationUntilMs = Long.MAX_VALUE
        try {
            var attempts = 0
            while (isActive && attempts < 40) {
                attempts++
                if (!services.voiceSettings.current().speechEnabled) {
                    sessionSpeech.suppressNavigationUntilMs = 0L
                    return@LaunchedEffect
                }
                if (services.speaker.ready.value) {
                    val lines = sessionSpeech.guidanceStartLines()
                    val includesDisclaimer = lines.any { it.id == GuidanceSpeech.disclaimer.id }
                    val accepted = speakLinesInOrder(
                        speaker = services.speaker,
                        settings = services.voiceSettings::current,
                        lines = lines,
                    )
                    if (accepted) {
                        if (includesDisclaimer) sessionSpeech.disclaimerDelivered = true
                        sessionSpeech.suppressAfter(lines, android.os.SystemClock.elapsedRealtime())
                        return@LaunchedEffect
                    }
                }
                delay(250)
            }
            sessionSpeech.suppressNavigationUntilMs = android.os.SystemClock.elapsedRealtime() + 2_000L
        } finally {
            if (sessionSpeech.suppressNavigationUntilMs == Long.MAX_VALUE) {
                sessionSpeech.suppressNavigationUntilMs = 0L
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .observeScreenTaps {
                val mark = tapDetector.onTap(android.os.SystemClock.elapsedRealtime())
                if (mark.completed) {
                    onRestartGuidance()
                } else {
                    Log.i(TAP_LOG, "tap count=${mark.countInGroup}")
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Live Vision",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .pointerInput(onMore) {
                    detectTapGestures(onLongPress = { onMore() })
                }
                .semantics {
                    contentDescription = "Live Vision. ${GuidanceSpeech.GESTURE_DESCRIPTION}"
                },
        )
        Text(
            text = GuidanceSpeech.disclaimer.english,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier.semantics { contentDescription = GuidanceSpeech.disclaimer.english },
        )
        Text(
            text = GuidanceSpeech.GESTURE_ON_SCREEN,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            modifier = Modifier.semantics { contentDescription = GuidanceSpeech.GESTURE_DESCRIPTION },
        )
        Text(
            DetectorStatusText.line(readiness, detail),
            color = if (detectorFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier.semantics {
                contentDescription = DetectorStatusText.line(readiness, detail)
            },
        )
        when {
            !running -> {
                Text(
                    text = "Guidance paused. Leave this screen with Back to start the camera and voice cues again.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    modifier = Modifier.semantics {
                        contentDescription = "Guidance paused. Use the phone Back control to return to live guidance."
                    },
                )
            }
            !granted -> {
                val openSettings = permissionAction == CameraPermissionAction.OpenAppSettings
                Text(
                    if (openSettings) {
                        "Camera permission is off, and Android will not ask again from this screen. Open Settings and allow Camera for TriGuide. Nothing is uploaded."
                    } else {
                        "Camera permission is required to show a live preview. Nothing is uploaded."
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (openSettings) {
                            openAppSettings(context)
                        } else {
                            launcher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics {
                            contentDescription = if (openSettings) {
                                "Open app settings to allow the camera"
                            } else {
                                "Grant camera permission"
                            }
                        },
                ) {
                    Text(
                        if (openSettings) "Open app settings" else "Grant camera permission",
                        fontSize = 18.sp,
                    )
                }
            }
            else -> {
                val previewView = remember {
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                }
                DisposableEffect(previewView) {
                    onDispose { services.camera.stop() }
                }
                LaunchedEffect(previewView, epoch) {
                    services.camera.stop()
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
                DecisionHeadline(decision = decision, applied = applied)
                Text(
                    text = "Source: ${decision.perceptionLabel}. Hold and Unknown stop the virtual robot. Depth is not measured.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    modifier = Modifier.semantics {
                        contentDescription = "Source ${decision.perceptionLabel}. Hold and Unknown stop the virtual robot. Depth is not measured."
                    },
                )
                VoiceCueLine(lastVoice)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
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
        Button(
            onClick = onMore,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics {
                    contentDescription = "More. Simulator, settings, and diagnostics."
                },
        ) {
            Text("More", fontSize = 18.sp)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            services.camera.stop()
            services.detection.clearFrame()
        }
    }
}

@Composable
private fun AssistCameraDrive(
    services: AppServices,
    cameraBlocked: MutableState<Boolean>,
    sessionSpeech: SessionSpeechTracker,
) {
    LaunchedEffect(services, sessionSpeech) {
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
                val candidate = if (cameraBlocked.value) {
                    VoiceCues.cameraUnavailable()
                } else {
                    VoiceCues.fromDecision(decision)
                }
                val cue = navigationCueForSpeech(
                    cue = candidate,
                    nowElapsedMs = now,
                    guidanceStartedAtMs = startedAt,
                    suppressNavigationUntilMs = sessionSpeech.suppressNavigationUntilMs,
                )
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
            services.guidance.markIdle("Guidance is paused. The virtual robot is holding.")
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
        ScreenHeader(title = "Robot Simulation", onBack = onBack)
        Text(
            "Simulated tricycle only. Nothing on this screen is a physical robot.",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 16.sp,
            modifier = Modifier.semantics {
                contentDescription = "Simulated tricycle only. Nothing on this screen is a physical robot."
            },
        )
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
    }
}

@Composable
private fun ScenariosScreen(onBack: () -> Unit) {
    var runSerial by remember { mutableIntStateOf(0) }
    val runs = remember(runSerial) { GuidanceScenarios.run() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "Test Scenarios", onBack = onBack)
        Text(
            "These cards run the navigation engine on an authored map. They are not the camera, not a floor mask, and not an indoor evaluation.",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        Button(
            onClick = { runSerial += 1 },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = "Run the simulator scenarios again" },
        ) {
            Text("Run scenarios again", fontSize = 18.sp)
        }
        runs.forEach { run ->
            val wheel = formatWheelDegrees(run.decision.steeringRadians)
            ScenarioCard(
                title = run.scenario.title,
                body = "${run.scenario.setup} Decision: ${run.decision.action.userLabel}. " +
                    "Reason: ${run.decision.reason} Front wheel: $wheel degrees. Source: ${run.decision.perceptionLabel}.",
            )
        }
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
        ScreenHeader(title = "Diagnostics", onBack = onBack)
        val report = diagnosticsText(
            granted = granted,
            detection = DetectorStatusText.line(readiness, detail),
            decision = smoothed.action.userLabel,
            reason = smoothed.reason,
            confidence = NavigationDecisions.formatConfidence(smoothed.confidence),
            wheelDegrees = formatWheelDegrees(robot.steeringRadians),
            source = smoothed.perceptionLabel,
            voice = "${voiceSettings.summary()}. $voiceStatus",
            lastCue = lastVoice.ifBlank { "none" },
            performance = performanceLine(fps, frames, inferenceFps, lastLatency, averageLatency, inferences),
            hardware = services.hardware.connectionLabel,
        )
        Button(
            onClick = {
                Log.i("TriGuideDiag", report)
                sharePlainText(context, report)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = "Share a plain text diagnostics report. It is not a safety proof." },
        ) {
            Text("Share diagnostics", fontSize = 18.sp)
        }
        SectionLabel("Camera")
        StatusLine("Version", BuildConfig.VERSION_NAME)
        StatusLine("Camera permission", if (granted) "granted" else "not granted")
        SectionLabel("Detection")
        StatusLine("Detection", DetectorStatusText.line(readiness, detail))
        StatusLine("Detection engine", readiness.name)
        StatusLine("Detection detail", detail)
        StatusLine("Real detector loaded", (readiness == DetectorReadiness.READY).toString())
        StatusLine("Observation", observation.summary)
        StatusLine("Observation from a real result", observation.realDetector.toString())
        SectionLabel("Decision")
        StatusLine("Smoothed decision", "${smoothed.action.userLabel}. ${smoothed.reason}")
        StatusLine("Smoothed confidence", NavigationDecisions.formatConfidence(smoothed.confidence))
        StatusLine("Raw decision", "${raw.action.userLabel}. ${raw.reason}")
        StatusLine("Metric depth used", smoothed.metricDepthUsed.toString())
        StatusLine("Vision sample", smoothed.basedOnRealVision.toString())
        StatusLine("Floor segmentation", "${FloorSegmentationStatus.STATE}. ${FloorSegmentationStatus.DETAIL}")
        SectionLabel("Voice")
        StatusLine("Voice guidance", voiceSettings.summary())
        StatusLine("Voice engine", voiceStatus)
        StatusLine("Last voice cue", lastVoice)
        SectionLabel("Performance")
        StatusLine(
            "Performance",
            performanceLine(fps, frames, inferenceFps, lastLatency, averageLatency, inferences),
        )
        StatusLine("Preview frames seen", frames.toString())
        StatusLine("Inferences completed", inferences.toString())
        StatusLine("Inference error", inferenceError ?: "none")
        SectionLabel("Hardware")
        StatusLine("Hardware", services.hardware.connectionLabel)
        StatusLine("Hardware accepts commands", services.hardware.dispatch(SteeringCommand(0.0, 0.0)).accepted.toString())
        SectionLabel("Robot")
        StatusLine("Sim speed m/s", format(robot.linearSpeedMps))
        StatusLine("Front wheel", "${formatWheelDegrees(robot.steeringRadians)} degrees")
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
        ScreenHeader(title = "Settings", onBack = onBack)
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
        StatusLine("Fingerprint", GuidanceSpeech.GESTURE_DESCRIPTION)
        StatusLine("TalkBack", "Buttons, the camera preview, and detection boxes have spoken labels. Voice cues are separate from TalkBack. The app opens on Live Vision and starts guidance by itself. Triple-tap on Live Vision restarts guidance. A single tap still activates the control under your finger.")
        StatusLine("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text(
            GuidanceSpeech.disclaimer.english,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
        )
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
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    BackButton(onBack)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { contentDescription = text },
    )
}

@Composable
private fun NavButton(label: String, description: String, primary: Boolean, onClick: () -> Unit) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .semantics { contentDescription = description }
    if (primary) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label, fontSize = 18.sp)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label, fontSize = 18.sp)
        }
    }
}

private fun hasCameraPermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

private fun diagnosticsText(
    granted: Boolean,
    detection: String,
    decision: String,
    reason: String,
    confidence: String,
    wheelDegrees: String,
    source: String,
    voice: String,
    lastCue: String,
    performance: String,
    hardware: String,
): String {
    return DiagnosticsReport.text(
        DiagnosticsSnapshot(
            versionName = BuildConfig.VERSION_NAME,
            cameraPermission = if (granted) "granted" else "not granted",
            detection = detection,
            decision = decision,
            reason = reason,
            confidence = confidence,
            wheelDegrees = wheelDegrees,
            source = source,
            floor = "${FloorSegmentationStatus.STATE}. ${FloorSegmentationStatus.DETAIL}",
            voice = voice,
            lastCue = lastCue,
            performance = performance,
            hardware = hardware,
            shortcut = GuidanceSpeech.GESTURE_ON_SCREEN,
        ),
    )
}

private fun sharePlainText(context: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "TriGuide diagnostics")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Share diagnostics").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "Back to live guidance. Starts the camera and voice cues." },
    ) {
        Text("Back to live guidance", fontSize = 18.sp)
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
