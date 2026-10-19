package com.triguide.ai.diagnostics

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.triguide.ai.camera.CameraXSource
import com.triguide.ai.domain.CameraStartResult
import com.triguide.ai.domain.FrameCrops
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableStateFlow

/** Bounded debug experiment; optional observation boxes/narration, never navigation or network. */
class LiveSegmenterDiagnosticActivity : ComponentActivity() {
    private val camera = CameraXSource()
    private val gate = DiagnosticFrameGate(MINIMUM_INTERVAL_NS, MAXIMUM_RESULT_AGE_NS)
    private val stopping = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private val engineLock = Any()
    private val statsLock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var engine: DiagnosticSegmenter? = null
    private lateinit var status: TextView
    private val statusText = MutableStateFlow("جارٍ تشغيل الكاميرا؛ النتائج ليست إرشادات للمشي.")
    private var objectCompanion: DiagnosticObjectCompanion? = null
    private var showObjects = false
    private var startedNs = 0L
    private var offered = 0
    private var completed = 0
    private val reasons = sortedMapOf<String, Int>()
    private val prepareMs = ArrayList<Double>()
    private val inferenceMs = ArrayList<Double>()
    private val decodeMs = ArrayList<Double>()
    private val callbackMs = ArrayList<Double>()
    private var lastUiNs = 0L
    private var lastSurfaceNs = 0L
    private var failed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) { stopProbe("restored_activity"); return }
        val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 20)
        if (durationSeconds !in 5..60 || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            failed = true
            stopProbe("invalid_duration_or_missing_camera_permission")
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showObjects = intent.getBooleanExtra(EXTRA_OBJECTS, false)
        val preview = PreviewView(this)
        if (showObjects) {
            preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            val companion = DiagnosticObjectCompanion(this).also { objectCompanion = it }
            setContent { CombinedDiagnosticScreen(preview, companion, statusText) { stopProbe("user_stop") } }
        } else {
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            status = TextView(this).apply {
                text = "Camera diagnostic only. Stay still. No walking instructions."
                textSize = 18f
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            val stop = Button(this).apply {
                text = "Stop diagnostic"
                minHeight = dp(56)
                setOnClickListener { stopProbe("user_stop") }
            }
            layout.addView(status)
            layout.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            layout.addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setContentView(layout)
        }
        handler.postDelayed({ failed = true; stopProbe("startup_timeout") }, STARTUP_TIMEOUT_MS)
        worker.execute {
            try {
                synchronized(engineLock) {
                    if (!stopping.get()) engine = DiagnosticSegmenter(this, "mosaic", "nnapi")
                }
                runOnUiThread {
                    if (!stopping.get()) {
                        gate.start()
                        camera.start(this, this, preview, { objectCompanion?.performance?.onFrame(it) },
                            { image, front -> analyze(image, front) }) { result ->
                            if (stopping.get()) return@start
                            if (result == CameraStartResult.Streaming) {
                                handler.removeCallbacksAndMessages(null)
                                startedNs = SystemClock.elapsedRealtimeNanos()
                                Log.i(TAG, "LIVE_START duration_seconds=$durationSeconds admission_fps_cap=10 " +
                                    "warmups=$WARMUPS sensor_clock=ordering_only capture_to_result_ms=unavailable " +
                                    "measurement=callback-to-decoded-map detector_concurrent=$showObjects no_image_storage=true")
                                handler.postDelayed({ stopProbe("duration") }, durationSeconds * 1000L)
                            } else {
                                failed = true
                                Log.e(TAG, "CAMERA_START_FAILED $result")
                                stopProbe("camera_start_failed")
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "LIVE_FAILED model_init ${error.message}", error)
                runOnUiThread { failed = true; stopProbe("model_init_failed") }
            }
        }
    }

    private fun analyze(image: ImageProxy, front: Boolean) {
        val received = SystemClock.elapsedRealtimeNanos()
        if (stopping.get()) return
        synchronized(statsLock) { offered++ }
        // Share the same camera and ImageProxy; the detector copies before returning.
        try {
            synchronized(engineLock) { if (!stopping.get()) objectCompanion?.analyze(image, front) }
        } catch (error: Throwable) {
            Log.e(TAG, "LIVE_FAILED detector_submission ${error.message}", error)
            runOnUiThread { failed = true; stopProbe("detector_submission_failed") }
            return
        }
        val admission = gate.offer(image.imageInfo.timestamp, received)
        val ticket = admission.ticket ?: run { count(admission.reason); return }
        try {
            synchronized(engineLock) {
                if (stopping.get()) return@synchronized
                val active = engine ?: error("Interpreter unavailable")
                val bitmap = uprightCrop(image)
                val uprightWidth = bitmap.width
                val uprightHeight = bitmap.height
                try { active.prepare(bitmap) } finally { bitmap.recycle() }
                val prepared = SystemClock.elapsedRealtimeNanos()
                active.invoke()
                val inferred = SystemClock.elapsedRealtimeNanos()
                val map = active.decode()
                var wall = 0
                var floor = 0
                for (id in map) { if (id == 1) wall++; if (id == 4) floor++ }
                val decoded = SystemClock.elapsedRealtimeNanos()
                val outcome = gate.complete(ticket, decoded)
                count(outcome)
                if (outcome != "accepted" || stopping.get()) return@synchronized
                if (showObjects && decoded - lastSurfaceNs >= SURFACE_UPDATE_NS) {
                    lastSurfaceNs = decoded
                    val surface = DiagnosticSurfaces.sample(map, uprightWidth, uprightHeight, front, decoded / 1_000_000)
                    // Publish on main and recheck stop: a late result must not revive a stopped overlay.
                    runOnUiThread { if (!stopping.get()) objectCompanion?.surfaces?.value = surface }
                    Log.i(TAG, "SURFACE_SAMPLE wall=${format(surface.fraction(DiagnosticSurfaces.WALL) * 100)} " +
                        "floor=${format(surface.fraction(DiagnosticSurfaces.FLOOR) * 100)} " +
                        "rug=${format(surface.fraction(DiagnosticSurfaces.RUG) * 100)} source_space=true navigation=none")
                }
                synchronized(statsLock) {
                    completed++
                    if (completed > WARMUPS && callbackMs.size < MAX_METRIC_SAMPLES) {
                        prepareMs += (prepared - received) / NS_PER_MS
                        inferenceMs += (inferred - prepared) / NS_PER_MS
                        decodeMs += (decoded - inferred) / NS_PER_MS
                        callbackMs += (decoded - received) / NS_PER_MS
                    }
                }
                if (completed == 1) {
                    Log.i(TAG, "LIVE_CONTRACT ${active.contractLine()} buffer=${image.width}x${image.height} " +
                        "crop=${image.cropRect} rotation=${image.imageInfo.rotationDegrees} front=$front")
                }
                if (decoded - lastUiNs >= UI_UPDATE_NS) {
                    lastUiNs = decoded
                    val note = "Frames $completed. Wall ${format(wall * 100.0 / map.size)}%, " +
                        "floor ${format(floor * 100.0 / map.size)}% of model canvas. Not confidence or a walkable path."
                    Log.i(TAG, "LIVE_SAMPLE callback_to_map_ms=${format((decoded - received) / NS_PER_MS)} " +
                        "wall_canvas_percent=${format(wall * 100.0 / map.size)} " +
                        "floor_canvas_percent=${format(floor * 100.0 / map.size)} guidance=none")
                    runOnUiThread {
                        if (!stopping.get()) {
                            statusText.value = note
                            if (::status.isInitialized) status.text = note
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            gate.complete(ticket, SystemClock.elapsedRealtimeNanos())
            Log.e(TAG, "LIVE_FAILED frame ${error.message}", error)
            runOnUiThread { failed = true; stopProbe("frame_failed") }
        }
        // CameraXSource is the sole ImageProxy.close owner, including exceptions.
    }

    private fun uprightCrop(image: ImageProxy): Bitmap {
        var owned = image.toBitmap() // CameraX handles format/plane stride; no custom byte parser.
        try {
            val rect = image.cropRect
            val crop = FrameCrops.clamp(owned.width, owned.height, rect.left, rect.top, rect.right, rect.bottom)
                ?: error("Empty camera crop")
            if (!crop.covers(owned.width, owned.height)) {
                val cropped = Bitmap.createBitmap(owned, crop.left, crop.top, crop.width, crop.height)
                if (cropped !== owned) owned.recycle()
                owned = cropped
            }
            val rotation = image.imageInfo.rotationDegrees
            require(rotation in listOf(0, 90, 180, 270))
            if (rotation != 0) {
                val rotated = Bitmap.createBitmap(owned, 0, 0, owned.width, owned.height,
                    Matrix().apply { postRotate(rotation.toFloat()) }, true)
                if (rotated !== owned) owned.recycle()
                owned = rotated
            }
            return owned
        } catch (error: Throwable) { owned.recycle(); throw error }
    }

    private fun stopProbe(reason: String) {
        if (!stopping.compareAndSet(false, true)) return
        val ended = SystemClock.elapsedRealtimeNanos()
        gate.stop() // Invalidate results before unbinding or waiting for native inference.
        objectCompanion?.pause() // Stop speech/hide object boxes immediately, before native cleanup.
        handler.removeCallbacksAndMessages(null)
        camera.stop()
        worker.execute {
            try {
                synchronized(engineLock) {
                    try { engine?.close() } finally { engine = null; objectCompanion?.close() }
                }
                synchronized(statsLock) {
                    val seconds = if (startedNs == 0L) 0.0 else (ended - startedNs) / 1_000_000_000.0
                    val state = if (failed || callbackMs.isEmpty()) "FAILED" else if (reason == "duration") "COMPLETE" else "INTERRUPTED"
                    fun percentiles(name: String, values: List<Double>): String {
                        if (values.isEmpty()) return "${name}_p50_ms=unavailable ${name}_p90_ms=unavailable"
                        val sorted = values.sorted()
                        fun p(q: Double) = format(sorted[(ceil(q * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)])
                        return "${name}_p50_ms=${p(0.5)} ${name}_p90_ms=${p(0.9)}"
                    }
                    Log.i(TAG, "LIVE_RESULT status=$state reason=$reason delivered=$offered completed=$completed " +
                        "measured=${callbackMs.size} elapsed_s=${format(seconds)} " +
                        "delivered_callback_fps=${if (seconds > 0) format(offered / seconds) else "unavailable"} " +
                        "accepted_measured_fps=${if (seconds > 0) format(callbackMs.size / seconds) else "unavailable"} " +
                        "${percentiles("prepare", prepareMs)} ${percentiles("inference", inferenceMs)} " +
                        "${percentiles("decode", decodeMs)} ${percentiles("callback_to_map", callbackMs)} " +
                        "rejections=$reasons camerax_internal_drops=unobservable capture_to_result_ms=unavailable " +
                        "navigation=disconnected detector_concurrent=$showObjects " +
                        "object_results=${objectCompanion?.performance?.inferencesCompleted?.value ?: 0} " +
                        "description_requests=${objectCompanion?.descriptionRequests?.get() ?: 0} " +
                        "speech_accepted=${objectCompanion?.speechAccepted?.get() ?: 0} " +
                        "voice_events=${if (showObjects) "see_speech_counters_audio_unverified" else "0"} image_files=0")
                }
            } finally { worker.shutdown(); runOnUiThread { finish() } }
        }
    }

    override fun onPause() { super.onPause(); stopProbe("paused") }
    override fun onDestroy() { stopProbe("destroyed"); super.onDestroy() }
    private fun count(reason: String) = synchronized(statsLock) { reasons[reason] = (reasons[reason] ?: 0) + 1 }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun format(value: Double) = "%.2f".format(Locale.US, value)

    companion object {
        const val EXTRA_DURATION_SECONDS = "triguide.diagnostic.duration_seconds"
        const val EXTRA_OBJECTS = "triguide.diagnostic.objects"
        private const val TAG = "TriGuideLiveSegmenter"
        private const val MINIMUM_INTERVAL_NS = 100_000_000L
        private const val MAXIMUM_RESULT_AGE_NS = 250_000_000L
        private const val STARTUP_TIMEOUT_MS = 15_000L
        private const val UI_UPDATE_NS = 1_000_000_000L
        private const val SURFACE_UPDATE_NS = 250_000_000L
        private const val MAX_METRIC_SAMPLES = 600
        private const val WARMUPS = 2
        private const val NS_PER_MS = 1_000_000.0
    }
}
