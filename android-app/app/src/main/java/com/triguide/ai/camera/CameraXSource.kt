package com.triguide.ai.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.triguide.ai.domain.CameraSource
import com.triguide.ai.domain.CameraStartResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Live preview plus a frame counter. The analyzer closes each frame and does not classify it.
 */
class CameraXSource : CameraSource {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzerExecutor: ExecutorService? = null

    override fun start(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrameTimestampNs: (Long) -> Unit,
        onResult: (CameraStartResult) -> Unit,
    ) {
        val executor = analyzerExecutor ?: Executors.newSingleThreadExecutor().also { analyzerExecutor = it }
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    val provider = future.get()
                    cameraProvider = provider
                    val selector = selectCamera(provider)
                    if (selector == null) {
                        onResult(CameraStartResult.Unavailable("No camera is available on this device."))
                        return@addListener
                    }
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { image ->
                        try {
                            onFrameTimestampNs(image.imageInfo.timestamp)
                        } finally {
                            image.close()
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                    onResult(CameraStartResult.Streaming)
                } catch (error: Exception) {
                    onResult(CameraStartResult.Failed(error.message ?: "Camera failed to start."))
                }
            },
            mainExecutor,
        )
    }

    override fun stop() {
        cameraProvider?.unbindAll()
        analyzerExecutor?.shutdown()
        analyzerExecutor = null
        cameraProvider = null
    }

    private fun selectCamera(provider: ProcessCameraProvider): CameraSelector? {
        return try {
            when {
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> null
            }
        } catch (error: Exception) {
            null
        }
    }
}
