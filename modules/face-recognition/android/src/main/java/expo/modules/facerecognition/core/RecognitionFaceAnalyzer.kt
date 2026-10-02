package expo.modules.facerecognition.core

import android.content.Context
import android.graphics.RectF
import android.os.SystemClock
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import com.insightface.sdk.inspireface.InspireFace
import com.insightface.sdk.inspireface.base.FaceFeature
import com.insightface.sdk.inspireface.base.FaceRect
import com.insightface.sdk.inspireface.base.ImageStream
import com.insightface.sdk.inspireface.base.MultipleFaceData
import com.insightface.sdk.inspireface.base.Session
import expo.modules.facerecognition.R

/**
 * Tracks only SDK face 0 and searches it after roughly one stable second.
 */
class RecognitionFaceAnalyzer(
    context: Context,
    private val overlay: FaceOverlayView,
    private val repository: FaceRepository,
    private val libraryEmpty: Boolean,
    private val listener: Listener
) : UprightFaceCameraAnalyzer() {

    enum class State {
        NO_FACE,
        MOVE_CLOSER,
        HOLD_STILL,
        SEARCHING,
        MATCHED,
        NO_MATCH,
        EMPTY_LIBRARY
    }

    interface Listener {
        /**
         * Called on the camera analysis executor.
         */
        fun onState(
            state: State,
            record: FaceRecord?,
            confidence: Float,
            threshold: Float
        )

        fun onSessionError()
    }

    companion object {
        private const val STABLE_RECOGNITION_MS = 1_000L
        private const val MIN_FACE_WIDTH_RATIO = 0.12f
    }

    private val stabilityGate =
        FaceStabilityGate(STABLE_RECOGNITION_MS, 1L)

    private val waitingColor =
        ContextCompat.getColor(context, R.color.liveness_accent)

    private val matchColor =
        waitingColor

    private val noMatchColor =
        ContextCompat.getColor(context, R.color.liveness_fail)

    private val warningColor =
        ContextCompat.getColor(context, R.color.liveness_warn)

    @Volatile
    private var mirrored = true

    @Volatile
    private var resetRequested = false

    private var recognizedStableRun = false
    private var lastState: State? = null
    private var currentBoxColor = waitingColor

    override fun beforeFrame() {
        if (resetRequested) {
            resetRequested = false
            resetRecognition()
        }
    }

    override fun createSession(): Session {
         return requireNotNull(
            FaceEngine.createVideoRecognitionSession()
         )
   }

    override fun onFaces(
        session: Session?,
        stream: ImageStream,
        faces: MultipleFaceData?,
        uprightNv21: ByteArray,
        uprightWidth: Int,
        uprightHeight: Int,
        frameStart: Long
    ) {
        if (faces == null || faces.detectedNum == 0) {
            resetRecognition()
            overlay.submit(null)
            report(
                State.NO_FACE,
                null,
                Float.NaN,
                Float.NaN
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

        if (face.width() < uprightWidth * MIN_FACE_WIDTH_RATIO) {
            resetRecognition()
            currentBoxColor = warningColor

            submitFace(
                face,
                uprightWidth,
                uprightHeight
            )

            report(
                State.MOVE_CLOSER,
                null,
                Float.NaN,
                Float.NaN
            )

            return
        }

        val trackId =
            if (faces.trackIds != null && faces.trackIds.isNotEmpty()) {
                faces.trackIds[0]
            } else {
                0
            }

        val stable = stabilityGate.update(
            trackId,
            face.left,
            face.top,
            face.right,
            face.bottom,
            SystemClock.elapsedRealtime()
        )

        if (stable < 0f) {
            recognizedStableRun = false
            currentBoxColor = waitingColor

            submitFace(
                face,
                uprightWidth,
                uprightHeight
            )

            report(
                State.HOLD_STILL,
                null,
                Float.NaN,
                Float.NaN
            )

            return
        }

        if (!recognizedStableRun) {
            recognizedStableRun = true

            if (libraryEmpty) {
                currentBoxColor = warningColor

                submitFace(
                    face,
                    uprightWidth,
                    uprightHeight
                )

                report(
                    State.EMPTY_LIBRARY,
                    null,
                    Float.NaN,
                    Float.NaN
                )
            } else {
               
               val activeSession = session ?: run {
                   onSessionError()
                   return
               }

                recognize(
                    activeSession,
                    stream,
                    faces,
                    face,
                    uprightWidth,
                    uprightHeight
                )
            }
        } else {
            submitFace(
                face,
                uprightWidth,
                uprightHeight
            )
        }
    }

    private fun recognize(
        session: Session,
        stream: ImageStream,
        faces: MultipleFaceData,
        face: RectF,
        imageWidth: Int,
        imageHeight: Int
    ) {
        currentBoxColor = warningColor

        submitFace(
            face,
            imageWidth,
            imageHeight
        )

        report(
            State.SEARCHING,
            null,
            Float.NaN,
            Float.NaN
        )

        val feature: FaceFeature =
            InspireFace.ExtractFaceFeature(
                session,
                stream,
                faces.tokens[0]
            )

        val result = repository.search(feature)

        if (result.matched && result.record != null) {
            currentBoxColor = matchColor

            report(
                State.MATCHED,
                result.record,
                result.confidence,
                result.threshold
            )
        } else {
            currentBoxColor = noMatchColor

            report(
                State.NO_MATCH,
                null,
                result.confidence,
                result.threshold
            )
        }

        submitFace(
            face,
            imageWidth,
            imageHeight
        )
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

    private fun resetRecognition() {
        stabilityGate.reset()
        recognizedStableRun = false
        currentBoxColor = waitingColor
    }

    private fun submitFace(
        face: RectF,
        imageWidth: Int,
        imageHeight: Int
    ) {
        overlay.submit(
            FaceOverlayView.Frame(
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                mirrored = mirrored,
                rects = arrayOf(RectF(face)),
                color = currentBoxColor
            )
        )
    }

    private fun report(
        state: State,
        record: FaceRecord?,
        confidence: Float,
        threshold: Float
    ) {
        if (state == lastState &&
            state != State.MATCHED &&
            state != State.NO_MATCH
        ) {
            return
        }

        lastState = state

        listener.onState(
            state,
            record,
            confidence,
            threshold
        )
    }
}
