
package expo.modules.facerecognition.camera
import expo.modules.facerecognition.FaceOverlayView

import android.app.Activity
import android.util.Log
import android.graphics.RectF
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import android.util.Size



import com.insightface.sdk.inspireface.InspireFace
import expo.modules.facerecognition.core.InspireFaceManager

class NativeCameraController(
    private val context: Context,
    private val activity: Activity,
    private val previewView: PreviewView,
    private val faceOverlayView: FaceOverlayView
) {

    companion object {
        private const val TAG = "NativeCameraController"
    }

    private var camera: Camera? = null
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensType = CameraSelector.LENS_FACING_FRONT
    private var imageAnalysis: ImageAnalysis? = null
    private val nv21Converter = Nv21Converter()

    fun start() {
        previewView.post {
            setupCamera()
        }
    }

    fun stop() {
        cameraProvider?.unbindAll()

        camera = null
        preview = null
        imageAnalysis = null
        cameraProvider = null
    }

    private fun setupCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(activity)

        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(activity)
        )
    }

    private fun bindCameraUseCases() {
        
        Log.e(
            TAG,
            "PreviewView: ${previewView.width}x${previewView.height}"
        )

        if (previewView.display == null) {
            return
        }

        val rotation = previewView.display.rotation

        val cameraProvider = cameraProvider
            ?: throw IllegalStateException(
                "Camera initialization failed."
            )

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensType)
            .build()

        val aspectRatioStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_16_9,
            AspectRatioStrategy.FALLBACK_RULE_NONE
        )

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .build()

        preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(
                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            )
            .build()
        
        imageAnalysis?.setAnalyzer(
            ContextCompat.getMainExecutor(activity)
        ) { image ->
            analyzeFrame(image)
        }

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview!!)
            .addUseCase(imageAnalysis!!)
            .build()

        cameraProvider.unbindAll()

        camera = cameraProvider.bindToLifecycle(
            activity as AppCompatActivity,
            cameraSelector,
            useCaseGroup
        )

        preview?.surfaceProvider = previewView.surfaceProvider
        
        previewView.previewStreamState.observe(
            activity as androidx.lifecycle.LifecycleOwner
        ) { state ->
            Log.e(
                TAG,
                "PreviewStreamState: $state"
            )

            if (state == PreviewView.StreamState.STREAMING) {
                Log.e(
                    TAG,
                    "Preview OutputTransform matrix=${previewView.outputTransform?.matrix}"
                )
            }
        }
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        try {
            
            val yPlane = imageProxy.planes[0]

            Log.e(
                TAG,
                "Y PLANE: width=${imageProxy.width}, " +
                    "height=${imageProxy.height}, " +
                    "rowStride=${yPlane.rowStride}, " +
                    "pixelStride=${yPlane.pixelStride}, " +
                    "position=${yPlane.buffer.position()}, " +
                    "limit=${yPlane.buffer.limit()}, " +
                    "remaining=${yPlane.buffer.remaining()}"
            )

            val nv21 = nv21Converter.convert(imageProxy)

            val bitmap = imageProxyToBitmap(imageProxy)

            val sensorToBuffer = imageProxy.imageInfo.getSensorToBufferTransformMatrix()

            val analysisTransform = OutputTransform(
                sensorToBuffer,
                Size(imageProxy.width, imageProxy.height)
            )

            val stream = InspireFace.CreateImageStreamFromBitmap(
                bitmap,
                InspireFace.CAMERA_ROTATION_270
            )
    
            val session = InspireFaceManager.getRegistrationSession()
    
            val faceData = InspireFace.ExecuteFaceTrack(
                session,
                stream
            )

            val outputTransform = previewView.outputTransform
                            
            if (faceData.detectedNum > 0) {
                val rect = faceData.rects[0]

                Log.e(
                    TAG,
                    "FaceRect raw: x=${rect.x}, y=${rect.y}, width=${rect.width}, height=${rect.height}"
                )
                
                val faceRect = RectF(
                    rect.x.toFloat(),
                    rect.y.toFloat(),
                    (rect.x + rect.width).toFloat(),
                    (rect.y + rect.height).toFloat()
                )
                
                val rotatedRect = RectF(
                    720f - faceRect.bottom,
                    1280f - faceRect.right,
                    720f - faceRect.top,
                    1280f - faceRect.left
                )

                val scaleX = previewView.width / 720f
                val scaleY = previewView.height / 1280f

                rotatedRect.left *= scaleX
                rotatedRect.right *= scaleX
                rotatedRect.top *= scaleY
                rotatedRect.bottom *= scaleY

                Log.e(
                    TAG,
                    "FaceRect mapped: $rotatedRect"
                )

                faceOverlayView.setFaceRect(rotatedRect)
            } else {
                faceOverlayView.setFaceRect(null)
            }
            
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Face tracking failed",
                e
            )
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
    
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
    
        val nv21 = ByteArray(ySize + uSize + vSize)
    
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
    
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
    
        val outputStream = ByteArrayOutputStream()
    
        yuvImage.compressToJpeg(
            Rect(0, 0, image.width, image.height),
            100,
            outputStream
        )
    
        val jpegBytes = outputStream.toByteArray()
    
        return BitmapFactory.decodeByteArray(
            jpegBytes,
            0,
            jpegBytes.size
        )
    }
}