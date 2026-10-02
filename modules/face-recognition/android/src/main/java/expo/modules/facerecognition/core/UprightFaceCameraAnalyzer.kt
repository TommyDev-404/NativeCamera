package expo.modules.facerecognition.core

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.insightface.sdk.inspireface.InspireFace
import com.insightface.sdk.inspireface.base.ImageStream
import com.insightface.sdk.inspireface.base.MultipleFaceData
import com.insightface.sdk.inspireface.base.Session
import expo.modules.facerecognition.core.FaceEngine

abstract class UprightFaceCameraAnalyzer : ImageAnalysis.Analyzer {

    private val converter = Nv21Converter()

    private var session: Session? = null
    private var sessionFailed = false
    @Volatile
    private var released = false

    override fun analyze(image: ImageProxy) {
        if (released || sessionFailed || shouldSkipFrame()) {
            image.close()
            return
        }

        beforeFrame()

        if (session == null) {
            session = createSession()

            if (session == null) {
                sessionFailed = true
                image.close()
                onSessionError()
                return
            }

            onSessionReady()
        }

        val frameStart = SystemClock.elapsedRealtime()
        val width = image.width
        val height = image.height
        val rotationDegrees = image.imageInfo.rotationDegrees

        val nv21: ByteArray

        try {
            nv21 = converter.convert(image)
        } finally {
            image.close()
        }

        val upright = converter.rotateUpright(
            nv21,
            width,
            height,
            rotationDegrees
        )

        val swapped = rotationDegrees == 90 || rotationDegrees == 270

        val uprightWidth = if (swapped) height else width
        val uprightHeight = if (swapped) width else height

        val stream = InspireFace.CreateImageStreamFromByteBuffer(
            upright,
            uprightWidth,
            uprightHeight,
            InspireFace.STREAM_YUV_NV21,
            InspireFace.CAMERA_ROTATION_0
        )

        if (stream == null) {
            return
        }

        try {
            val faces = InspireFace.ExecuteFaceTrack(
                session,
                stream
            )

            onFaces(
                session,
                stream,
                faces,
                upright,
                uprightWidth,
                uprightHeight,
                frameStart
            )
        } finally {
            InspireFace.ReleaseImageStream(stream)
        }
    }

    protected open fun beforeFrame() {
    }

    protected open fun shouldSkipFrame(): Boolean {
        return false
    }

    protected abstract fun createSession(): Session?

    protected open fun onSessionReady() {
    }

    protected abstract fun onFaces(
        session: Session?,
        stream: ImageStream,
        faces: MultipleFaceData?,
        uprightNv21: ByteArray,
        uprightWidth: Int,
        uprightHeight: Int,
        frameStart: Long
    )

    protected abstract fun onSessionError()

    fun release() {
        released = true

        session?.let {
            FaceEngine.releaseSession(it)
            session = null
        }
    }
}