package com.triguide.ai.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import com.triguide.ai.domain.DetectedObject
import com.triguide.ai.domain.DetectionFrame
import com.triguide.ai.domain.DetectionReports
import com.triguide.ai.domain.DetectorConfig
import com.triguide.ai.domain.DetectorReadiness
import com.triguide.ai.domain.EnvironmentObservation
import com.triguide.ai.domain.FrameTimestamps
import com.triguide.ai.domain.ObjectDetectionEngine
import com.triguide.ai.domain.PerformanceMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaPipe Tasks Object Detector in LIVE_STREAM mode.
 * CameraX feeds RGBA frames with STRATEGY_KEEP_ONLY_LATEST. This class copies each
 * accepted frame, rotates it upright, and calls detectAsync. Boxes are published
 * only from the result listener. A missing file or a load error publishes none.
 */
class MediaPipeObjectDetectorEngine(
    context: Context,
    private val performance: PerformanceMonitor,
) : ObjectDetectionEngine {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val loadExecutor = Executors.newSingleThreadExecutor()
    private val acceptingFrame = AtomicBoolean(true)
    private val loggedFirstInference = AtomicBoolean(false)
    private val pending = ConcurrentHashMap<Long, PendingFrame>()

    private var detector: ObjectDetector? = null
    private var closed = false
    private var lastTimestampMs = 0L

    private val readinessState = MutableStateFlow(DetectorReadiness.LOADING)
    override val readiness: StateFlow<DetectorReadiness> = readinessState.asStateFlow()

    private val detailState = MutableStateFlow("Checking the on-device model.")
    override val statusDetail: StateFlow<String> = detailState.asStateFlow()

    private val frameState = MutableStateFlow<DetectionFrame?>(null)
    override val latestFrame: StateFlow<DetectionFrame?> = frameState.asStateFlow()

    private val errorState = MutableStateFlow<String?>(null)
    override val inferenceError: StateFlow<String?> = errorState.asStateFlow()

    override val statusLabel: String
        get() = readinessState.value.name

    override val isRealDetector: Boolean
        get() = readinessState.value == DetectorReadiness.READY

    init {
        loadExecutor.execute { load() }
    }

    override fun currentObservation(): EnvironmentObservation {
        return DetectionReports.observation(
            readiness = readinessState.value,
            detail = detailState.value,
            frame = frameState.value,
            modelName = DetectorConfig.MODEL_DISPLAY_NAME,
            scoreThreshold = DetectorConfig.SCORE_THRESHOLD,
        )
    }

    /**
     * Called on the camera analyzer thread. The caller closes [image] after this returns.
     * [frontCamera] is true when the preview is the mirrored front lens.
     */
    fun analyze(image: ImageProxy, frontCamera: Boolean) {
        val active = currentDetector() ?: return
        if (!acceptingFrame.compareAndSet(true, false)) {
            return
        }
        var timestamp = -1L
        try {
            val rotation = image.imageInfo.rotationDegrees
            val raw = rgbaBitmap(image)
            val upright = rotateUpright(raw, rotation)
            if (upright !== raw) {
                raw.recycle()
            }
            timestamp = synchronized(lock) {
                val next = FrameTimestamps.next(lastTimestampMs, SystemClock.uptimeMillis())
                lastTimestampMs = next
                next
            }
            pending[timestamp] = PendingFrame(
                width = upright.width,
                height = upright.height,
                startedElapsedMs = SystemClock.elapsedRealtime(),
                mirrorHorizontally = frontCamera,
            )
            prunePending()
            active.detectAsync(BitmapImageBuilder(upright).build(), timestamp)
        } catch (error: Exception) {
            if (timestamp >= 0L) {
                pending.remove(timestamp)
            }
            acceptingFrame.set(true)
            errorState.value = "A frame was not submitted: ${error.message ?: error.javaClass.simpleName}"
            Log.w(TAG, "detectAsync was not run", error)
        }
    }

    override fun clearFrame() {
        frameState.value = null
    }

    override fun close() {
        val toClose = synchronized(lock) {
            closed = true
            detector.also { detector = null }
        }
        acceptingFrame.set(false)
        toClose?.close()
        loadExecutor.shutdown()
    }

    private fun load() {
        if (closed) {
            return
        }
        val present = try {
            appContext.assets.open(DetectorConfig.MODEL_ASSET_PATH).use { true }
        } catch (error: IOException) {
            false
        }
        if (!present) {
            readinessState.value = DetectorReadiness.MODEL_MISSING
            detailState.value = DetectorConfig.MODEL_ASSET_PATH
            Log.e(TAG, "model asset missing: ${DetectorConfig.MODEL_ASSET_PATH}")
            return
        }
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(DetectorConfig.MODEL_ASSET_PATH)
                .setDelegate(Delegate.CPU)
                .build()
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMaxResults(DetectorConfig.MAX_RESULTS)
                .setScoreThreshold(DetectorConfig.SCORE_THRESHOLD)
                .setResultListener(this::onResult)
                .setErrorListener(this::onError)
                .build()
            val created = ObjectDetector.createFromOptions(appContext, options)
            val discarded = synchronized(lock) {
                if (closed) {
                    true
                } else {
                    detector = created
                    false
                }
            }
            if (discarded) {
                created.close()
                return
            }
            readinessState.value = DetectorReadiness.READY
            detailState.value = DetectorConfig.MODEL_DISPLAY_NAME
            Log.i(TAG, "detector ready model=${DetectorConfig.MODEL_DISPLAY_NAME} delegate=CPU")
        } catch (error: Throwable) {
            readinessState.value = DetectorReadiness.LOAD_FAILED
            detailState.value = "${error.javaClass.simpleName}: ${error.message ?: "no message"}"
            Log.e(TAG, "detector load failed", error)
        }
    }

    private fun onResult(result: ObjectDetectorResult, input: MPImage) {
        acceptingFrame.set(true)
        if (closed) {
            return
        }
        val meta = pending.remove(result.timestampMs())
        val width = meta?.width ?: input.width
        val height = meta?.height ?: input.height
        val latencyMs = meta?.let { (SystemClock.elapsedRealtime() - it.startedElapsedMs).toDouble() }
        if (latencyMs != null && latencyMs.isFinite() && latencyMs >= 0.0) {
            performance.onInferenceCompleted(latencyMs)
        }
        val objects = result.detections().mapNotNull { detection ->
            val category = detection.categories().firstOrNull() ?: return@mapNotNull null
            val box = detection.boundingBox()
            val label = category.categoryName()?.takeIf { it.isNotBlank() }
                ?: "class ${category.index()}"
            DetectedObject(
                label = label,
                confidence = category.score(),
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom,
            )
        }
        frameState.value = DetectionFrame(
            objects = objects,
            imageWidth = width,
            imageHeight = height,
            mirrorHorizontally = meta?.mirrorHorizontally == true,
            inferenceLatencyMs = latencyMs,
            fromRealDetector = true,
            completedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
        errorState.value = null
        if (loggedFirstInference.compareAndSet(false, true)) {
            Log.i(TAG, "first inference latencyMs=$latencyMs objects=${objects.size}")
        }
    }

    private fun onError(error: RuntimeException) {
        acceptingFrame.set(true)
        errorState.value = error.message ?: "Object detector inference failed."
        frameState.value = null
        Log.e(TAG, "inference error", error)
    }

    private fun currentDetector(): ObjectDetector? {
        return synchronized(lock) {
            if (closed || readinessState.value != DetectorReadiness.READY) {
                null
            } else {
                detector
            }
        }
    }

    private fun prunePending() {
        while (pending.size > 8) {
            val oldest = pending.keys.minOrNull() ?: return
            pending.remove(oldest)
        }
    }

    private data class PendingFrame(
        val width: Int,
        val height: Int,
        val startedElapsedMs: Long,
        val mirrorHorizontally: Boolean,
    )

    companion object {
        private const val TAG = "TriGuideDetect"

        private fun rgbaBitmap(image: ImageProxy): Bitmap {
            val plane = image.planes.firstOrNull()
                ?: throw IllegalArgumentException("Camera frame has no image plane.")
            val width = image.width
            val height = image.height
            if (width <= 0 || height <= 0) {
                throw IllegalArgumentException("Camera frame has no size.")
            }
            val buffer = plane.buffer
            buffer.rewind()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            if (pixelStride == 4 && rowStride == width * 4) {
                bitmap.copyPixelsFromBuffer(buffer)
                return bitmap
            }
            val pixels = IntArray(width * height)
            var offset = 0
            for (y in 0 until height) {
                val rowStart = y * rowStride
                for (x in 0 until width) {
                    val index = rowStart + x * pixelStride
                    val red = buffer.get(index).toInt() and 0xFF
                    val green = buffer.get(index + 1).toInt() and 0xFF
                    val blue = buffer.get(index + 2).toInt() and 0xFF
                    val alpha = buffer.get(index + 3).toInt() and 0xFF
                    pixels[offset] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                    offset += 1
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return bitmap
        }

        private fun rotateUpright(source: Bitmap, rotationDegrees: Int): Bitmap {
            val normalized = ((rotationDegrees % 360) + 360) % 360
            if (normalized == 0) {
                return source
            }
            if (normalized != 90 && normalized != 180 && normalized != 270) {
                throw IllegalArgumentException("Unsupported frame rotation $rotationDegrees.")
            }
            val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
            return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
    }
}
