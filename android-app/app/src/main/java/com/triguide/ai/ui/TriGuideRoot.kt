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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triguide.ai.BuildConfig
import com.triguide.ai.R
import com.triguide.ai.camera.CameraXSource
import com.triguide.ai.domain.DiagnosticsReport
import com.triguide.ai.domain.DiagnosticsSnapshot
import com.triguide.ai.domain.CameraLifecyclePhase
import com.triguide.ai.domain.CameraPermissionAction
import com.triguide.ai.domain.CameraPermissionPrompt
import com.triguide.ai.domain.CameraSpeechGate
import com.triguide.ai.domain.CameraStartResult
import com.triguide.ai.domain.GuidanceSpeech
import com.triguide.ai.domain.SessionSpeechTracker
import com.triguide.ai.domain.DetectionReports
import com.triguide.ai.domain.DetectorStatusText
import com.triguide.ai.domain.DetectorConfig
import com.triguide.ai.domain.DetectorReadiness
import com.triguide.ai.domain.AiVisionDiagnostic
import com.triguide.ai.domain.AiVisionModeCopy
import com.triguide.ai.domain.CameraActiveCue
import com.triguide.ai.domain.FloorSegmenterModelContract
import com.triguide.ai.domain.FloorSegmentationStatus
import com.triguide.ai.domain.GuidanceScenarios
import com.triguide.ai.domain.GuidanceSession
import com.triguide.ai.domain.IllustrativePath
import com.triguide.ai.domain.LiveVisionGesture
import com.triguide.ai.domain.LiveVisionGestureDetector
import com.triguide.ai.domain.LiveVisionStatus
import com.triguide.ai.domain.MockHardwareController
import com.triguide.ai.domain.navigationCueForSpeech
import com.triguide.ai.domain.NavigationConfig
import com.triguide.ai.domain.NavigationDecisions
import com.triguide.ai.domain.PerceptionMode
import com.triguide.ai.domain.PathRiskAnalyzer
import com.triguide.ai.domain.PerformanceMonitor
import com.triguide.ai.domain.ResolvedVoiceLanguage
import com.triguide.ai.domain.SceneDescription
import com.triguide.ai.domain.SceneLine
import com.triguide.ai.domain.SceneObservation
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
import com.triguide.ai.memory.LocalGuidanceMemory
import com.triguide.ai.memory.PersonConsentStore
import com.triguide.ai.robot.SimulatedTricycleController
import com.triguide.ai.sim.TricycleKinematics
import com.triguide.ai.vision.FloorSegmenterCameraAdapter
import com.triguide.ai.vision.MediaPipeObjectDetectorEngine
import com.triguide.ai.voice.AndroidVoiceSpeaker
import com.triguide.ai.voice.SoftOpenLaneBeeper
import com.triguide.ai.voice.VoiceSettingsStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAP_LOG = "TriGuideTap"

internal enum class Screen {
    LiveVision,
    More,
    Simulator,
    Scenarios,
    Diagnostics,
    Settings,
}

internal data class CameraPrompt(val serial: Int = 0, val fromGesture: Boolean = false)

/**
 * Held as one object so key-event collection does not close over a stale screen value.
 * Cold start stays on Live Vision with epoch 0. Later starts increment the epoch.
 */
internal class LiveNavState {
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

    /** Returning from tools must not silently restart a stopped camera/session. */
    fun returnToLive() {
        screen = Screen.LiveVision
    }

    fun showMore() {
        screen = Screen.More
        running = false
    }

    companion object {
        /** Restoring UI state is never an authorization to restart camera/guidance. */
        fun restored(screenName: String?, epoch: Int): LiveNavState = LiveNavState().apply {
            screen = Screen.entries.firstOrNull { it.name == screenName } ?: Screen.LiveVision
            running = false
            this.epoch = epoch.coerceAtLeast(0)
        }
        val Saver = listSaver<LiveNavState, Any>(
            save = { listOf(it.screen.name, it.epoch) },
            restore = { restored(it.getOrNull(0) as? String, it.getOrNull(1) as? Int ?: 0) },
        )
    }
}

