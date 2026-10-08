package expo.modules.faceregistration.core

import android.content.Context
import android.util.Log
import com.insightface.sdk.inspireface.InspireFace
import com.insightface.sdk.inspireface.base.Session


object FaceEngine {

    private const val TAG = "FaceEngine"
    private const val MAX_FACES = 3
    private const val TRACK_PREVIEW_SIZE = 320
    private const val DETECT_LEVEL = 320

    @Volatile
    private var launched = false

    @Synchronized
    fun ensureLaunched(context: Context): Boolean {
        if (launched) {
            return true
        }

        launched = InspireFace.GlobalLaunch(
            context.applicationContext,
            InspireFace.PIKACHU
        )

        return launched
    }

    @Synchronized
    fun createTrackingSession(): Session? {
        if (!launched) {
            return null
        }

        val parameter = InspireFace.CreateCustomParameter()
            .enableRecognition(true)

        val session = InspireFace.CreateSession(
            parameter,
            InspireFace.DETECT_MODE_LIGHT_TRACK,
            MAX_FACES,
            DETECT_LEVEL,
            -1
        ) ?: return null

        InspireFace.SetTrackPreviewSize(
            session,
            TRACK_PREVIEW_SIZE
        )

        InspireFace.SetFaceDetectThreshold(
            session,
            0.5f
        )

        InspireFace.SetFilterMinimumFacePixelSize(
            session,
            0
        )

        return session
    }

    @Synchronized
    fun releaseSession(session: Session?) {
        if (session == null) {
            return
        }

        InspireFace.ReleaseSession(session)
    }
}