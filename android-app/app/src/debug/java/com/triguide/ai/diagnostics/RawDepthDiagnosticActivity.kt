/*
 * Copyright 2021 Google LLC
 * Copyright 2026 TriGuide-AI contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Lifecycle / GLSurfaceView session pattern adapted from the official ARCore
 * raw_depth_java sample (v1.57.0). TriGuide changes: fail-closed (no install or
 * permission prompts), no RGB acquisition, new-timestamp depth (not the sample's
 * equality check), RawDepthFrameStats + RawDepthSampleGate, bounded metrics only.
 */

package com.triguide.ai.diagnostics

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil

/**
 * Debug DUMP-protected Raw Depth metrics probe. ARCore owns the camera for a
 * bounded foreground window only. Never navigation, never CLEAR, never files.
 *
 * Manifest / launch wiring / [RawDepthSampleGate] owned by Codex.
 * [RawDepthFrameStats] owned by Cursor D2. This activity only consumes both.
 */
class RawDepthDiagnosticActivity : ComponentActivity(), GLSurfaceView.Renderer {

    private val stopping = AtomicBoolean(false)
    private val resultLogged = AtomicBoolean(false)
    private val closedLogged = AtomicBoolean(false)
    private val frameInUseLock = Any()
    private val statsLock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private val sampleGate = RawDepthSampleGate()
    private val coverageStats = RawDepthCoverageStats()
    private val cameraBackground = DiagnosticCameraBackground()

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var statusView: TextView
    private lateinit var surfaceStatusView: TextView

    private var session: Session? = null
    @Volatile private var sessionResumed = false
    private var cameraTextureId = 0
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var viewportChanged = false
    private var durationSeconds = DEFAULT_DURATION_SECONDS
    private var sessionStartedElapsedNs = 0L
    private var lastSampleAdmissionElapsedNs = 0L
    private var lastUiElapsedNs = 0L
    private var lastSampleLogElapsedNs = 0L
    private var failed = false
    private var stopReason = "unspecified"
    private var finishPosted = false
    private var hardwarePaused = false
    private var cameraPreviewEnabled = true
    private var surfaceProbingEnabled = true
    private var lastSurfaceProbeElapsedNs = 0L
    private var backgroundDraws = 0
    private var surfaceProbeSamples = 0
    private var verticalSurfaceSamples = 0
    private var upwardSurfaceSamples = 0
    private var inventoryVerticalSamples = 0
    private var inventoryUpwardSamples = 0
    private var inventoryTruncatedSamples = 0
    private val sessionUpdateMs = ArrayList<Double>(MAX_LATENCY_SAMPLES)
    private val surfaceProbeMs = ArrayList<Double>(MAX_LATENCY_SAMPLES)

