package com.triguide.ai.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/** Shared by static and live debug probes; never used by production perception. Single-thread owner. */
internal class DiagnosticSegmenter(
    context: Context,
    variant: String,
    private val backend: String,
    private val encoding: String = REFERENCE_RGB,
) : Closeable {
    data class ModelSpec(val id: String, val assetPath: String, val sha256: String, val type: DataType)
    val model = MODELS[variant] ?: error("Unknown variant: $variant")
    private val interpreter: Interpreter
    private val input: ByteBuffer
    private val output: ByteBuffer
    private val canvasBitmap: Bitmap
    private val canvas: Canvas
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pixels: IntArray
    private val rgbBytes: ByteArray
    private var closed = false

    init {
        require(backend == "cpu" || backend == "nnapi")
        require(encoding == REFERENCE_RGB || encoding == LEGACY_INPUT)
        val mapped = context.assets.openFd(model.assetPath).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use {
                it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(mapped.duplicate())
        val actualHash = digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
        require(actualHash == model.sha256) { "Model hash mismatch: $actualHash" }
        interpreter = Interpreter(mapped, Interpreter.Options().setNumThreads(THREADS).apply {
            if (backend == "nnapi") setUseNNAPI(true)
        })
        var allocatedBitmap: Bitmap? = null
        try {
            val source = interpreter.getInputTensor(0)
            val target = interpreter.getOutputTensor(0)
            require(source.shape().contentEquals(intArrayOf(1, SIZE, SIZE, 3)))
            require(source.dataType() == model.type)
            require(target.shape().contentEquals(intArrayOf(1, SIZE, SIZE)))
            require(target.dataType() == DataType.INT32 && target.numBytes() == SIZE * SIZE * 4)
            if (model.type != DataType.FLOAT32) {
                require(source.quantizationParams().scale.isFinite() && source.quantizationParams().scale > 0f)
            }
            input = ByteBuffer.allocateDirect(source.numBytes()).order(ByteOrder.nativeOrder())
            output = ByteBuffer.allocateDirect(target.numBytes()).order(ByteOrder.nativeOrder())
            canvasBitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { allocatedBitmap = it }
            canvas = Canvas(canvasBitmap)
            pixels = IntArray(SIZE * SIZE)
            rgbBytes = ByteArray(SIZE * SIZE * 3)
        } catch (error: Throwable) {
            try { interpreter.close() } catch (closeError: Throwable) { error.addSuppressed(closeError) }
            allocatedBitmap?.recycle()
            throw error
        }
    }

    fun contractLine(): String {
        val source = interpreter.getInputTensor(0)
        val target = interpreter.getOutputTensor(0)
        return "model=${model.id} sha256=${model.sha256} " +
            "input=${source.shape().contentToString()}/${source.dataType()}/bytes=${source.numBytes()}/" +
            "q=${source.quantizationParams().scale},${source.quantizationParams().zeroPoint} " +
            "output=${target.shape().contentToString()}/${target.dataType()}/bytes=${target.numBytes()}"
    }

    /** Same centered canvas as the historical static probe; buffers reused across live frames. */
    fun prepare(source: Bitmap) {
        check(!closed)
        canvas.drawColor(Color.rgb(128, 128, 128))
        val scale = min(SIZE.toFloat() / source.width, SIZE.toFloat() / source.height)
        val width = source.width * scale
        val height = source.height * scale
        val left = (SIZE - width) / 2f
        val top = (SIZE - height) / 2f
        canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height), paint)
        canvasBitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        input.clear()
        if (model.type == DataType.UINT8 && encoding == REFERENCE_RGB) {
            DiagnosticRgbPacking.pack(pixels, rgbBytes)
            input.put(rgbBytes) // One native-buffer bulk copy instead of 786,432 scalar writes.
            input.rewind()
            return
        }
        val quant = interpreter.getInputTensor(0).quantizationParams()
        pixels.forEach { pixel ->
            putChannel(Color.red(pixel), quant.scale, quant.zeroPoint)
            putChannel(Color.green(pixel), quant.scale, quant.zeroPoint)
            putChannel(Color.blue(pixel), quant.scale, quant.zeroPoint)
        }
        input.rewind()
    }

    private fun putChannel(channel: Int, scale: Float, zeroPoint: Int) {
        when {
            model.type == DataType.FLOAT32 -> input.putFloat(channel / 127.5f - 1f)
            encoding == REFERENCE_RGB -> input.put(channel.toByte())
            else -> input.put(((channel / 127.5f - 1f) / scale + zeroPoint)
                .roundToInt().coerceIn(0, 255).toByte())
        }
    }

    fun invoke() {
        check(!closed)
        input.rewind()
        output.rewind()
        interpreter.run(input, output)
    }

    fun decode(): IntArray {
        output.rewind()
        return Ade20kInt32ClassMap.decode(output)
    }

    override fun close() {
        if (!closed) {
            closed = true
            try { interpreter.close() } finally { canvasBitmap.recycle() }
        }
    }

    companion object {
        const val SIZE = 512
        const val THREADS = 4
        const val REFERENCE_RGB = "reference-rgb"
        const val LEGACY_INPUT = "normalized-quantized"
        private val MODELS = mapOf(
            "float" to ModelSpec("deeplabv3_mnv2_ade20k_float", "diagnostics/deeplabv3_mnv2_ade20k_float.tflite",
                "12a2141f28928a9bccce5063d0f868211c9d2633a263b9141594ff300daeafc0", DataType.FLOAT32),
            "uint8" to ModelSpec("deeplabv3_mnv2_ade20k_uint8", "diagnostics/deeplabv3_mnv2_ade20k_uint8.tflite",
                "2d58c681d81b5e181816b81cc815b82edf3daad905b4d7d35afade80aee93b5f", DataType.UINT8),
            "mosaic" to ModelSpec("mobile_segmenter_r4_quant_argmax_uint8", "diagnostics/mobile_segmenter_r4_quant_argmax_uint8.tflite",
                "da8b3822a22fde45ecaf0ff32022287eff58b500096971b04f3c1b292da66a3c", DataType.UINT8),
        )
    }
}