private enum class RobotDriveMode(val label: String, val description: String) {
    Manual(
        label = "Manual drive",
        description = "Simulated. Buttons set speed and the front wheel. Not the camera, and not a physical robot.",
    ),
    CameraInformed(
        label = "Camera-informed",
        description = "Simulated robot. This mode opens the rear camera and steers from reported boxes. A box is not a measured clearance. This is not a physical robot.",
    ),
    SyntheticWorld(
        label = "Synthetic world",
        description = "Simulated map with authored obstacles. Those positions are not from the camera and not from a physical robot.",
    ),
}

private class AppServices(context: android.content.Context) {
    val performance = PerformanceMonitor()
    val detection = MediaPipeObjectDetectorEngine(context, performance)
    /** Diagnostic-only; Phase 1 has no floor model and never feeds navigation/TTS. */
    val floorSegmenter = FloorSegmenterCameraAdapter(context)
    val guidance = GuidanceSession()
    val robot = SimulatedTricycleController()
    val hardware = MockHardwareController()
    val camera = CameraXSource()
    val voiceSettings = VoiceSettingsStore(context)
    val voiceGate = VoiceGuidanceGate()
    val speaker = AndroidVoiceSpeaker(context)
    private val memoryDirectory = java.io.File(context.filesDir, "guidance-memory")
    val memory = LocalGuidanceMemory(memoryDirectory)
    val people = PersonConsentStore(memoryDirectory)

    init {
        memory.startSession(android.os.SystemClock.elapsedRealtime())
    }

    /** Single CameraX analysis fan-out: detector then diagnostic segmenter slot. */
    fun onAnalysisFrame(image: androidx.camera.core.ImageProxy, frontCamera: Boolean) {
        detection.analyze(image, frontCamera)
        floorSegmenter.offerAnalysisFrame(image, frontCamera)
    }
}

@Composable
fun TriGuideRoot(startPaused: Boolean = false) {
    val context = LocalContext.current
    val services = remember { AppServices(context.applicationContext) }
    val sessionSpeech = remember { SessionSpeechTracker() }
    val nav = rememberSaveable(saver = LiveNavState.Saver) {
        LiveNavState().apply { if (startPaused) pause() }
    }
    val sceneLab = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        services.camera.stop()
        services.robot.setCommand(SteeringCommand(0.0, 0.0))
        nav.showMore()
        Log.i(TAP_LOG, "scene lab returned; guidance remains paused")
    }
    val scope = rememberCoroutineScope()
    fun speakPaused() {
        // Start the interrupting paused cue in this callback when TTS is ready.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            speakWhenReady(
                speaker = services.speaker,
                settings = services.voiceSettings::current,
                line = GuidanceSpeech.guidancePaused,
                interrupt = true,
            )
        }
    }
    BackHandler(enabled = nav.screen != Screen.LiveVision) {
        nav.returnToLive()
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
                        services.robot.setCommand(SteeringCommand(0.0, 0.0))
                        services.camera.stop()
                        nav.showMore()
                        if (wasRunning) speakPaused()
                    },
                    onRestartGuidance = {
                        Log.i(TAP_LOG, "triple-tap accepted; restarting guidance")
                        confirmPulse(context)
                        nav.start(fromGesture = true)
                    },
                    onEmergencyStop = {
                        if (nav.running) {
                            // STOP wins immediately over analysis UX and soft tones.
                            nav.pause()
                            services.robot.setCommand(SteeringCommand(0.0, 0.0))
                            services.guidance.markIdle("Guidance paused. The virtual robot is stopped.")
                            services.camera.stop()
                            Log.i(TAP_LOG, "emergency stop activated")
                            successPulse(context)
                            speakPaused()
                        }
                    },
                )
                Screen.More -> MoreScreen(
                    services = services,
                    onLiveGuidance = { nav.start(fromGesture = false) },
                    onNavigate = { nav.screen = it },
                    onSceneLab = {
                        nav.showMore()
                        com.triguide.ai.diagnostics.SceneLabLauncher.intent(context)?.let(sceneLab::launch)
                    },
                )
                Screen.Simulator -> SimulatorScreen(services, onBack = nav::returnToLive)
                Screen.Scenarios -> ScenariosScreen(onBack = nav::returnToLive)
                Screen.Diagnostics -> DiagnosticsScreen(services, onBack = nav::returnToLive)
                Screen.Settings -> SettingsScreen(services, onBack = nav::returnToLive)
            }
        }
    }
}