    // Counters — bounded scalars only; latency list capped at MAX_LATENCY_SAMPLES (last N).
    private var uniqueCameraTimestamps = 0
    private var lastCameraTimestampNs = Long.MIN_VALUE
    private var trackingFrames = 0
    private var nonTrackingFrames = 0
    private var acceptedSamples = 0
    private var acquiredPairAttempts = 0
    private var notYetAvailableCount = 0
    private var summarizeRejects = 0
    private var firstCameraTimestampNs: Long? = null
    private var firstDepthTimestampNs: Long? = null
    private var firstConfidenceTimestampNs: Long? = null
    private var firstDepthWidth = 0
    private var firstDepthHeight = 0
    private var firstDepthRowStride = 0
    private var firstDepthPixelStride = 0
    private var firstConfidenceRowStride = 0
    private var firstConfidencePixelStride = 0
    private var latestMedianMm: Int? = null
    private var latestP90Mm: Int? = null
    private var latestValidFraction: Double? = null
    private var latestConfidentFraction: Double? = null
    private val gateRejections = sortedMapOf<String, Int>()
    private val receiptToSummaryMs = ArrayList<Double>(MAX_LATENCY_SAMPLES)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            failed = true
            stopReason = "restored_activity"
            stopping.set(true)
            sampleGate.stop()
            Log.w(TAG, "RAW_DEPTH_START status=FAIL reason=restored_activity navigation=none")
            logResultOnce()
            finish()
            return
        }

        durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, DEFAULT_DURATION_SECONDS)
        cameraPreviewEnabled = intent.getBooleanExtra(EXTRA_CAMERA_PREVIEW, true)
        surfaceProbingEnabled = intent.getBooleanExtra(EXTRA_SURFACE_PROBE, true)
        if (durationSeconds !in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS) {
            failClosedStartup("invalid_duration")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            failClosedStartup("missing_camera_permission")
            return
        }

        val availability = try {
            ArCoreApk.getInstance().checkAvailability(this)
        } catch (error: Exception) {
            Log.w(TAG, "availability check failed", error)
            failClosedStartup("arcore_availability_exception")
            return
        }
        if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
            failClosedStartup("arcore_not_supported_installed availability=$availability")
            return
        }

        try {
            val created = Session(this)
            // Own the Session before configure so a configure exception still closes in onDestroy.
            session = created
            if (!created.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)) {
                failClosedStartup("raw_depth_unsupported")
                return
            }
            configureSession(created)
        } catch (error: Exception) {
            Log.w(TAG, "session create failed", error)
            failClosedStartup("session_create_${error.javaClass.simpleName}")
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()

        Log.i(
            TAG,
            "RAW_DEPTH_START duration_seconds=$durationSeconds " +
                "camera_preview=$cameraPreviewEnabled surface_probe=$surfaceProbingEnabled " +
                "depth_sample_admission_cap_hz=$SAMPLE_CAP_HZ " +
                "confidence_threshold_diagnostic_only=128 " +
                "no_image_storage=true navigation=none metric_accuracy=unverified",
        )
        updateStatus(
            "Raw Depth diagnostic only / تشخيص العمق فقط. " +
                "Stay mostly still; small hand motion may be needed for depth. " +
                "No walking instructions. Missing depth = unknown, not clear. " +
                "ابقَ ثابتاً تقريباً؛ حركة يد بسيطة قد تلزم. لا تعليمات مشي. العمق الناقص = مجهول.",
        )
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        statusView = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(8))
            maxLines = 6
        }
        surfaceStatusView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(16), 0, dp(16), dp(8))
            maxLines = 5
            text = if (surfaceProbingEnabled) {
                "Surface probes: UNKNOWN. Geometry only; not floor/wall confirmation. لا إذن للمشي."
            } else "Surface probes disabled / فحص الأسطح معطّل. لا إذن للمشي."
        }
        val disclosure = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(dp(16), 0, dp(16), dp(8))
            text =
                "This application runs on Google Play Services for AR (ARCore), which is provided " +
                    "by Google and governed by the Google Privacy Policy. " +
                    "Terms: https://developers.google.com/ar/develop/terms " +
                    "Privacy: https://policies.google.com/privacy"
        }
        surfaceView = GLSurfaceView(this).apply {
            // Probe pause is terminal. Destroy context as a cleanup backstop;
            // no reason to retain GPU objects for an activity that never resumes.
            preserveEGLContextOnPause = false
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(this@RawDepthDiagnosticActivity)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        val stop = Button(this).apply {
            text = "Stop / إيقاف"
            textSize = 18f
            minHeight = dp(56)
            setOnClickListener { requestStop("user_stop", failed = false) }
        }
        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            surfaceStatusView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(
            disclosure,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            FrameLayout(this).apply {
                addView(
                    surfaceView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                if (surfaceProbingEnabled) addView(
                    SurfaceProbeOverlayView(this@RawDepthDiagnosticActivity),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        root.addView(
            stop,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.BOTTOM },
        )
        setContentView(root)
    }

    private fun configureSession(target: Session) {
        val config = target.config
        config.depthMode = Config.DepthMode.RAW_DEPTH_ONLY
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        config.planeFindingMode = if (surfaceProbingEnabled) {
            Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        } else Config.PlaneFindingMode.DISABLED
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
        config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        config.cloudAnchorMode = Config.CloudAnchorMode.DISABLED
        config.geospatialMode = Config.GeospatialMode.DISABLED
        // Task: autofocus when the API accepts it. ARCore exposes AUTO/FIXED on Config; AUTO is set here.
        config.focusMode = Config.FocusMode.AUTO
        target.configure(config)
    }

    override fun onResume() {
        super.onResume()
        if (stopping.get() || session == null) return
        // Official sample order: resume Session before GLSurfaceView.
        // Never call requestStop while holding frameInUseLock (stop waits on GL needing that lock).
        try {
            synchronized(frameInUseLock) {
                val active = session ?: return
                if (stopping.get()) return
                active.resume()
                sessionResumed = true
                if (sessionStartedElapsedNs == 0L) {
                    sessionStartedElapsedNs = SystemClock.elapsedRealtimeNanos()
                    handler.postDelayed(
                        { requestStop("duration", failed = false) },
                        durationSeconds * 1000L,
                    )
                }
            }
        } catch (error: CameraNotAvailableException) {
            Log.e(TAG, "camera not available on resume", error)
            requestStop("camera_not_available", failed = true)
            return
        } catch (error: Exception) {
            Log.e(TAG, "session resume failed", error)
            requestStop("session_resume_${error.javaClass.simpleName}", failed = true)
            return
        }
        if (!stopping.get() && ::surfaceView.isInitialized) {
            surfaceView.onResume()
        }
    }

    override fun onPause() {
        // Terminal: background ends the probe. Same stop path as Stop/timeout (GL then session, then result).
        requestStop("paused", failed = false)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        synchronized(frameInUseLock) {
            try {
                session?.close()
            } catch (error: Exception) {
                Log.w(TAG, "session.close", error)
            } finally {
                session = null
                logClosedOnce()
            }
        }
        super.onDestroy()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
        GLES20.glClearColor(0.05f, 0.05f, 0.05f, 1f)
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        check(cameraTextureId > 0) { "invalid_camera_texture" }
        if (cameraPreviewEnabled) cameraBackground.onSurfaceCreated()
        Log.i(TAG, "CAMERA_BACKGROUND_READY enabled=$cameraPreviewEnabled no_image_storage=true navigation=none")
        } catch (error: Exception) {
            Log.e(TAG, "camera background initialization failed", error)
            runOnUiThread { requestStop("background_init_${error.javaClass.simpleName}", failed = true) }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (stopping.get() || !sessionResumed || cameraTextureId == 0) return

        synchronized(frameInUseLock) {
            val active = session ?: return
            if (stopping.get() || !sessionResumed) return
            try {
                if (viewportChanged && viewportWidth > 0 && viewportHeight > 0) {
                    active.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
                    viewportChanged = false
                }
                active.setCameraTextureNames(intArrayOf(cameraTextureId))
                val updateStarted = SystemClock.elapsedRealtimeNanos()
                val frame = active.update()
                synchronized(statsLock) {
                    if (sessionUpdateMs.size >= MAX_LATENCY_SAMPLES) sessionUpdateMs.removeAt(0)
                    sessionUpdateMs += (SystemClock.elapsedRealtimeNanos() - updateStarted) / NS_PER_MS
                }
                if (!stopping.get() && cameraPreviewEnabled && cameraBackground.draw(
                    frameTimestampNs = frame.timestamp,
                    textureId = cameraTextureId,
                ) { positions, uv ->
                    frame.transformCoordinates2d(
                        Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, positions,
                        Coordinates2d.TEXTURE_NORMALIZED, uv,
                    )
                }) {
                    synchronized(statsLock) { backgroundDraws++ }
                }
                processFrame(frame)
            } catch (error: CameraNotAvailableException) {
                Log.e(TAG, "camera/config error on GL thread", error)
                runOnUiThread { requestStop("camera_unavailable_runtime", failed = true) }
            } catch (error: Exception) {
                Log.e(TAG, "unexpected GL-frame error", error)
                runOnUiThread {
                    requestStop("gl_frame_${error.javaClass.simpleName}", failed = true)
                }
            }
        }
    }

    /** SDK plane hits use VIEW pixels; never infer floor, wall identity or walkability. */
    private fun maybeSampleSurfaces(frame: Frame) {
        if (!surfaceProbingEnabled || stopping.get() || viewportWidth <= 0 || viewportHeight <= 0) return
        val started = SystemClock.elapsedRealtimeNanos()
        if (started - lastSurfaceProbeElapsedNs < UI_LOG_MIN_INTERVAL_NS) return
        lastSurfaceProbeElapsedNs = started
        val active = session ?: return
        val inventory = ArCoreSurfaceProbe.inventory(active, frame)
        val observations = ArCoreSurfaceProbe.sample(frame, viewportWidth, viewportHeight)
        val latencyMs = (SystemClock.elapsedRealtimeNanos() - started) / NS_PER_MS
        if (stopping.get()) return
        synchronized(statsLock) {
            surfaceProbeSamples++
            if (observations.any { it.reading.kind == SurfaceKind.VERTICAL_SURFACE }) verticalSurfaceSamples++
            if (observations.any { it.reading.kind == SurfaceKind.HORIZONTAL_UPWARD }) upwardSurfaceSamples++
            if (inventory.trackingVertical > 0) inventoryVerticalSamples++
            if (inventory.trackingUpward > 0) inventoryUpwardSamples++
            if (inventory.truncated) inventoryTruncatedSamples++
            if (surfaceProbeMs.size >= MAX_LATENCY_SAMPLES) surfaceProbeMs.removeAt(0)
            surfaceProbeMs += latencyMs
        }
        val logReadings = observations.joinToString(" ") {
            "${it.ray.name}=${it.reading.kind}:${it.reading.distanceMeters ?: "null"}:${it.reading.reason}"
        }
        Log.i(TAG, "SURFACE_INVENTORY frame_ts_ns=${frame.timestamp} status=${inventory.status} " +
            "inspected=${inventory.inspected} truncated=${inventory.truncated} " +
            "tracking_vertical=${inventory.trackingVertical} tracking_upward=${inventory.trackingUpward} " +
            "tracking_downward=${inventory.trackingDownward} paused=${inventory.paused} " +
            "stopped=${inventory.stopped} subsumed=${inventory.subsumed} unknown_kind=${inventory.unknownKind} " +
            "scope=session_known_not_visibility semantics=orientation_only navigation=none")
        Log.i(TAG, "SURFACE_SAMPLE frame_ts_ns=${frame.timestamp} viewport=${viewportWidth}x$viewportHeight " +
            "$logReadings processing_ms=${format(latencyMs)} coordinate_space=VIEW_PIXELS " +
            "distance_kind=sdk_hit_ray_m semantics=orientation_only no_floor_or_wall_confirmation=true " +
            "metric_accuracy=unverified navigation=none no_image_storage=true")
        val description = observations.joinToString(" | ") {
            "${it.ray.name}: ${it.reading.kind} ${it.reading.distanceMeters?.let { m -> "~${format(m.toDouble())}m" } ?: "—"}"
        } + "\nKnown SDK planes: V=${inventory.trackingVertical} U=${inventory.trackingUpward}" +
            " (${inventory.status}); not visibility or floor proof. لا إذن للمشي."
        runOnUiThread { if (!stopping.get()) surfaceStatusView.text = description }
    }

    /** API 30+ Context.display; API 26–29 WindowManager.defaultDisplay. */
    private fun displayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation
                ?: @Suppress("DEPRECATION") windowManager.defaultDisplay.rotation
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }

    private fun processFrame(frame: Frame) {
        if (stopping.get()) return
        val cameraTimestampNs = frame.timestamp
        if (cameraTimestampNs <= 0L) return

        val tracking = frame.camera.trackingState == TrackingState.TRACKING
        val isNewDistinctFrame: Boolean
        synchronized(statsLock) {
            if (cameraTimestampNs <= lastCameraTimestampNs) {
                // Repeated or out-of-order under LATEST_CAMERA_IMAGE — skip entirely.
                isNewDistinctFrame = false
            } else {
                isNewDistinctFrame = true
                uniqueCameraTimestamps++
                lastCameraTimestampNs = cameraTimestampNs
                if (firstCameraTimestampNs == null) firstCameraTimestampNs = cameraTimestampNs
                // Tracking counters only for distinct frames.
                if (tracking) trackingFrames++ else nonTrackingFrames++
            }
        }
        if (!isNewDistinctFrame) return
        maybeSampleSurfaces(frame)
        // Do not acquire depth while not TRACKING (startup NotTracking must not become fatal).
        if (!tracking) {
            maybeReportTracking(
                state = frame.camera.trackingState.name,
                reason = frame.camera.trackingFailureReason.name,
                nowElapsedNs = SystemClock.elapsedRealtimeNanos(),
            )
            return
        }

        val nowElapsed = SystemClock.elapsedRealtimeNanos()
        if (nowElapsed - lastSampleAdmissionElapsedNs < SAMPLE_MIN_INTERVAL_NS) {
            return
        }
        // Cap cadence before acquire so NotYetAvailable is rate-limited too.
        lastSampleAdmissionElapsedNs = nowElapsed
        val receivedElapsedNs = nowElapsed

        try {
            // Both SDK calls use this unchanged Frame; no equality of image clocks
            // is assumed. Scope owns cleanup even if acquisition/process/close fails.
            RawDepthPairScope.withFrame(
                frame = frame,
                timestampOf = { it.timestamp },
                acquireDepth = { it.acquireRawDepthImage16Bits() },
                acquireConfidence = { it.acquireRawDepthConfidenceImage() },
            ) { pair, depth, confidence ->
                summarizeAndGate(
                    pair = pair,
                    depth = depth,
                    confidence = confidence,
                    tracking = tracking,
                    receivedElapsedNs = receivedElapsedNs,
                )
            }
        } catch (_: NotYetAvailableException) {
            synchronized(statsLock) { notYetAvailableCount++ }
        } catch (error: Exception) {
            Log.e(TAG, "depth acquire/process failed", error)
            runOnUiThread {
                requestStop("depth_process_${error.javaClass.simpleName}", failed = true)
            }
        }
    }

    private fun summarizeAndGate(
        pair: RawDepthPairProof,
        depth: Image,
        confidence: Image,
        tracking: Boolean,
        receivedElapsedNs: Long,
    ) {
        if (stopping.get()) return
        val cameraTimestampNs = pair.frameTimestampNs
        RawDepthImageFormat.requireRaw16BitDepth(depth.format)
        if (confidence.format != ImageFormat.Y8) {
            throw IllegalStateException("unexpected_confidence_format_${confidence.format}")
        }
        if (depth.width != confidence.width || depth.height != confidence.height) {
            throw IllegalStateException(
                "depth_confidence_size_mismatch ${depth.width}x${depth.height} vs " +
                    "${confidence.width}x${confidence.height}",
            )
        }

        val depthPlane = depth.planes[0]
        val confidencePlane = confidence.planes[0]
        val depthTs = depth.timestamp
        val confidenceTs = confidence.timestamp
        synchronized(statsLock) {
            acquiredPairAttempts++
            if (firstDepthTimestampNs == null) {
                firstDepthTimestampNs = depthTs
                firstConfidenceTimestampNs = confidenceTs
                firstDepthWidth = depth.width
                firstDepthHeight = depth.height
                firstDepthRowStride = depthPlane.rowStride
                firstDepthPixelStride = depthPlane.pixelStride
                firstConfidenceRowStride = confidencePlane.rowStride
                firstConfidencePixelStride = confidencePlane.pixelStride
            }
        }

        val summary = try {
            RawDepthFrameStats.summarize(
                width = depth.width,
                height = depth.height,
                depth = depthPlane.buffer,
                depthRowStride = depthPlane.rowStride,
                depthPixelStride = depthPlane.pixelStride,
                confidence = confidencePlane.buffer,
                confidenceRowStride = confidencePlane.rowStride,
                confidencePixelStride = confidencePlane.pixelStride,
                confidenceThreshold = CONFIDENCE_THRESHOLD_DIAGNOSTIC,
            )
        } catch (error: IllegalArgumentException) {
            synchronized(statsLock) {
                summarizeRejects++
                countRejection("summarize_invalid")
            }
            Log.w(TAG, "summarize rejected: ${error.message}")
            return
        }

        // Observed coverage includes decoded zero/rejected/reprojected pairs,
        // not just admitted samples. It is not unique geometry or walkability.
        if (stopping.get()) return
        coverageStats.record(summary)

        // Empty depth/confidence must not become accepted or COMPLETE.
        if (summary.validDepthPixels <= 0 || summary.confidentPixels <= 0) {
            synchronized(statsLock) {
                countRejection("no_valid_confident_pixels")
            }
            maybeUpdateUi(SystemClock.elapsedRealtimeNanos())
            return
        }

        val completedElapsedNs = SystemClock.elapsedRealtimeNanos()
        if (stopping.get()) return
        val gateResult = sampleGate.accept(
            pair = pair,
            depthTimestampNs = depthTs,
            confidenceTimestampNs = confidenceTs,
            receivedElapsedNs = receivedElapsedNs,
            completedElapsedNs = completedElapsedNs,
            tracking = tracking,
        )
        if (gateResult != "accepted") {
            synchronized(statsLock) { countRejection(gateResult) }
            // Preserve the rejection; expose primitive metadata to diagnose alignment,
            // never pixels or an assumed timestamp tolerance.
            if (!stopping.get() && completedElapsedNs - lastSampleLogElapsedNs >= UI_LOG_MIN_INTERVAL_NS) {
                lastSampleLogElapsedNs = completedElapsedNs
                Log.i(
                    TAG,
                    "RAW_DEPTH_REJECT reason=$gateResult camera_ts_ns=$cameraTimestampNs " +
                        "depth_ts_ns=$depthTs confidence_ts_ns=$confidenceTs " +
                        "depth_minus_confidence_ns=${depthTs - confidenceTs} " +
                        "valid_pixels=${summary.validDepthPixels} confident_pixels=${summary.confidentPixels} " +
                        "sensor_age=unknown coverage=observed_only " +
                        "navigation=none metric_accuracy=unverified no_image_storage=true",
                )
            }
            maybeUpdateUi(completedElapsedNs)
            return
        }
        if (stopping.get()) return

        val latencyMs = (completedElapsedNs - receivedElapsedNs) / NS_PER_MS
        val validFraction =
            if (summary.sampledPixels > 0) summary.validDepthPixels.toDouble() / summary.sampledPixels
            else 0.0
        val confidentFraction =
            if (summary.sampledPixels > 0) summary.confidentPixels.toDouble() / summary.sampledPixels
            else 0.0

        synchronized(statsLock) {
            acceptedSamples++
            latestMedianMm = summary.medianDepthMm
            latestP90Mm = summary.p90DepthMm
            latestValidFraction = validFraction
            latestConfidentFraction = confidentFraction
            // Retain last MAX_LATENCY_SAMPLES (sliding), not first N.
            if (receiptToSummaryMs.size >= MAX_LATENCY_SAMPLES) {
                receiptToSummaryMs.removeAt(0)
            }
            receiptToSummaryMs += latencyMs
        }

        maybeLogSample(completedElapsedNs, summary.sampledPixels, validFraction, confidentFraction, latencyMs)
        maybeUpdateUi(completedElapsedNs)
    }

    private fun maybeLogSample(
        nowElapsedNs: Long,
        sampledPixels: Int,
        validFraction: Double,
        confidentFraction: Double,
        latencyMs: Double,
    ) {
        if (stopping.get()) return
        if (nowElapsedNs - lastSampleLogElapsedNs < UI_LOG_MIN_INTERVAL_NS) return
        lastSampleLogElapsedNs = nowElapsedNs
        val median: Int?
        val p90: Int?
        val accepted: Int
        synchronized(statsLock) {
            median = latestMedianMm
            p90 = latestP90Mm
            accepted = acceptedSamples
        }
        if (stopping.get()) return
        Log.i(
            TAG,
            "RAW_DEPTH_SAMPLE accepted=$accepted sampled_pixels=$sampledPixels " +
                "valid_fraction=${fraction(validFraction)} confident_fraction=${fraction(confidentFraction)} " +
                "median_depth_mm=${median ?: "null"} p90_depth_mm=${p90 ?: "null"} " +
                "distances_label=NOT_calibrated receipt_to_summary_ms=${format(latencyMs)} " +
                "admission_cap_hz=$SAMPLE_CAP_HZ not_full_camera_fps=true " +
                "acceptance_scope=same_frame_processing_only sensor_age=unknown coverage=observed_only " +
                "no_image_storage=true navigation=none metric_accuracy=unverified",
        )
    }

    /** Direct SDK diagnostics only; never infer a remedy or acquire depth while paused. */
    private fun maybeReportTracking(state: String, reason: String, nowElapsedNs: Long) {
        if (stopping.get()) return
        synchronized(statsLock) { countRejection("tracking_${state}_$reason") }
        if (nowElapsedNs - lastSampleLogElapsedNs < UI_LOG_MIN_INTERVAL_NS) return
        lastSampleLogElapsedNs = nowElapsedNs
        if (stopping.get()) return
        Log.i(TAG, "RAW_DEPTH_TRACKING state=$state failure_reason=$reason navigation=none")
        if (nowElapsedNs - lastUiElapsedNs < UI_LOG_MIN_INTERVAL_NS) return
        lastUiElapsedNs = nowElapsedNs
        runOnUiThread {
            if (!stopping.get()) {
                updateStatus(
                    "Tracking $state / SDK reason $reason. No depth admitted. " +
                        "التتبّع $state، السبب $reason. لا توجد قراءة عمق مقبولة. لا تمشِ.",
                )
            }
        }
    }

    private fun maybeUpdateUi(nowElapsedNs: Long) {
        if (stopping.get()) return
        if (nowElapsedNs - lastUiElapsedNs < UI_LOG_MIN_INTERVAL_NS) return
        lastUiElapsedNs = nowElapsedNs
        val median: Int?
        val p90: Int?
        val accepted: Int
        val valid: Double?
        val confident: Double?
        synchronized(statsLock) {
            median = latestMedianMm
            p90 = latestP90Mm
            accepted = acceptedSamples
            valid = latestValidFraction
            confident = latestConfidentFraction
        }
        val observed = coverageStats.snapshot()
        val line =
            "Diagnostic pairs $accepted (cap ${SAMPLE_CAP_HZ} Hz, not camera FPS). " +
                "Decoded ${observed.decodedPairs}; observed valid ${percent(observed.validFraction)} " +
                "confident ${percent(observed.confidentFraction)} (not unique estimates). " +
                "Last admitted valid ${percent(valid)} confident ${percent(confident)}. " +
                "Median ${median ?: "—"} mm / p90 ${p90 ?: "—"} mm NOT calibrated. " +
                "Sensor age unknown; coverage not sufficient for navigation. No walking cue. " +
                "عينات تشخيص $accepted، خرائط مفكوكة ${observed.decodedPairs}. " +
                "الثقة المرصودة ${percent(observed.confidentFraction)}. عمر العمق مجهول ولا إذن للمشي."
        runOnUiThread {
            if (!stopping.get()) updateStatus(line)
        }
    }

    private fun requestStop(reason: String, failed: Boolean) {
        if (!stopping.compareAndSet(false, true)) return
        this.failed = failed || this.failed
        stopReason = reason
        sampleGate.stop()
        coverageStats.stop()
        handler.removeCallbacksAndMessages(null)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Drain GL first (never hold frameInUseLock while waiting on GL), then pause session, then result.
        pauseHardwareOnce()
        logResultOnce()
        postFinish()
    }

    /**
     * Official order: GLSurfaceView.onPause waits for the GL thread to leave onDrawFrame
     * (releasing frameInUseLock) before session.pause. Idempotent — no duplicate pause work.
     */
    private fun pauseHardwareOnce() {
        if (hardwarePaused) return
        hardwarePaused = true
        if (::surfaceView.isInitialized) {
            try {
                surfaceView.queueEvent {
                    try {
                        // queueEvent is on the GL thread, but a lost context needs
                        // no delete. Context destruction on pause is the backstop.
                        if (EGL14.eglGetCurrentContext() != EGL14.EGL_NO_CONTEXT) {
                            try {
                                cameraBackground.releaseOnGlThread()
                            } finally {
                                if (cameraTextureId > 0) GLES20.glDeleteTextures(1, intArrayOf(cameraTextureId), 0)
                            }
                        }
                    } catch (error: Exception) {
                        Log.w(TAG, "GL resource cleanup", error)
                    } finally {
                        cameraTextureId = 0
                        Log.i(TAG, "CAMERA_BACKGROUND_RELEASED navigation=none no_image_storage=true")
                    }
                }
                surfaceView.onPause()
            } catch (error: Exception) {
                Log.w(TAG, "GLSurfaceView.onPause", error)
            }
        }
        synchronized(frameInUseLock) {
            sessionResumed = false
            try {
                session?.pause()
            } catch (error: Exception) {
                Log.w(TAG, "session.pause", error)
            }
        }
    }

    private fun logResultOnce() {
        if (!resultLogged.compareAndSet(false, true)) return
        val ended = SystemClock.elapsedRealtimeNanos()
        val started = sessionStartedElapsedNs
        val elapsedS =
            if (started == 0L) 0.0 else (ended - started) / 1_000_000_000.0
        val status: String
        val accepted: Int
        val unique: Int
        val tracking: Int
        val nonTracking: Int
        val attempts: Int
        val nya: Int
        val summarizeBad: Int
        val rejections: String
        val latencyLine: String
        val dims: String
        val firstTs: String
        val median: Int?
        val p90: Int?
        val valid: Double?
        val confident: Double?
        val surfaceLine: String
        synchronized(statsLock) {
            accepted = acceptedSamples
            unique = uniqueCameraTimestamps
            tracking = trackingFrames
            nonTracking = nonTrackingFrames
            attempts = acquiredPairAttempts
            nya = notYetAvailableCount
            summarizeBad = summarizeRejects
            rejections = gateRejections.entries.joinToString(",") { "${it.key}=${it.value}" }
                .ifEmpty { "none" }
            // Latency: p50/p90 over bounded last-600 receipt→summary samples (p50 ≈ median).
            latencyLine = percentiles("receipt_to_summary", receiptToSummaryMs)
            dims =
                if (firstDepthWidth > 0) {
                    "depth=${firstDepthWidth}x$firstDepthHeight " +
                        "depth_rowStride=$firstDepthRowStride depth_pixelStride=$firstDepthPixelStride " +
                        "conf_rowStride=$firstConfidenceRowStride conf_pixelStride=$firstConfidencePixelStride"
                } else {
                    "depth=unavailable"
                }
            firstTs =
                "first_camera_ts_ns=${firstCameraTimestampNs ?: "null"} " +
                    "first_depth_ts_ns=${firstDepthTimestampNs ?: "null"} " +
                    "first_confidence_ts_ns=${firstConfidenceTimestampNs ?: "null"}"
            median = latestMedianMm
            p90 = latestP90Mm
            valid = latestValidFraction
            confident = latestConfidentFraction
            surfaceLine = "camera_preview=$cameraPreviewEnabled background_draws=$backgroundDraws " +
                "surface_probe=$surfaceProbingEnabled surface_probe_samples=$surfaceProbeSamples " +
                "surface_samples_with_vertical=$verticalSurfaceSamples surface_samples_with_upward=$upwardSurfaceSamples " +
                "inventory_samples_with_vertical=$inventoryVerticalSamples inventory_samples_with_upward=$inventoryUpwardSamples " +
                "inventory_truncated_samples=$inventoryTruncatedSamples inventory_scope=session_known_not_visibility " +
                percentiles("session_update", sessionUpdateMs) + " " + percentiles("surface_probe", surfaceProbeMs)
            status = when {
                this.failed -> "FAIL"
                stopReason == "duration" && accepted == 0 -> "NO_DEPTH"
                stopReason == "duration" && accepted > 0 -> "COMPLETE"
                stopReason == "duration" -> "NO_DEPTH"
                else -> "INTERRUPTED"
            }
        }
        val observed = coverageStats.snapshot()
        Log.i(
            TAG,
            "RAW_DEPTH_RESULT status=$status reason=$stopReason " +
                "unique_camera_frames=$unique tracking=$tracking nontracking=$nonTracking " +
                "schema_version=2 accepted=$accepted acquired_pair_attempts=$attempts " +
                "notYetAvailable=$nya summarize_rejects=$summarizeBad gate_rejections=$rejections " +
                "$dims $firstTs " +
                "accepted_latest_valid_fraction=${fraction(valid)} accepted_latest_confident_fraction=${fraction(confident)} " +
                "observed_decoded_pairs=${observed.decodedPairs} observed_sampled_pixels=${observed.sampledPixels} " +
                "observed_valid_pixels=${observed.validDepthPixels} observed_confident_pixels=${observed.confidentPixels} " +
                "observed_pairs_with_confident_pixels=${observed.pairsWithConfidentPixels} " +
                "observed_valid_fraction=${fraction(observed.validFraction)} " +
                "observed_confident_fraction=${fraction(observed.confidentFraction)} " +
                "observed_capacity_reached=${observed.capacityReached} observed_counts_include_reprojection=true " +
                "$surfaceLine surface_semantics=orientation_only no_floor_or_wall_confirmation=true " +
                "median_depth_mm=${median ?: "null"} p90_depth_mm=${p90 ?: "null"} " +
                "distances_label=NOT_calibrated $latencyLine " +
                "elapsed_s=${format(elapsedS)} duration_seconds=$durationSeconds " +
                "depth_sample_admission_cap_hz=$SAMPLE_CAP_HZ " +
                "acceptance_scope=same_frame_processing_only sensor_age=unknown coverage=observed_only " +
                "no_image_storage=true navigation=none metric_accuracy=unverified",
        )
    }

    private fun logClosedOnce() {
        if (!closedLogged.compareAndSet(false, true)) return
        Log.i(TAG, "RAW_DEPTH_CLOSED session_closed=true no_image_storage=true navigation=none")
    }

    private fun postFinish() {
        if (finishPosted) return
        finishPosted = true
        runOnUiThread {
            if (!isFinishing) finish()
        }
    }

    private fun failClosedStartup(reason: String) {
        failed = true
        stopReason = reason
        Log.i(
            TAG,
            "RAW_DEPTH_START status=FAIL reason=$reason frames=0 " +
                "no_image_storage=true navigation=none metric_accuracy=unverified",
        )
        if (!resultLogged.get()) {
            stopping.set(true)
            sampleGate.stop()
            coverageStats.stop()
            pauseHardwareOnce()
            logResultOnce()
        }
        finish()
    }

    private fun countRejection(reason: String) {
        gateRejections[reason] = (gateRejections[reason] ?: 0) + 1
    }

    // Keep sparse nonzero observations visible; two decimal places would print0.00.
    private fun fraction(value: Double?): String = value?.toString() ?: "unavailable"

    private fun percent(value: Double?): String = when {
        value == null -> "unavailable"
        value > 0.0 && value < 0.0001 -> "<0.01%"
        else -> "${format(value * 100)}%"
    }

    private fun updateStatus(text: String) {
        if (::statusView.isInitialized) statusView.text = text
    }

    private fun percentiles(name: String, values: List<Double>): String {
        if (values.isEmpty()) {
            return "${name}_p50_ms=unavailable ${name}_p90_ms=unavailable"
        }
        val sorted = values.sorted()
        fun p(q: Double): String {
            val index = (ceil(q * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
            return format(sorted[index])
        }
        return "${name}_p50_ms=${p(0.5)} ${name}_p90_ms=${p(0.9)}"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun format(value: Double) = "%.2f".format(Locale.US, value)

    companion object {
        const val EXTRA_DURATION_SECONDS = "triguide.diagnostic.duration_seconds"
        const val EXTRA_CAMERA_PREVIEW = "triguide.diagnostic.camera_preview"
        const val EXTRA_SURFACE_PROBE = "triguide.diagnostic.surface_probe"

        private const val TAG = "TriGuideRawDepth"
        private const val DEFAULT_DURATION_SECONDS = 20
        private const val MIN_DURATION_SECONDS = 5
        private const val MAX_DURATION_SECONDS = 60
        private const val SAMPLE_CAP_HZ = 10
        private const val SAMPLE_MIN_INTERVAL_NS = 100_000_000L
        private const val UI_LOG_MIN_INTERVAL_NS = 1_000_000_000L
        private const val MAX_LATENCY_SAMPLES = 600
        private const val CONFIDENCE_THRESHOLD_DIAGNOSTIC = 128
        private const val NS_PER_MS = 1_000_000.0
    }
}
