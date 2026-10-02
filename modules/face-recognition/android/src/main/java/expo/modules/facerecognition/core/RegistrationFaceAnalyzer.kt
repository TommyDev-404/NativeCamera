package expo.modules.facerecognition.core

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.os.SystemClock
import androidx.core.content.ContextCompat
import expo.modules.facerecognition.R
import com.insightface.sdk.inspireface.base.FaceRect
import com.insightface.sdk.inspireface.base.ImageStream
import com.insightface.sdk.inspireface.base.MultipleFaceData
import com.insightface.sdk.inspireface.base.Session
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class RegistrationFaceAnalyzer(
    context: Context,
    private val overlay: FaceOverlayView,
    private val captureDirectory: File,
    private val listener: Listener
) : UprightFaceCameraAnalyzer() {

    enum class Stage {
        NO_FACE,
        MOVE_CLOSER,
        HOLD_STILL,
        CAPTURING,
        COMPLETE
    }

    interface Listener {
        fun onState(stage: Stage, progress: Float)

        fun onCaptured(path: String)

        fun onCaptureError()

        fun onSessionError()
    }

    companion object {
        private const val STATE_REPORT_INTERVAL_MS = 80L
        private const val MIN_FACE_WIDTH_RATIO = 0.18f
    }

    private val stabilityGate = FaceStabilityGate()

    private val accentColor =
        ContextCompat.getColor(
            context,
            R.color.liveness_accent
        )

    private val redColor =
        ContextCompat.getColor(
            context,
            R.color.liveness_fail
        )

    private val yellowColor =
        ContextCompat.getColor(
            context,
            R.color.liveness_warn
        )

    private val greenColor = accentColor

    private var lastStateReport = 0L
    private var lastStage: Stage? = null
    private var lastProgressBucket = -1

    @Volatile
    private var mirrored = true

    @Volatile
    private var resetRequested = false

    private var captured = false

    override fun shouldSkipFrame(): Boolean {
        return captured
    }

    override fun beforeFrame() {
        if (resetRequested) {
            resetRequested = false
            clearStability()
        }
    }

    override fun createSession(): Session? {
        return FaceEngine.createTrackingSession()
    }

    override fun onFaces(
        session: Session?,
        stream: ImageStream,
        faces: MultipleFaceData?,
        upright: ByteArray,
        uprightWidth: Int,
        uprightHeight: Int,
        frameStart: Long
    ) {
        if (faces == null || faces.detectedNum == 0) {
            clearStability()
            overlay.submit(null)
            reportState(
                Stage.NO_FACE,
                0f
            )
            return
        }

        val first: FaceRect = faces.rects[0]

        val face = RectF(
            first.x.toFloat(),
            first.y.toFloat(),
            (first.x + first.width).toFloat(),
            (first.y + first.height).toFloat()
        )

        val trackId =
            if (faces.trackIds != null && faces.trackIds.isNotEmpty()) {
                faces.trackIds[0]
            } else {
                0
            }

        if (face.width() < uprightWidth * MIN_FACE_WIDTH_RATIO) {
            clearStability()

            submitFrame(
                face,
                uprightWidth,
                uprightHeight,
                -1f,
                redColor,
                redColor
            )

            reportState(
                Stage.MOVE_CLOSER,
                0f
            )

            return
        }

        val progress = stabilityGate.update(
            trackId,
            face.left,
            face.top,
            face.right,
            face.bottom,
            SystemClock.elapsedRealtime()
        )

        if (progress < 0f) {
            submitFrame(
                face,
                uprightWidth,
                uprightHeight,
                -1f,
                accentColor,
                accentColor
            )

            reportState(
                Stage.HOLD_STILL,
                0f
            )

            return
        }

        val progressColor =
            if (progress >= 1f) {
                greenColor
            } else if (progress >= 0.5f) {
                yellowColor
            } else {
                redColor
            }

        submitFrame(
            face,
            uprightWidth,
            uprightHeight,
            progress,
            progressColor,
            progressColor
        )

        reportState(
            if (progress >= 1f) {
                Stage.COMPLETE
            } else {
                Stage.CAPTURING
            },
            progress
        )

        if (progress >= 1f) {
            val capture = writeCapture(
                upright,
                uprightWidth,
                uprightHeight,
                face
            )

            if (capture == null) {
                clearStability()
                listener.onCaptureError()
            } else {
                captured = true
                listener.onCaptured(
                    capture.absolutePath
                )
            }
        }
    }

    override fun onSessionError() {
        listener.onSessionError()
    }

    fun setMirrored(mirrored: Boolean) {
        this.mirrored = mirrored
    }

    fun resetTracking() {
        resetRequested = true
        overlay.submit(null)
    }

    private fun clearStability() {
        stabilityGate.reset()
    }

    private fun submitFrame(
        face: RectF,
        imageWidth: Int,
        imageHeight: Int,
        progress: Float,
        boxColor: Int,
        progressColor: Int
    ) {
        overlay.submit(
            FaceOverlayView.Frame(
                imageWidth,
                imageHeight,
                mirrored,
                arrayOf(RectF(face)),
                boxColor,
                progress,
                progressColor
            )
        )
    }

    private fun reportState(
        stage: Stage,
        progress: Float
    ) {
        val now = SystemClock.elapsedRealtime()
        val bucket = Math.round(progress * 100f)

        if (
            stage != lastStage ||
            bucket != lastProgressBucket ||
            now - lastStateReport >= STATE_REPORT_INTERVAL_MS
        ) {
            lastStage = stage
            lastProgressBucket = bucket
            lastStateReport = now

            listener.onState(
                stage,
                progress
            )
        }
    }

    private fun writeCapture(
        nv21: ByteArray,
        width: Int,
        height: Int,
        face: RectF
    ): File? {
        if (
            (!captureDirectory.exists() && !captureDirectory.mkdirs()) ||
            !captureDirectory.isDirectory
        ) {
            return null
        }

        val destination: File

        try {
            destination = File.createTempFile(
                "face_",
                ".jpg",
                captureDirectory
            )
        } catch (e: IOException) {
            return null
        }

        val crop = paddedEvenCrop(
            face,
            width,
            height
        )

        try {
            FileOutputStream(destination).use { output ->
                val compressed = YuvImage(
                    nv21,
                    ImageFormat.NV21,
                    width,
                    height,
                    null
                ).compressToJpeg(
                    crop,
                    94,
                    output
                )

                if (!compressed) {
                    destination.delete()
                    return null
                }

                output.flush()
                output.fd.sync()

                return destination
            }
        } catch (e: IOException) {
            destination.delete()
            return null
        } catch (e: RuntimeException) {
            destination.delete()
            return null
        }
    }

   private fun paddedEvenCrop(
      face: RectF,
      width: Int,
      height: Int
   ): Rect {
      val paddingX = face.width() * 0.45f
      val paddingY = face.height() * 0.55f

      val left = maxOf(
         0,
         (Math.floor((face.left - paddingX).toDouble()).toInt()) and -2
      )
      val top = maxOf(
         0,
         (Math.floor((face.top - paddingY).toDouble()).toInt()) and -2
      )
      val right = minOf(
         width,
         (Math.ceil((face.right + paddingX).toDouble()).toInt() + 1) and -2
      )
      val bottom = minOf(
         height,
         (Math.ceil((face.bottom + paddingY).toDouble()).toInt() + 1) and -2
      )

      if (
         right <= left + 2 ||
         bottom <= top + 2
      ) {
         return Rect(
               0,
               0,
               width and -2,
               height and -2
         )
      }

      return Rect(
         left,
         top,
         right,
         bottom
      )
   }

    /*
    private fun paddedEvenCrop(
        face: RectF,
        width: Int,
        height: Int
    ): Rect {
        val paddingX = face.width() * 0.45f
        val paddingY = face.height() * 0.55f

        val left = maxOf(
            0,
            (Math.floor(face.left - paddingX).toInt()) and -2
        )

        val top = maxOf(
            0,
            (Math.floor(face.top - paddingY).toInt()) and -2
        )

        val right = minOf(
            width,
            (Math.ceil(face.right + paddingX).toInt() + 1) and -2
        )

        val bottom = minOf(
            height,
            (Math.ceil(face.bottom + paddingY).toInt() + 1) and -2
        )

        if (
            right <= left + 2 ||
            bottom <= top + 2
        ) {
            return Rect(
                0,
                0,
                width and -2,
                height and -2
            )
        }

        return Rect(
            left,
            top,
            right,
            bottom
        )
    }
        */
}