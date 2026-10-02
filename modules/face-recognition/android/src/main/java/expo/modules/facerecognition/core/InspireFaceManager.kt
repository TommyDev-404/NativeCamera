package expo.modules.facerecognition.core

import android.content.Context
import android.util.Log
import com.insightface.sdk.inspireface.InspireFace
import com.insightface.sdk.inspireface.base.CustomParameter
import com.insightface.sdk.inspireface.base.Session

object InspireFaceManager {
    private const val TAG = "InspireFaceManager"

    private const val TRACK_PREVIEW_SIZE = 320
    private const val DETECT_LEVEL = 320

    private var initialized = false

    private var registrationSession: Session? = null
    private var recognitionSession: Session? = null

    fun launch(context: Context): Boolean {
        if (initialized) {
            return true
        }

        val result = InspireFace.GlobalLaunch(
            context.applicationContext,
            InspireFace.PIKACHU
        )

        Log.e(TAG, "GlobalLaunch result -> $result")

        initialized = result

        return result
    }

    fun createRegistrationSession(): Boolean {
        check(initialized) {
            "InspireFace has not been launched"
        }

        if (registrationSession != null) {
            return true
        }

        registrationSession = createTrackingSession(
            parameter = InspireFace.CreateCustomParameter()
                .enableFaceQuality(true)
                .enableInteractionLiveness(true)
                .enableLiveness(true),
            maxFaces = 1,
            minimumFacePixelSize = 24
        )

        if (registrationSession == null) {
            throw IllegalStateException(
                "Failed to create registration session"
            )
        }

        Log.e(TAG, "Registration tracking session created")
        
        return true
    }

    fun createRecognitionSession(): Boolean {
        check(initialized) {
            "InspireFace has not been launched"
        }

        if (recognitionSession != null) {
            return true
        }

        recognitionSession = createTrackingSession(
            parameter = InspireFace.CreateCustomParameter()
                .enableRecognition(true)
                .enableFaceQuality(true)
                .enableInteractionLiveness(true)
                .enableLiveness(true),
            maxFaces = 1,
            minimumFacePixelSize = 24
        )

        if (recognitionSession == null) {
            throw IllegalStateException(
                "Failed to create recognition session"
            )
        }

        Log.e(
            TAG,
            "Recognition tracking session created"
        )

        return true
    }

    fun getRegistrationSession(): Session {
        return registrationSession
            ?: throw IllegalStateException(
                "Registration session has not been created"
            )
    }

    fun getRecognitionSession(): Session {
        return recognitionSession
            ?: throw IllegalStateException(
                "Recognition session has not been created"
            )
    }

    fun destroyRegistrationSession() {
        registrationSession?.let { session ->
            InspireFace.ReleaseSession(session)
        }

        registrationSession = null
    }

    fun destroyRecognitionSession() {
        recognitionSession?.let { session ->
            InspireFace.ReleaseSession(session)
        }

        recognitionSession = null
    }

    private fun createTrackingSession(
        parameter: CustomParameter,
        maxFaces: Int,
        minimumFacePixelSize: Int
    ): Session? {
        val safeMaxFaces = maxOf(1, maxFaces)
        val safeMinimumFacePixelSize = maxOf(0, minimumFacePixelSize)

        val session = InspireFace.CreateSession(
            parameter,
            InspireFace.DETECT_MODE_LIGHT_TRACK,
            safeMaxFaces,
            DETECT_LEVEL,
            -1
        ) ?: return null

        InspireFace.SetFaceDetectThreshold(session, 0.5f)
        InspireFace.SetFilterMinimumFacePixelSize(session, safeMinimumFacePixelSize)
        InspireFace.SetTrackPreviewSize(session, TRACK_PREVIEW_SIZE)

        InspireFace.SetTrackModeDetectInterval(session, 20)
        InspireFace.SetTrackModeSmoothRatio(session, 0.05f)
        InspireFace.SetTrackModeNumSmoothCacheFrame(session, 20)

        return session
    }
}