@Composable
private fun MoreScreen(
    services: AppServices,
    onLiveGuidance: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onSceneLab: () -> Unit,
) {
    val context = LocalContext.current
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
        if (com.triguide.ai.diagnostics.SceneLabLauncher.available) {
            item {
                NavButton("فحص الجدار والأرضية · 60 ثانية", "افتح فحصاً محلياً تجريبياً مع مربعات الأشياء. ليس توجيهاً للمشي.", primary = false) {
                    // More already paused guidance and unbound the main camera. Returning stays paused.
                    onSceneLab()
                }
            }
        }
        item { StatusLine("Mode", "Research and Simulation") }
        item { StatusLine("Detection", detectionLine) }
        item { StatusLine("Decision", decision.action.userLabel) }
        item { StatusLine("Path risk", decision.pathRisk.name) }
        item { StatusLine("Reason", decision.reason) }
        item { StatusLine("Front wheel", "${formatWheelDegrees(robot.steeringRadians)} degrees") }
        item { StatusLine("Floor segmentation", "${FloorSegmentationStatus.STATE}. ${FloorSegmentationStatus.DETAIL}") }
        item { StatusLine("AI Vision overlay", AiVisionModeCopy.LEGEND) }
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

/** A stopped, failed, or old camera result must never become a new spoken scene. */
internal fun canDescribeFreshCameraScene(
    running: Boolean,
    permissionGranted: Boolean,
    cameraState: String,
    readiness: DetectorReadiness,
    inferenceError: String?,
    scene: SceneObservation,
): Boolean {
    val age = scene.frameAgeMs ?: return false
    return running &&
        permissionGranted &&
        cameraState == "Streaming" &&
        readiness == DetectorReadiness.READY &&
        inferenceError.isNullOrBlank() &&
        scene.mode == PerceptionMode.CAMERA_2D &&
        scene.realDetector &&
        !scene.contradictory &&
        age >= 0L && age < NavigationConfig.STALE_FRAME_MS
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
    onEmergencyStop: () -> Unit,
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
    val swipeDetector = remember { LiveVisionGestureDetector() }
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
    val observation = DetectionReports.observation(
        readiness = readiness,
        detail = detail,
        frame = frame,
        modelName = DetectorConfig.MODEL_DISPLAY_NAME,
        scoreThreshold = DetectorConfig.SCORE_THRESHOLD,
    )
    val detectorFailed = readiness == DetectorReadiness.MODEL_MISSING || readiness == DetectorReadiness.LOAD_FAILED
    val showBoxes = readiness == DetectorReadiness.READY && frame?.fromRealDetector == true
    // Default on so the owner can see the green open-lane strip without hunting the toggle.
    var aiVisionMode by remember { mutableStateOf(true) }
    val decision by services.guidance.decision.collectAsStateWithLifecycle()
    val overlayScene = SceneObservations.fromCamera(
        readiness = readiness,
        frame = frame,
        nowElapsedMs = android.os.SystemClock.elapsedRealtime(),
    )
    val livePathRisk = remember(overlayScene.cues) { PathRiskAnalyzer.analyze(overlayScene.cues) }
    val aiVisionModel = AiVisionDiagnostic.build(
        enabled = aiVisionMode,
        pathRisk = livePathRisk,
        cues = overlayScene.cues,
    )
    val softTone = remember { SoftOpenLaneBeeper() }
    DisposableEffect(Unit) {
        onDispose { softTone.release() }
    }
    var cameraActiveTonePlayed by remember { mutableStateOf(false) }
    val streaming = cameraState == "Streaming"
    // One soft non-speech tick when the analysis stream is genuinely ready.
    // Not a safety signal. Detector-empty corridor never drives a repeating beep.
    LaunchedEffect(streaming, running) {
        if (!streaming || !running) {
            cameraActiveTonePlayed = false
            softTone.release()
            return@LaunchedEffect
        }
        if (
            CameraActiveCue.shouldPlayStartupTone(
                cameraStreaming = streaming,
                guidanceRunning = running,
                alreadyPlayedForThisBind = cameraActiveTonePlayed,
                emergencyActive = !running,
            )
        ) {
            softTone.playCameraActiveOnce()
            cameraActiveTonePlayed = true
        }
    }
    val priority = LiveVisionStatus.priority(
        running = running,
        permissionGranted = granted,
        cameraState = cameraState,
        readiness = readiness,
        detail = detail,
        inferenceError = inferenceError,
        action = decision.action,
        pathRisk = decision.pathRisk,
    )
    val cameraPhase = when {
        !granted -> CameraLifecyclePhase.PERMISSION_REQUIRED
        !running -> CameraLifecyclePhase.IDLE
        cameraState == "Starting camera" -> CameraLifecyclePhase.STARTING
        cameraState.startsWith("Failed") || cameraState.startsWith("No camera") ->
            CameraLifecyclePhase.FAILED
        cameraState == "Streaming" -> CameraLifecyclePhase.STREAMING
        else -> CameraLifecyclePhase.STARTING
    }
    if (running && granted) {
        SideEffect {
            if (!sessionSpeech.startupLineDelivered && sessionSpeech.suppressNavigationUntilMs == 0L) {
                sessionSpeech.suppressNavigationUntilMs = Long.MAX_VALUE
            }
        }
        AssistCameraDrive(services, cameraPhase, sessionSpeech)
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
                    // A confirmed hazard may already have spoken; do not add a second open line.
                    if (sessionSpeech.startupLineDelivered) {
                        return@LaunchedEffect
                    }
                    val lines = sessionSpeech.guidanceStartLines()
                    val accepted = speakLinesInOrder(
                        speaker = services.speaker,
                        settings = services.voiceSettings::current,
                        lines = lines,
                    )
                    if (accepted) {
                        sessionSpeech.startupLineDelivered = true
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

    fun runDescribe() {
        val scene = SceneObservations.fromCamera(
            readiness = readiness,
            frame = frame,
            nowElapsedMs = android.os.SystemClock.elapsedRealtime(),
        )
        if (!canDescribeFreshCameraScene(
                running = running,
                permissionGranted = granted,
                cameraState = cameraState,
                readiness = readiness,
                inferenceError = inferenceError,
                scene = scene,
            )
        ) {
            val unavailable = if (!running) {
                SceneLine("Guidance paused. Restart to describe.", "التوجيه متوقف. أعد تشغيله للوصف.")
            } else {
                SceneLine("Live picture unavailable. Try again later.", "الصورة الحية غير متاحة الآن. حاول لاحقاً.")
            }
            describeFrame(services, unavailable.english, unavailable)
            return
        }
        val line = SceneDescription.fromCues(scene.cues, services.people.recognize()?.name)
        services.memory.noteSummary(line.english, android.os.SystemClock.elapsedRealtime())
        describeFrame(services, line.english, line)
    }

    fun speakStatusNow() {
        val settings = services.voiceSettings.current().sanitized()
        if (!settings.speechEnabled) return
        val language = services.speaker.resolve(settings.language)
        val text = LiveVisionStatus.spokenStatus(priority, language)
        services.speaker.speak(
            SpeechDirective.Speak(
                text = text,
                interrupt = true,
                speechRate = settings.speechRate,
                kind = VoiceAlertKind.SESSION_NOTICE,
                language = language,
            ),
        ) {}
    }

    fun handleGesture(gesture: LiveVisionGesture) {
        successPulse(context)
        when (gesture) {
            LiveVisionGesture.DESCRIBE -> runDescribe()
            LiveVisionGesture.OPEN_MORE -> onMore()
            LiveVisionGesture.SPEAK_STATUS -> speakStatusNow()
            LiveVisionGesture.RESTART_GUIDANCE -> onRestartGuidance()
        }
    }

    val lensShape = RoundedCornerShape(32.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                )
                Text(
                    text = "TriGuide",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = LiveVisionStatus.SAFETY_LINE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = priority.label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (priority.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = LiveVisionStatus.TIP_LINE_ONE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = LiveVisionStatus.TIP_LINE_TWO,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .observeScreenTaps {
                    val mark = tapDetector.onTap(android.os.SystemClock.elapsedRealtime())
                    if (mark.completed) {
                        successPulse(context)
                        onRestartGuidance()
                    } else {
                        confirmPulse(context)
                        Log.i(TAP_LOG, "tap count=${mark.countInGroup}")
                    }
                }
                .observeLiveVisionSwipes { direction ->
                    val mark = swipeDetector.onSwipe(direction, android.os.SystemClock.elapsedRealtime())
                    if (mark.completed && mark.gesture != null) {
                        Log.i(TAP_LOG, "gesture=${mark.gesture}")
                        handleGesture(mark.gesture)
                    } else if (mark.countInGroup > 0) {
                        confirmPulse(context)
                        Log.i(TAP_LOG, "swipe $direction count=${mark.countInGroup}")
                    }
                }
                .semantics {
                    contentDescription = LiveVisionStatus.PREVIEW_DESCRIPTION
                    customActions = listOf(
                        CustomAccessibilityAction("Describe this frame") {
                            runDescribe()
                            true
                        },
                        CustomAccessibilityAction("Open More") {
                            onMore()
                            true
                        },
                        CustomAccessibilityAction("Speak status") {
                            speakStatusNow()
                            true
                        },
                        CustomAccessibilityAction("Restart guidance") {
                            onRestartGuidance()
                            true
                        },
                        CustomAccessibilityAction("Emergency stop") {
                            onEmergencyStop()
                            true
                        },
                    )
                },
        ) {
            when {
                !running -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Guidance paused",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Triple-tap or swipe left three times to restart.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics {
                                contentDescription =
                                    "Guidance paused. Triple-tap or swipe left three times to restart."
                            },
                        )
                    }
                }
                !granted -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val openSettings = permissionAction == CameraPermissionAction.OpenAppSettings
                        Text(
                            if (openSettings) {
                                "Android will not ask again from this screen. Open Settings and allow Camera for TriGuide. Nothing is uploaded."
                            } else {
                                "Allow the camera to show a live preview. Nothing is uploaded."
                            },
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = {
                                if (openSettings) {
                                    openAppSettings(context)
                                } else {
                                    launcher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
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
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
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
                            onAnalysisFrame = services::onAnalysisFrame,
                            onResult = { result ->
                                cameraState = when (result) {
                                    CameraStartResult.Streaming -> "Streaming"
                                    is CameraStartResult.Unavailable -> result.reason
                                    is CameraStartResult.Failed -> "Failed: ${result.reason}"
                                }
                            },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(lensShape)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, lensShape)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), lensShape)
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        AndroidView(
                            factory = { previewView },
                            update = { view -> view.contentDescription = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (aiVisionMode) {
                            AiVisionRiskOverlay(
                                model = aiVisionModel,
                                frame = frame,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            DetectionOverlay(
                                frame = frame,
                                showBoxes = showBoxes,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        TextButton(
                            onClick = { aiVisionMode = !aiVisionMode },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .semantics {
                                    contentDescription = AiVisionModeCopy.TOGGLE_DESCRIPTION
                                },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Text(
                                text = if (aiVisionMode) {
                                    "${AiVisionModeCopy.TOGGLE_LABEL} on"
                                } else {
                                    "${AiVisionModeCopy.TOGGLE_LABEL} off"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }

            val cameraStartupFailed =
                granted && running &&
                    (cameraState.startsWith("Failed") || cameraState.startsWith("No camera"))
            if (cameraStartupFailed) {
                Text(
                    text = "Camera failed · ←←← or triple-tap to retry",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .semantics {
                            contentDescription =
                                "Camera failed. Swipe left three times or triple-tap to retry. Wait for streaming before assuming recovery."
                        },
                )
            }
        }

        if (running) {
            Spacer(Modifier.height(8.dp))
            EmergencyStopBar(onConfirmedStop = onEmergencyStop)
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
    cameraPhase: CameraLifecyclePhase,
    sessionSpeech: SessionSpeechTracker,
) {
    val phaseState = rememberUpdatedState(cameraPhase)
    LaunchedEffect(services, sessionSpeech) {
        var last = android.os.SystemClock.elapsedRealtime()
        val startedAt = last
        var logged = ""
        val cameraGate = CameraSpeechGate()
        try {
            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                val dt = ((now - last) / 1000.0).coerceIn(0.0, 0.1)
                last = now
                val phase = phaseState.value
                // Navigation decisions only while streaming; startup stays silent for camera cues.
                val canNavigate = phase == CameraLifecyclePhase.STREAMING
                val scene = SceneObservations.fromCamera(
                    readiness = services.detection.readiness.value,
                    frame = services.detection.latestFrame.value,
                    nowElapsedMs = now,
                )
                val decision = if (canNavigate) {
                    services.guidance.onCamera(scene)
                } else {
                    services.guidance.decision.value
                }
                if (canNavigate) {
                    services.robot.setCommand(decision.toSteeringCommand())
                    services.robot.tick(dt)
                } else {
                    services.robot.setCommand(SteeringCommand(0.0, 0.0))
                }
                val cameraCue = cameraGate.cueForTransition(phase)
                if (cameraCue != null) {
                    android.util.Log.i(
                        "TriGuideVoice",
                        "camera phase=${phase.name} speech=${cameraCue.kind} (once per episode/recovery)",
                    )
                    announce(services, cameraCue, now)
                }
                val candidate = if (canNavigate) {
                    VoiceCues.fromDecision(decision)
                } else {
                    null
                }
                val cue = navigationCueForSpeech(
                    cue = candidate,
                    nowElapsedMs = now,
                    guidanceStartedAtMs = startedAt,
                    suppressNavigationUntilMs = sessionSpeech.suppressNavigationUntilMs,
                )
                val spoke = announce(services, cue, now)
                if (spoke &&
                    cue?.kind == VoiceAlertKind.OBSTACLE_DETECTED &&
                    !sessionSpeech.startupLineDelivered
                ) {
                    sessionSpeech.markStartupSkippedForHazard(now)
                }
                if (scene.realDetector) {
                    services.memory.note(scene.cues, now)
                }
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
                        VoiceCues.fromDecision(decision).also {
                            if (scene.realDetector) {
                                services.memory.note(scene.cues, now)
                            }
                        }
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
        if (mode == RobotDriveMode.CameraInformed) {
            CameraInformedPreview(services)
        }
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
        val evidenceDump = services.guidance.evidenceRecorder.formatLatest(10)
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
            memory = "${services.memory.summaryLine()} ${services.people.statusLine()}",
        ) + "\n\n" + evidenceDump
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
        StatusLine("Floor segmenter seam", services.floorSegmenter.diagnosticSummaryLine())
        StatusLine(
            "Segmenter class map",
            "${FloorSegmenterModelContract.classMappingStatus}. ${FloorSegmenterModelContract.MAPPING_NOTE}",
        )
        StatusLine("Corridor band", "${PathRiskAnalyzer.CORRIDOR_LEFT}–${PathRiskAnalyzer.CORRIDOR_RIGHT} (normalized x)")
        SectionLabel("AI Vision (research overlay)")
        Text(
            text = AiVisionModeCopy.LEGEND,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = AiVisionModeCopy.LEGEND },
        )
        StatusLine("AI Vision risk (live decision)", smoothed.pathRisk.name)
        SectionLabel("AI Evidence Recorder")
        Text(
            text = evidenceDump,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "Text-only decision evidence. No camera frames are stored here."
                },
        )
        SectionLabel("Voice")
        StatusLine("Voice guidance", voiceSettings.summary())
        StatusLine("Voice engine", voiceStatus)
        StatusLine("Last voice cue", lastVoice)
        StatusLine("Memory", "${services.memory.summaryLine()} ${services.people.statusLine()}")
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
        PersonConsentSection(services.people)
        StatusLine("Object detection", "${DetectorConfig.MODEL_DISPLAY_NAME} on device. Confidence floor ${DetectorConfig.SCORE_THRESHOLD}.")
        StatusLine("Navigation", "Camera mode uses box position in the image. It does not measure distance.")
        StatusLine("Floor segmentation", "${FloorSegmentationStatus.STATE}. ${FloorSegmentationStatus.DETAIL}")
        StatusLine("Cloud camera upload", "Off. This build does not request internet access.")
        StatusLine("Fingerprint", GuidanceSpeech.GESTURE_DESCRIPTION)
        StatusLine("TalkBack", "Buttons and the camera preview have spoken labels. Detection boxes are visual debug only and are not announced. Voice cues are separate from TalkBack. The app opens on Live Vision and starts guidance by itself. Triple-tap on Live Vision restarts guidance. Full gesture help is on this Settings screen. A single tap still activates the control under your finger.")
        StatusLine("Gestures", GuidanceSpeech.GESTURE_ON_SCREEN)
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
    memory: String,
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
            memory = memory,
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

@Composable
private fun CameraInformedPreview(services: AppServices) {
    val context = LocalContext.current
    val granted = hasCameraPermission(context)
    val readiness by services.detection.readiness.collectAsStateWithLifecycle()
    val detail by services.detection.statusDetail.collectAsStateWithLifecycle()
    val frame by services.detection.latestFrame.collectAsStateWithLifecycle()
    val observation = DetectionReports.observation(
        readiness = readiness,
        detail = detail,
        frame = frame,
        modelName = DetectorConfig.MODEL_DISPLAY_NAME,
        scoreThreshold = DetectorConfig.SCORE_THRESHOLD,
    )
    Text(
        text = observation.summary,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        modifier = Modifier.semantics { contentDescription = observation.summary },
    )
    if (!granted) {
        Text(
            text = "Camera permission is off, so this mode has no frame. The virtual robot holds. Open floor is not assumed.",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        return
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(previewView) {
        onDispose {
            services.camera.stop()
            services.detection.clearFrame()
        }
    }
    LaunchedEffect(previewView) {
        services.camera.start(
            context = context,
            lifecycleOwner = context as LifecycleOwner,
            previewView = previewView,
            onFrameTimestampNs = services.performance::onFrame,
            onAnalysisFrame = services::onAnalysisFrame,
            onResult = { },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        DetectionOverlay(
            frame = frame,
            showBoxes = readiness == DetectorReadiness.READY && frame?.fromRealDetector == true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun describeFrame(services: AppServices, logText: String, line: SceneLine) {
    Log.i("TriGuideScene", logText)
    val settings = services.voiceSettings.current().sanitized()
    if (!settings.speechEnabled) {
        return
    }
    val language = services.speaker.resolve(settings.language)
    services.speaker.speak(
        SpeechDirective.Speak(
            text = SceneDescription.text(line, language),
            interrupt = true,
            speechRate = settings.speechRate,
            kind = VoiceAlertKind.SESSION_NOTICE,
            language = language,
        ),
    ) {}
}

private fun announce(services: AppServices, cue: VoiceCue?, nowElapsedMs: Long): Boolean {
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
            return false
        }
        return true
    }
    return false
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
