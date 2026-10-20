package com.triguide.ai.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

/** Ephemeral primitives only. No Frame/Image/Session/Pose is sent to the worker. */
internal data class SemanticFusionPoint(val imageU: Double, val imageV: Double,
    val worldX: Double? = null, val worldY: Double? = null, val worldZ: Double? = null)
/** Opt-in diagnostic on the EXISTING ARCore owner. No camera owner, guidance or speech. */
internal class ArCoreSemanticDiagnostic(context: Context, private val onResult: (SemanticObservation?) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private val publication = SemanticFusionPublication()
    private val gate = DiagnosticFrameGate(1_000_000_000L, MAX_AGE_NS).apply { start() }
    private var engine: DiagnosticSegmenter? = null // worker-owned, including close
    private var lastCpuTimestampNs = 0L // GL owner; independent clock ordering only
    private val lock = Any()
    private val reasons = sortedMapOf<String, Int>()
    private val timings = ArrayList<Double>(60)
    private val captureMs = ArrayList<Double>(60)
    private val prepareMs = ArrayList<Double>(60)
    private val inferenceMs = ArrayList<Double>(60)
    private var completed = 0
    private var fusedCompleted = 0
    private var rgbOnlyCompleted = 0
    private var candidateFrames = 0
    private var geometryInvalidations = 0

    init {
        val appContext = context.applicationContext
        worker.execute {
            try {
                if (!stopped.get()) {
                    engine = DiagnosticSegmenter(appContext, "mosaic", "nnapi")
                    Log.i(TAG, "SEMANTIC_FUSION_CONTRACT ${engine!!.contractLine()} " +
                        "rgb=bt601_limited_assumption colour_accuracy=unverified canvas=centered_letterbox " +
                        "labels=argmax_not_confidence navigation=none")
                    ready.set(true)
                }
            } catch (error: Exception) {
                count("model_init_failed")
                Log.e(TAG, "SEMANTIC_FUSION_DISABLED model_init_failed", error)
            }
        }
    }

    fun trackingChanged(tracking: Boolean) {
        if (publication.trackingChanged(tracking)) onResult(null)
    }

    fun geometryChanged() {
        synchronized(lock) { geometryInvalidations++ }
        publication.invalidateGeometry()
        onResult(null)
    }

    fun canDisplay(result: SemanticObservation, nowNs: Long): Boolean =
        publication.canPublish(result.revision, result.receivedNs, nowNs)

    fun displayDecision(result: SemanticObservation, nowNs: Long): SemanticDisplayDecision =
        publication.decision(result.revision, result.receivedNs, nowNs)

    /** Only called inside the already-admitted live raw-depth/confidence scope on GL. */
    fun capture(frame: Frame, depth: Image, confidence: Image) {
        captureImage(frame) { CapturedSamples.Fused(samplePoints(frame,depth,confidence)) }
    }

    /** Called after the current raw opportunity. No depth points, no ground evaluation. */
    fun captureRgbOnly(frame: Frame) {
        captureImage(frame) { CapturedSamples.Rgb(sampleRgbPoints(frame)) }
    }

    /** Shared one-inflight/cadence gate prevents a second CPU acquisition for this same frame. */
    private fun captureImage(frame: Frame, sample: () -> CapturedSamples) {
        if (stopped.get() || !ready.get()) return
        val received = SystemClock.elapsedRealtimeNanos()
        val frameStamp = frame.timestamp
        val admission = gate.offer(frameStamp, received)
        val ticket = admission.ticket ?: return // no queues, no CPU image acquired when busy
        val revision = publication.revision()
        try {
            val captured = frame.acquireCameraImage().use { image ->
                require(image.format == ImageFormat.YUV_420_888 && image.planes.size == 3)
                require(image.width > 0 && image.height > 0 && image.width.toLong() * image.height <= 1_048_576L)
                require(image.cropRect.left == 0 && image.cropRect.top == 0 &&
                    image.cropRect.right == image.width && image.cropRect.bottom == image.height) { "non_full_cpu_crop" }
                require(image.timestamp > lastCpuTimestampNs) { "cpu_timestamp_not_new" }
                lastCpuTimestampNs = image.timestamp
                val axes = FloatArray(6) { Float.NaN }
                frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED, floatArrayOf(0f,0f,1f,0f,0f,1f),
                    Coordinates2d.VIEW_NORMALIZED, axes)
                val quarterTurns = SemanticImageOrientation.fromViewAxes(axes)
                    ?: error("unknown_cpu_to_view_orientation")
                val samples = sample()
                Captured(image.width, image.height, image.planes.mapIndexed { index, plane ->
                    copyPlane(plane, if (index == 0) image.width else (image.width + 1) / 2,
                        if (index == 0) image.height else (image.height + 1) / 2)
                }, samples, quarterTurns)
            } // native CPU image closed BEFORE worker is queued, including conversion/inference errors
            val copiedNs = SystemClock.elapsedRealtimeNanos()
            synchronized(lock) { if (captureMs.size < 60) captureMs += (copiedNs - received) / 1_000_000.0 }
            if (stopped.get()) { gate.complete(ticket, SystemClock.elapsedRealtimeNanos()); return }
            worker.execute {
                try {
                    if (stopped.get()) return@execute
                    val active = engine ?: error("engine_unavailable")
                    val rgb = Yuv420Rgb.convert(captured.width, captured.height,
                        captured.planes[0], captured.planes[1], captured.planes[2])
                    val bitmap = Bitmap.createBitmap(rgb, captured.width, captured.height, Bitmap.Config.ARGB_8888)
                    var upright: Bitmap? = null
                    try {
                        upright = if (captured.quarterTurns == 0) bitmap else Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height,
                            Matrix().apply { postRotate(captured.quarterTurns * 90f) }, false,
                        )
                        active.prepare(upright)
                    } finally {
                        if (upright !== bitmap) upright?.recycle()
                        bitmap.recycle()
                    }
                    val preparedNs = SystemClock.elapsedRealtimeNanos()
                    active.invoke()
                    val inferredNs = SystemClock.elapsedRealtimeNanos()
                    val map = active.decode()
                    var unmapped = 0
                    val cells = (captured.samples as? CapturedSamples.Fused)?.points?.mapIndexed { index, p ->
                        val oriented = SemanticImageOrientation.pixel(captured.width, captured.height,
                            p.imageU, p.imageV, captured.quarterTurns)
                        val modelIndex = oriented?.let { SemanticFusionMapping.modelIndex(it.width, it.height,
                            it.x.toDouble() / it.width, it.y.toDouble() / it.height) }
                        if (modelIndex == null) unmapped++
                        SemanticDepthCell(index % GRID, index / GRID, modelIndex?.let { map[it] } ?: 0,
                            p.worldX, p.worldY, p.worldZ)
                    }
                    val reading = cells?.let { SemanticGroundDiagnostic.evaluate(it) }
                    val rgbGrid = (captured.samples as? CapturedSamples.Rgb)?.let {
                        SemanticRgbGrid.decode(captured.width,captured.height,captured.quarterTurns,it.points,map)
                    }
                    val ended = SystemClock.elapsedRealtimeNanos()
                    val outcome = gate.complete(ticket, ended)
                    count(outcome)
                    synchronized(lock) {
                        if (timings.size < 60) {
                            timings += (ended - received) / 1_000_000.0
                            prepareMs += (preparedNs - copiedNs) / 1_000_000.0
                            inferenceMs += (inferredNs - preparedNs) / 1_000_000.0
                        }
                    }
                    if (outcome != "accepted" || !publication.canPublish(revision, received, ended)) return@execute
                    val result: SemanticObservation = if (cells != null && reading != null) {
                        SemanticFusionResult(revision,received,frameStamp,cells,reading,unmapped,ended)
                    } else {
                        val decoded = requireNotNull(rgbGrid)
                        Semantic2DObservation(revision,received,frameStamp,ended,decoded.cells,decoded.unmappedCells)
                    }
                    synchronized(lock) {
                        completed++
                        if (result is SemanticFusionResult) fusedCompleted++ else rgbOnlyCompleted++
                        if (reading?.referenceYMeters != null) candidateFrames++
                    }
                    if (cells != null && reading != null) Log.i(TAG, "SEMANTIC_FUSION_SAMPLE status=${reading.status} reason=${reading.reason} support=${reading.supportCount} " +
                        "cpu_upright_quarter_turns=${captured.quarterTurns} " +
                        "wall_cells=${cells.count { it.label == 1 }} floor_cells=${cells.count { it.label == 4 }} " +
                        "rug_cells=${cells.count { it.label == 29 }} raw_points=${cells.count { it.worldY != null }} " +
                        "unmapped_cells=$unmapped ignored_label_zero_includes_unmapped=true " +
                        "raised_samples=${reading.raisedSamples} flat_rug_samples=${reading.flatRugSamples} " +
                        "below_samples=${reading.belowSamples} unknown_samples=${reading.unknownSamples} " +
                        "receipt_to_result_ms=${(ended - received) / 1_000_000.0} " +
                        "sensor_age=unknown ground_identity=unverified temporal_confirmation=none " +
                        "grid=8x8 navigation=none no_image_storage=true no_coordinates_logged=true")
                    else Log.i(TAG, "SEMANTIC_2D_SAMPLE source=rgb_only geometry=unknown " +
                        "wall_cells=${result.paintCells.count { it.label == 1 }} " +
                        "floor_cells=${result.paintCells.count { it.label == 4 }} " +
                        "rug_cells=${result.paintCells.count { it.label == 29 }} unmapped_cells=${result.unmappedCells} " +
                        "cpu_upright_quarter_turns=${captured.quarterTurns} receipt_to_result_ms=${(ended-received)/1_000_000.0} " +
                        "sensor_age=unknown labels=argmax_not_confidence navigation=none no_image_storage=true")
                    onResult(result) // Activity marshals to main and rechecks revision AND receipt age
                } catch (error: Exception) {
                    gate.complete(ticket, SystemClock.elapsedRealtimeNanos())
                    count("processing_failed")
                    Log.e(TAG, "SEMANTIC_FUSION_FAILED processing", error)
                    onResult(null)
                }
            }
        } catch (_: NotYetAvailableException) {
            gate.complete(ticket, SystemClock.elapsedRealtimeNanos()); count("cpu_not_available")
        } catch (error: Exception) {
            gate.complete(ticket, SystemClock.elapsedRealtimeNanos()); count("capture_failed")
            Log.e(TAG, "SEMANTIC_FUSION_FAILED capture", error)
        }
    }

    private fun sampleRgbPoints(frame: Frame): List<Pair<Double,Double>> {
        val view = FloatArray(GRID*GRID*2) { i ->
            if (i%2 == 0) ((i/2%GRID)+.5f)/GRID else ((i/2/GRID)+.5f)/GRID
        }
        val image = FloatArray(view.size) { Float.NaN }
        frame.transformCoordinates2d(Coordinates2d.VIEW_NORMALIZED,view,Coordinates2d.IMAGE_NORMALIZED,image)
        return (0 until GRID*GRID).map { image[2*it].toDouble() to image[2*it+1].toDouble() }
    }

    /** Transform only the current Frame; NaN sentinel prevents an unchanged SDK output becoming valid. */
    private fun samplePoints(frame: Frame, depth: Image, confidence: Image): List<SemanticFusionPoint> {
        val viewUv = FloatArray(GRID * GRID * 2) { i ->
            if (i % 2 == 0) ((i / 2 % GRID) + 0.5f) / GRID else ((i / 2 / GRID) + 0.5f) / GRID
        }
        val textureUv = FloatArray(viewUv.size) { Float.NaN }
        frame.transformCoordinates2d(Coordinates2d.VIEW_NORMALIZED, viewUv, Coordinates2d.TEXTURE_NORMALIZED, textureUv)
        val pixels = (0 until GRID * GRID).map { i -> SemanticFusionMapping.depthPixel(depth.width, depth.height,
            textureUv[2 * i].toDouble(), textureUv[2 * i + 1].toDouble()) }
        // Selected depth integer pixels, not the unsnapped view ray, determine semantic coordinates.
        val snappedUv = FloatArray(textureUv.size) { i ->
            pixels[i / 2]?.let { if (i % 2 == 0) it.first.toFloat() / depth.width else it.second.toFloat() / depth.height }
                ?: 0f // SDK never receives NaN input; invalid slots are invalidated explicitly below
        }
        val imageUv = FloatArray(snappedUv.size) { Float.NaN }
        frame.transformCoordinates2d(Coordinates2d.TEXTURE_NORMALIZED, snappedUv, Coordinates2d.IMAGE_NORMALIZED, imageUv)
        pixels.forEachIndexed { index, pixel ->
            if (pixel == null) { imageUv[2 * index] = Float.NaN; imageUv[2 * index + 1] = Float.NaN }
        }
        val dp = depth.planes[0]; val cp = confidence.planes[0]
        val raw = RawDepthFrameStats.sampleConfidentPixels(depth.width, depth.height,
            dp.buffer, dp.rowStride, dp.pixelStride, cp.buffer, cp.rowStride, cp.pixelStride, pixels.filterNotNull())
            .associateBy { it.x to it.y }
        val intrinsics = frame.camera.textureIntrinsics
        val dims = intrinsics.imageDimensions; val focal = intrinsics.focalLength; val principal = intrinsics.principalPoint
        val calibration = TextureDepthCalibration(dims[0], dims[1], depth.width, depth.height,
            focal[0].toDouble(), focal[1].toDouble(), principal[0].toDouble(), principal[1].toDouble())
        val pose = frame.camera.pose
        return pixels.mapIndexed { index, pixel ->
            val rawPoint = raw[pixel]
            val point = rawPoint?.let { TextureDepthProjection.project(calibration, it.x, it.y, it.depthMm) }
            val world = point?.let { pose.transformPoint(floatArrayOf(it.xMeters.toFloat(), it.yMeters.toFloat(), it.zMeters.toFloat())) }
                ?.takeIf { xyz -> xyz.all { it.isFinite() } }
            SemanticFusionPoint(imageUv[2 * index].toDouble(), imageUv[2 * index + 1].toDouble(),
                world?.get(0)?.toDouble(), world?.get(1)?.toDouble(), world?.get(2)?.toDouble())
        }
    }

    private fun copyPlane(plane: Image.Plane, width: Int, height: Int): YuvRgbPlane {
        val buffer = plane.buffer.duplicate()
        require(plane.pixelStride > 0)
        val rowBytes = (width - 1L) * plane.pixelStride + 1L
        require(plane.rowStride >= rowBytes)
        val required = (height - 1L) * plane.rowStride + rowBytes
        require(required in 1..minOf(buffer.remaining().toLong(), 4_194_304L)) { "cpu_plane_truncated_or_too_large" }
        val bytes = ByteArray(required.toInt()) // no unused trailing padding/extra remaining buffer
        buffer.get(bytes)
        return YuvRgbPlane(ByteBuffer.wrap(bytes), plane.rowStride, plane.pixelStride)
    }

    /** Stops immediately; close is sequenced behind the sole in-flight inference, never UI-blocking. */
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        ready.set(false); gate.stop(); publication.stop(); onResult(null)
        worker.execute {
            try { engine?.close() } finally { engine = null; Log.i(TAG, "SEMANTIC_FUSION_CLOSED navigation=none") }
        }
        worker.shutdown()
    }

    fun summary(sessionSeconds: Double): String = synchronized(lock) {
        fun percentiles(name: String, values: List<Double>): String {
            val sorted = values.sorted()
            fun p(q: Double) = if (sorted.isEmpty()) "unavailable" else sorted[(ceil(sorted.size * q).toInt() - 1).coerceAtLeast(0)].toString()
            return "${name}_p50_ms=${p(0.5)} ${name}_p90_ms=${p(0.9)}"
        }
        val fps = if (sessionSeconds.isFinite() && sessionSeconds > 0) (completed / sessionSeconds).toString() else "unavailable"
        "semantic_enabled=true semantic_completed=$completed semantic_candidate_frames=$candidateFrames " +
            "semantic_fused_completed=$fusedCompleted semantic_rgb_only_completed=$rgbOnlyCompleted " +
            "semantic_geometry_invalidations=$geometryInvalidations " +
            "semantic_outcomes=${reasons.entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { "none" }} " +
            "semantic_accepted_per_session_second=$fps not_camera_fps=true " +
            percentiles("semantic_receipt_to_result", timings) + " " + percentiles("semantic_gl_capture", captureMs) + " " +
            percentiles("semantic_worker_prepare_including_queue", prepareMs) + " " + percentiles("semantic_inference", inferenceMs) + " " +
            "semantic_navigation=none semantic_temporal_confirmation=none"
    }
    private fun count(reason: String) { synchronized(lock) { reasons[reason] = (reasons[reason] ?: 0) + 1 } }
    private sealed interface CapturedSamples {
        data class Fused(val points: List<SemanticFusionPoint>) : CapturedSamples
        data class Rgb(val points: List<Pair<Double,Double>>) : CapturedSamples
    }
    private data class Captured(val width: Int, val height: Int, val planes: List<YuvRgbPlane>,
        val samples: CapturedSamples, val quarterTurns: Int)
    companion object { const val MAX_AGE_NS = 250_000_000L; private const val GRID = 8; private const val TAG = "TriGuideSemantic" }
}
