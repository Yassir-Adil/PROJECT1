package com.triguide.ai.diagnostics

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil

/** Static debug-only probe, sharing the hash-pinned interpreter with the live diagnostic. */
class DeepLabDiagnosticActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread({
            try { runProbe() }
            catch (error: Throwable) { Log.e(TAG, "RUN_FAILED ${error.javaClass.simpleName}: ${error.message}", error) }
            finally { runOnUiThread { finish() } }
        }, "triguide-segmenter-diagnostic").start()
    }

    private fun runProbe() {
        val variant = intent.getStringExtra(EXTRA_VARIANT)?.lowercase(Locale.US) ?: VARIANT_FLOAT
        val backend = intent.getStringExtra(EXTRA_BACKEND)?.lowercase(Locale.US) ?: BACKEND_CPU
        val encoding = intent.getStringExtra(EXTRA_INPUT_ENCODING) ?: INPUT_REFERENCE_RGB
        val runs = intent.getIntExtra(EXTRA_RUNS, 20)
        require(runs in 1..2000)
        // Private fixtures are supplied separately via authorized local QA, never APK assets.
        val image = File(filesDir, "diagnostic-inputs/scene.jpg")
        require(image.isFile && image.length() in 1..20_000_000L) {
            "Missing or oversized app-private diagnostic-inputs/scene.jpg; no bundled private image"
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(image.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 16_777_216L) { "Invalid or oversized diagnostic image" }
        val imageHash = image.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var count = stream.read(buffer)
            while (count != -1) {
                digest.update(buffer, 0, count)
                count = stream.read(buffer)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        val bitmap = BitmapFactory.decodeFile(image.path)
            ?: error("Could not decode diagnostic image")
        try {
            DiagnosticSegmenter(this, variant, backend, encoding).use { engine ->
                Log.i(TAG, "MODEL_CONTRACT ${engine.contractLine()}")
                engine.prepare(bitmap)
                Log.i(TAG, "RUN_START model=${engine.model.id} backend=$backend sha256=${engine.model.sha256} " +
                    "image=${bitmap.width}x${bitmap.height} image_sha256=$imageHash input=[1, 512, 512, 3] output=[1, 512, 512] " +
                    "preprocess=aspect_fit_gray128_rgb input_encoding=${if (variant == VARIANT_FLOAT) "float_minus1_to1" else encoding} " +
                    "threads=${DiagnosticSegmenter.THREADS} warmups=2 runs=$runs")
                repeat(2) { engine.invoke() }
                val timings = ArrayList<Double>(runs)
                repeat(runs) {
                    val started = SystemClock.elapsedRealtimeNanos()
                    engine.invoke()
                    timings += (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                }
                val map = engine.decode()
                val sorted = timings.sorted()
                val mean = timings.average()
                fun percentile(q: Double) = format(sorted[(ceil(q * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)])
                Log.i(TAG, "RUN_RESULT model=${engine.model.id} backend=$backend runs=$runs " +
                    "inference_p50_ms=${percentile(0.5)} inference_p90_ms=${percentile(0.9)} " +
                    "inference_mean_ms=${format(mean)} sequential_inference_fps=${format(1000.0 / mean)} " +
                    "measurement=identical-static-image-replay-not-live-camera")
                Log.i(TAG, "CLASS_IDS full=${classes(map, 0, 512, 0, 512)} " +
                    "lower_half=${classes(map, 256, 512, 0, 512)} lower_center=${classes(map, 320, 512, 128, 384)} " +
                    "taxonomy=ade20k-top31-plus-background prediction_accuracy=unmeasured")
                Log.i(TAG, "VOICE_EVENTS=none diagnostic-only; semantic class ids are not walkability or movement advice")
            }
        } finally { bitmap.recycle() }
    }

    private fun classes(map: IntArray, top: Int, bottom: Int, left: Int, right: Int): String {
        val counts = IntArray(32)
        for (y in top until bottom) for (x in left until right) counts[map[y * 512 + x]]++
        val total = (bottom - top) * (right - left)
        return counts.indices.filter { counts[it] > 0 }.sortedByDescending { counts[it] }.take(8).joinToString(",") {
            "id_$it(${Ade20kTop31ClassIds.labelName(it)}):${format(counts[it] * 100.0 / total)}%"
        }
    }

    private fun format(value: Double) = "%.2f".format(Locale.US, value)

    companion object {
        const val EXTRA_VARIANT = "triguide.diagnostic.variant"
        const val EXTRA_BACKEND = "triguide.diagnostic.backend"
        const val EXTRA_RUNS = "triguide.diagnostic.runs"
        const val EXTRA_INPUT_ENCODING = "triguide.diagnostic.input_encoding"
        const val INPUT_REFERENCE_RGB = DiagnosticSegmenter.REFERENCE_RGB
        const val INPUT_NORMALIZED_QUANTIZED = DiagnosticSegmenter.LEGACY_INPUT
        const val VARIANT_FLOAT = "float"
        const val VARIANT_UINT8 = "uint8"
        const val BACKEND_CPU = "cpu"
        const val BACKEND_NNAPI = "nnapi"
        private const val TAG = "TriGuideSegmenterDiag"
    }
}
