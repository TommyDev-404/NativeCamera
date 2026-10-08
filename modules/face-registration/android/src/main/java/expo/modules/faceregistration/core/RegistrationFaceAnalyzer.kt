package expo.modules.faceregistration.core

import android.content.Context
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.insightface.sdk.inspireface.InspireFace
import com.insightface.sdk.inspireface.base.FaceRect
import com.insightface.sdk.inspireface.base.ImageStream
import com.insightface.sdk.inspireface.base.MultipleFaceData
import com.insightface.sdk.inspireface.base.Session
import expo.modules.faceregistration.R

class RegistrationFaceAnalyzer(
    context: Context,
    private val overlay: FaceOverlayView,
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
        fun onEmbeddingGenerated(embedding: FloatArray)
        fun onCaptureError()
        fun onSessionError()
    }

    companion object {
        private const val TAG = "RegistrationFaceAnalyzer"
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

    @Volatile
    private var captured = false

    @Volatile
    private var extracting = false

    override fun shouldSkipFrame(): Boolean {
        return captured || extracting
    }

    override fun beforeFrame() {
        if (resetRequested) {
            resetRequested = false
            captured = false
            extracting = false
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
        if (captured || extracting) {
            return
        }

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
            Stage.CAPTURING,
            progress
        )

        if (progress >= 1f) {
            extracting = true

            generateEmbedding(
                session,
                stream,
                faces
            )
        }
    }

    private fun generateEmbedding(
        session: Session?,
        stream: ImageStream,
        faces: MultipleFaceData
    ) {
        if (captured) {
            return
        }

        val token =
            if (faces.tokens != null && faces.tokens.isNotEmpty()) {
                faces.tokens[0]
            } else {
                null
            }

        if (token == null) {
            Log.e(
                TAG,
                "FaceBasicToken is null"
            )

            extracting = false
            clearStability()
            listener.onCaptureError()

            return
        }

        Log.d(
            TAG,
            "Extracting face feature: handle=${token.handle}, size=${token.size}"
        )

        val feature = try {
            InspireFace.ExtractFaceFeature(
                session,
                stream,
                token
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "ExtractFaceFeature failed",
                e
            )

            null
        }

        if (feature == null) {
            Log.e(
                TAG,
                "ExtractFaceFeature returned null"
            )
            extracting = false
            clearStability()
            listener.onCaptureError()

            return
        }

        val embedding = feature.data

        if (embedding == null || embedding.isEmpty()) {
            Log.e(
                TAG,
                "FaceFeature.data is null or empty"
            )

            extracting = false
            clearStability()
            listener.onCaptureError()

            return
        }

        Log.d(
            TAG,
            "Embedding generated successfully: length=${embedding.size}"
        )

        captured = true
        extracting = false

        listener.onEmbeddingGenerated(
            embedding.copyOf()
        )
    }

    override fun onSessionError() {
        extracting = false
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
}