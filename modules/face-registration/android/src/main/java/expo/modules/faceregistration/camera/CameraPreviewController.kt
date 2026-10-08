package expo.modules.faceregistration.camera

import android.content.Context
import android.util.Size
import androidx.annotation.StringRes
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import expo.modules.faceregistration.R
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/**
 * Reusable front-camera preview + analysis component.
 * It keeps both use cases on the same 4:3 crop.
 */
class CameraPreviewController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val analysisExecutor: Executor,
    private val analyzer: ImageAnalysis.Analyzer,
    private val listener: Listener
) {
    private val context = context.applicationContext

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var stopped = false

    interface Listener {
        /**
         * Called when the front camera is ready.
         */
        fun onCameraReady(frontCamera: Boolean)

        fun onCameraError(@StringRes messageRes: Int)
    }

    /**
     * Asynchronously acquires CameraX and binds the front camera.
     */
    fun start() {
        stopped = false

        val future: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(context)

        future.addListener({
            if (stopped) {
                return@addListener
            }

            try {
                cameraProvider = future.get()
            } catch (e: ExecutionException) {
                listener.onCameraError(R.string.msg_engine_failed)
                return@addListener
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                listener.onCameraError(R.string.msg_engine_failed)
                return@addListener
            }

            val frontCamera = CameraSelector.DEFAULT_FRONT_CAMERA

            if (!hasCamera(frontCamera)) {
                listener.onCameraError(R.string.msg_no_front_camera)
                return@addListener
            }

            createUseCases()

            if (bindCurrentLens()) {
                listener.onCameraReady(true)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Stops only this component's use cases; it does not disturb another Activity.
     */
    fun stop() {
        stopped = true

        analysis?.clearAnalyzer()

        val provider = cameraProvider
        val currentPreview = preview
        val currentAnalysis = analysis

        if (provider != null && currentPreview != null && currentAnalysis != null) {
            provider.unbind(currentPreview, currentAnalysis)
        }
    }

    private fun createUseCases() {
        val analysisResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        analysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolution)
            .setBackpressureStrategy(
                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            )
            .build()

        analysis?.setAnalyzer(
            analysisExecutor,
            analyzer
        )

        preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(
                        AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                    )
                    .build()
            )
            .build()

        preview?.setSurfaceProvider(
            previewView.surfaceProvider
        )
    }

    private fun bindCurrentLens(): Boolean {
        val provider = cameraProvider
        val currentPreview = preview
        val currentAnalysis = analysis

        if (provider == null ||
            currentPreview == null ||
            currentAnalysis == null ||
            stopped
        ) {
            return false
        }

        return try {
            provider.unbind(
                currentPreview,
                currentAnalysis
            )

            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                currentPreview,
                currentAnalysis
            )

            true
        } catch (e: RuntimeException) {
            listener.onCameraError(R.string.msg_camera_unavailable)
            false
        }
    }

    private fun hasCamera(selector: CameraSelector): Boolean {
        return try {
            cameraProvider?.hasCamera(selector) == true
        } catch (e: CameraInfoUnavailableException) {
            false
        }
    }
}
