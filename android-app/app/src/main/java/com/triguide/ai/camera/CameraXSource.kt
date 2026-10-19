package com.triguide.ai.camera

import android.content.Context
import android.util.Rational
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import com.triguide.ai.domain.CameraSource
import com.triguide.ai.domain.CameraStartResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Preview plus analysis. Analysis keeps only the latest frame, in RGBA, so the
 * detector can drop work instead of queueing stale images.
 */
class CameraXSource : CameraSource {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzerExecutor: ExecutorService? = null
    private var bindGeneration: Int = 0

    override fun start(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrameTimestampNs: (Long) -> Unit,
        onAnalysisFrame: (ImageProxy, Boolean) -> Unit,
        onResult: (CameraStartResult) -> Unit,
    ) {
        val executor = analyzerExecutor ?: Executors.newSingleThreadExecutor().also { analyzerExecutor = it }
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val generation = ++bindGeneration
        previewView.doOnLayout {
            if (generation != bindGeneration) {
                return@doOnLayout
            }
            if (previewView.width <= 0 || previewView.height <= 0) {
                onResult(CameraStartResult.Failed("Camera preview has no size yet."))
                return@doOnLayout
            }
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    if (generation != bindGeneration) {
                        return@addListener
                    }
                    try {
                        bind(
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            executor = executor,
                            onFrameTimestampNs = onFrameTimestampNs,
                            onAnalysisFrame = onAnalysisFrame,
                            onResult = onResult,
                            provider = future.get(),
                        )
                    } catch (error: Exception) {
                        onResult(CameraStartResult.Failed(error.message ?: "Camera failed to start."))
                    }
                },
                mainExecutor,
            )
        }
    }

    override fun stop() {
        bindGeneration += 1
        cameraProvider?.unbindAll()
        analyzerExecutor?.shutdown()
        analyzerExecutor = null
        cameraProvider = null
    }

    private fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        executor: ExecutorService,
        onFrameTimestampNs: (Long) -> Unit,
        onAnalysisFrame: (ImageProxy, Boolean) -> Unit,
        onResult: (CameraStartResult) -> Unit,
        provider: ProcessCameraProvider,
    ) {
        cameraProvider = provider
        val selector = selectCamera(provider)
        if (selector == null) {
            onResult(CameraStartResult.Unavailable("No camera is available on this device."))
            return
        }
        val frontCamera = selector == CameraSelector.DEFAULT_FRONT_CAMERA
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        val preview = Preview.Builder()
            .setResolutionSelector(previewResolution())
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolution())
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        // Sole close owner: ImageProxy closes exactly once via use{}. Downstream
        // analyzers (detector, diagnostic segmenter) must copy pixels before return
        // and must not call image.close().
        analysis.setAnalyzer(executor) { image ->
            image.use {
                onFrameTimestampNs(it.imageInfo.timestamp)
                onAnalysisFrame(it, frontCamera)
            }
        }
        val viewPort = ViewPort.Builder(Rational(previewView.width, previewView.height), rotation)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()
        val group = UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)
            .addUseCase(analysis)
            .build()
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, group)
        onResult(CameraStartResult.Streaming)
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

    private fun previewResolution(): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }

    private fun analysisResolution(): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()
    }
}
