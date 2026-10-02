package expo.modules.facerecognition

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import expo.modules.facerecognition.R
import expo.modules.facerecognition.camera.CameraPreviewController
import expo.modules.facerecognition.core.FaceEngine
import expo.modules.facerecognition.core.FaceOverlayView
import expo.modules.facerecognition.core.RegistrationFaceAnalyzer
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.view.View
import android.view.ViewGroup


class FaceRegistrationView(
    context: Context,
    appContext: AppContext
) : ExpoView(context, appContext), RegistrationFaceAnalyzer.Listener {

    companion object {
        private const val TAG = "FaceRegistrationView"
        private const val COMPLETE_DISPLAY_MS = 350L
    }

    private val analysisExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val container = FrameLayout(context)
    private val previewView = PreviewView(context)
    private val overlayView = FaceOverlayView(context)
    private val promptContainer = FrameLayout(context)
    private val promptTitle = TextView(context)
    private val promptSubtitle = TextView(context)

    private var cameraController: CameraPreviewController? = null
    private var analyzer: RegistrationFaceAnalyzer? = null

    private var engineStartingOrReady = false
    private var resultScheduled = false
    private var pendingCapturePath: String? = null

    private val lifecycleOwner: LifecycleOwner
        get() = requireNotNull(
            appContext.activityProvider?.currentActivity as? LifecycleOwner
        )

    init {
        setupViews()
    }

    private fun setupViews() {
        container.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )

        addView(container)

        previewView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        installHierarchyFitter(previewView)
         
        container.addView(previewView)

        overlayView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        container.addView(overlayView)

        setupPrompt()
    }

    private fun setupPrompt() {
        promptContainer.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(24)
            rightMargin = dp(24)
            bottomMargin = dp(24)
        }
    
        promptContainer.setPadding(
            dp(20),
            dp(12),
            dp(20),
            dp(14)
        )
    
        promptContainer.setBackgroundResource(
            R.drawable.bg_chip
        )
    
        container.addView(promptContainer)
    
        promptTitle.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
    
        promptTitle.gravity = Gravity.CENTER
        promptTitle.setTextColor(Color.WHITE)
        promptTitle.textSize = 20f
        promptTitle.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )
    
        promptContainer.addView(promptTitle)
    
        promptSubtitle.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(24)
        }
    
        promptSubtitle.gravity = Gravity.CENTER
        resultSubtitle.setTextColor(Color.rgb(190, 190, 190))
        promptSubtitle.textSize = 14f
    
        promptContainer.addView(promptSubtitle)
    
        promptTitle.setText(R.string.msg_initializing)
        promptSubtitle.setText(R.string.capture_first_face_hint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        startEngine()
    }

    private fun startEngine() {
        if (engineStartingOrReady) {
            return
        }

        engineStartingOrReady = true

        promptTitle.setText(
            R.string.msg_initializing
        )

        promptSubtitle.setText(
            R.string.capture_first_face_hint
        )

        analysisExecutor.execute {
            val ready = FaceEngine.ensureLaunched(
                context
            )
            
            post {
                if (!isAttachedToWindow) {
                    return@post
                }

                if (ready) {
                    bindCamera()
                } else {
                    engineStartingOrReady = false

                    promptTitle.setText(
                        R.string.msg_engine_failed
                    )
                }
            }
        }
    }

    private fun bindCamera() {
        val captureDirectory = File(
            context.cacheDir,
            "face_capture"
        )

        if (!captureDirectory.exists()) {
            captureDirectory.mkdirs()
        }

        analyzer = RegistrationFaceAnalyzer(
            context,
            overlayView,
            captureDirectory,
            this
        )

        cameraController = CameraPreviewController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            analysisExecutor = analysisExecutor,
            analyzer = analyzer!!,
            listener = object : CameraPreviewController.Listener {

                override fun onCameraReady(
                    frontCamera: Boolean
                ) {
                    analyzer?.setMirrored(
                        frontCamera
                    )

                    post {
                        showState(
                            RegistrationFaceAnalyzer.Stage.NO_FACE,
                            0f
                        )
                    }
                }

                override fun onCameraError(
                    messageRes: Int
                ) {
                    post {
                        promptTitle.setText(
                            messageRes
                        )
                    }
                }
            }
        )

        cameraController?.start()
    }

    override fun onState(stage: RegistrationFaceAnalyzer.Stage, progress: Float) {
        post {
            showState(
                stage,
                progress
            )
        }
    }

    private fun showState(stage: RegistrationFaceAnalyzer.Stage, progress: Float) {
        if (!isAttachedToWindow) {
            return
        }

        when (stage) {
            RegistrationFaceAnalyzer.Stage.MOVE_CLOSER -> {
                promptTitle.setText(
                    R.string.capture_move_closer
                )

                promptSubtitle.setText(
                    R.string.capture_first_face_hint
                )
            }

            RegistrationFaceAnalyzer.Stage.HOLD_STILL -> {
                promptTitle.setText(
                    R.string.capture_hold_still
                )

                promptSubtitle.setText(
                    R.string.capture_warmup_hint
                )
            }

            RegistrationFaceAnalyzer.Stage.CAPTURING -> {
                promptTitle.setText(
                    R.string.capture_keep_still
                )

                promptSubtitle.text = context.getString(
                    R.string.capture_progress,
                    Math.round(progress * 100f)
                )
            }

            RegistrationFaceAnalyzer.Stage.COMPLETE -> {
                promptTitle.setText(
                    R.string.capture_complete
                )

                promptSubtitle.setText(
                    R.string.capture_complete_hint
                )
            }

            RegistrationFaceAnalyzer.Stage.NO_FACE -> {
                promptTitle.setText(
                    R.string.capture_no_face
                )

                promptSubtitle.setText(
                    R.string.capture_first_face_hint
                )
            }
        }
    }

    override fun onCaptured(path: String) {
        post {
            if (!isAttachedToWindow) {
                File(path).delete()
                return@post
            }

            resultScheduled = true
            pendingCapturePath = path

            overlayView.clearFace()

            showState(
                RegistrationFaceAnalyzer.Stage.COMPLETE,
                1f
            )

            mainHandler.postDelayed(
                {
                    if (!isAttachedToWindow) {
                        File(path).delete()
                        return@postDelayed
                    }

                    onRegistrationCompleted(path)
                },
                COMPLETE_DISPLAY_MS
            )
        }
    }

    override fun onCaptureError() {
        post {
            promptTitle.setText(
                R.string.capture_failed
            )

            promptSubtitle.setText(
                R.string.capture_retry_hint
            )
        }
    }

    override fun onSessionError() {
        post {
            promptTitle.setText(
                R.string.msg_engine_failed
            )
        }
    }

    private fun onRegistrationCompleted(path: String) {
        Log.e(
            TAG,
            "Registration capture completed: $path"
        )

        /*
         * This replaces:
         *
         * setResult(
         *     RESULT_OK,
         *     Intent().putExtra(
         *         EXTRA_CAPTURE_PATH,
         *         path
         *     )
         * )
         *
         * The Expo view should eventually emit
         * the path to React Native here.
         */
    }

    override fun onDetachedFromWindow() {
        mainHandler.removeCallbacksAndMessages(
            null
        )

        if (!resultScheduled && pendingCapturePath != null) {
            File(
                pendingCapturePath!!
            ).delete()
        }

        cameraController?.stop()
        cameraController = null

        analyzer?.let { toRelease ->
            analysisExecutor.execute {
                toRelease.release()
            }
        }

        analyzer = null

        engineStartingOrReady = false

        analysisExecutor.shutdown()

        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }
    
    private fun installHierarchyFitter(view: ViewGroup) {
        view.setOnHierarchyChangeListener(
            object : OnHierarchyChangeListener {
                override fun onChildViewRemoved(parent: View?, child: View?) = Unit

                override fun onChildViewAdded(parent: View?, child: View?) {
                    parent?.measure(
                        MeasureSpec.makeMeasureSpec(
                            measuredWidth,
                            MeasureSpec.EXACTLY
                        ),
                        MeasureSpec.makeMeasureSpec(
                            measuredHeight,
                            MeasureSpec.EXACTLY
                        )
                    )

                    parent?.layout(
                        0,
                        0,
                        parent.measuredWidth,
                        parent.measuredHeight
                    )
                }
            }
        )
    }
}