package expo.modules.facerecognition

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import expo.modules.facerecognition.camera.CameraPreviewController
import expo.modules.facerecognition.core.FaceEngine
import expo.modules.facerecognition.core.FaceModelPrefs
import expo.modules.facerecognition.core.FaceOverlayView
import expo.modules.facerecognition.core.FaceRecord
import expo.modules.facerecognition.core.FaceRepository
import expo.modules.facerecognition.R
import expo.modules.facerecognition.core.RecognitionFaceAnalyzer
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.content.ContextCompat

class FaceRecognitionView(
    context: Context,
    appContext: AppContext
) : ExpoView(context, appContext), CameraPreviewController.Listener {

    companion object {
        private const val TAG = "FaceRecognitionView"
    }

    private val activity: LifecycleOwner
        get() = requireNotNull(
            appContext.activityProvider?.currentActivity as? LifecycleOwner
        )

    private val frameLayout = FrameLayout(context)
    private val viewFinder = PreviewView(context)
    private val faceOverlayView = FaceOverlayView(context)
    private val resultContainer = FrameLayout(context)
    private val resultTitle = TextView(context)
    private val resultSubtitle = TextView(context)

    private var cameraPreviewController: CameraPreviewController? = null
    private var recognitionAnalyzer: RecognitionFaceAnalyzer? = null
    private var analysisExecutor: ExecutorService? = null

    private var model: FaceModelPrefs.Model? = null
    private var repository: FaceRepository? = null

    private var recognitionInitialized = false
    private var libraryEmpty = false

    private var videoStarting = false
    private var videoGeneration = 0

    @Volatile
    private var destroyed = false

    init {
        setupViews()
        initializeRecognition()
    }

    private fun setupViews() {
        frameLayout.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    
        addView(frameLayout)
    
        viewFinder.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    
        viewFinder.isFocusableInTouchMode = true
        viewFinder.requestFocusFromTouch()
        installHierarchyFitter(viewFinder)
        frameLayout.addView(viewFinder)
    
        faceOverlayView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    
        frameLayout.addView(faceOverlayView)
    
        resultContainer.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(24)
            rightMargin = dp(24)
            bottomMargin = dp(24)
        }
    
        resultContainer.setPadding(
            dp(20),
            dp(18),
            dp(20),
            dp(14)
        )
    
        resultContainer.setBackgroundResource(R.drawable.bg_chip)
        frameLayout.addView(resultContainer)
    
        resultTitle.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
    
        resultTitle.gravity = Gravity.CENTER
        resultTitle.setTextColor(Color.WHITE)
        resultTitle.textSize = 20f
        resultTitle.setTypeface(null, Typeface.BOLD)
    
        resultContainer.addView(resultTitle)
    
        resultSubtitle.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(28)
        }
    
        resultSubtitle.gravity = Gravity.CENTER
        resultSubtitle.setTextColor(Color.rgb(190, 190, 190))
        resultSubtitle.textSize = 14f
    
        resultContainer.addView(resultSubtitle)
        
        resultTitle.setText(R.string.capture_move_closer)
        resultSubtitle.setText(R.string.recognition_video_first_face_hint)
        resultContainer.visibility = View.GONE
    }

    private fun initializeRecognition() {
        var ready = false

        try {
            model = FaceModelPrefs.get(context)

            ready = FaceEngine.ensureLaunched(context)

            if (ready) {
                repository = FaceRepository(
                    context.applicationContext,
                    requireNotNull(model)
                )

                ready = repository?.open() == true
            }

            if (!ready) {
                repository?.close()
                repository = null
            }

            libraryEmpty = ready && repository?.query(null).isNullOrEmpty()

            recognitionInitialized = ready

            if (ready) {
                Log.e(
                    TAG,
                    "Recognition initialized successfully"
                )

                Log.e(
                    TAG,
                    "Model: ${model?.sdkName}"

                )

                Log.e(
                    TAG,
                    "Recognition library empty: $libraryEmpty"
                )
            } else {
                Log.e(
                    TAG,
                    "Recognition initialization failed"
                )
            }
        } catch (e: Exception) {
            repository?.close()
            repository = null

            recognitionInitialized = false
            libraryEmpty = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        destroyed = false

        if (!recognitionInitialized) {
            resultTitle.text = "Recognition unavailable"
            resultSubtitle.text = ""
            resultContainer.visibility = View.VISIBLE

            return
        }

        startVideoRecognition()
    }

    private fun startVideoRecognition() {
        if (
            destroyed ||
            cameraPreviewController != null ||
            videoStarting
        ) {
            return
        }

        if (!recognitionInitialized) {
            showRecognitionError()

            return
        }

        val repository = repository ?: run {
            showRecognitionError()

            return
        }

        videoStarting = true
        val generation = ++videoGeneration

        resultTitle.text = "Initializing camera..."
        resultSubtitle.text = ""
        resultContainer.visibility = View.VISIBLE

        analysisExecutor = Executors.newSingleThreadExecutor()

        val analyzer = RecognitionFaceAnalyzer(
            context = context,
            overlay = faceOverlayView,
            repository = repository,
            libraryEmpty = libraryEmpty,
            listener = object : RecognitionFaceAnalyzer.Listener {

                override fun onState(
                    state: RecognitionFaceAnalyzer.State,
                    record: FaceRecord?,
                    confidence: Float,
                    threshold: Float
                ) {
                    postVideoUi(generation) {
                        renderVideoState(
                            state,
                            record,
                            confidence,
                            threshold
                        )
                    }
                }

                override fun onSessionError() {
                    postVideoUi(generation) {
                        videoStarting = false
                        resultTitle.text = "Recognition error"
                        resultSubtitle.text = ""
                        resultContainer.visibility = View.VISIBLE
                    }
                }
            }
        )

        recognitionAnalyzer = analyzer

        cameraPreviewController = CameraPreviewController(
            context = context,
            lifecycleOwner = activity,
            previewView = viewFinder,
            analysisExecutor = requireNotNull(analysisExecutor),
            analyzer = analyzer,
            listener = this
        )

        cameraPreviewController?.start()
    }

    private fun renderVideoState(
        state: RecognitionFaceAnalyzer.State,
        record: FaceRecord?,
        confidence: Float,
        threshold: Float
    ) {
        if (!isCurrentVideo(videoGeneration)) {
            return
        }

        when (state) {
            RecognitionFaceAnalyzer.State.MOVE_CLOSER -> {
                resultTitle.setText(R.string.capture_move_closer)
                resultSubtitle.setText(R.string.recognition_video_first_face_hint)

                resultTitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.liveness_warn
                    )
                )
            }

            RecognitionFaceAnalyzer.State.HOLD_STILL -> {
                resultTitle.setText(R.string.recognition_video_hold_still)
                resultSubtitle.setText(R.string.recognition_video_first_face_hint)

                resultTitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.white
                    )
                )
            }

            RecognitionFaceAnalyzer.State.SEARCHING -> {
                resultTitle.setText(R.string.recognition_searching)
                resultSubtitle.setText(R.string.recognition_video_first_face_hint)

                resultTitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.liveness_warn
                    )
                )
            }

            RecognitionFaceAnalyzer.State.MATCHED -> {
                resultTitle.setText(R.string.recognition_match_found)

                resultTitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.liveness_accent
                    )
                )

                if (record != null) {
                    resultSubtitle.text = buildString {
                        append(record.name)

                        if (!confidence.isNaN()) {
                            append("\n")
                            append(
                                context.getString(
                                    R.string.recognition_result_details,
                                    record.id,
                                    confidence,
                                    threshold
                                )
                            )
                        }
                    }
                } else {
                    resultSubtitle.text = buildString {
                        append(
                            context.getString(
                                R.string.recognition_result_name_unknown
                            )
                        )
                        append("\n")
                        append(
                            context.getString(
                                R.string.recognition_no_confidence
                            )
                        )
                    }
                }
            }

            RecognitionFaceAnalyzer.State.NO_MATCH -> {
                resultTitle.setText(R.string.recognition_no_match)

                resultTitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.liveness_fail
                    )
                )

                resultSubtitle.text = buildString {
                    append(
                        context.getString(
                            R.string.recognition_result_name_unknown
                        )
                    )
                    append("\n")

                    if (confidence.isNaN()) {
                        append(
                            context.getString(
                                R.string.recognition_no_confidence
                            )
                        )
                    } else {
                        append(
                            context.getString(
                                R.string.recognition_no_match_details,
                                confidence,
                                threshold
                            )
                        )
                    }
                }
            }

            RecognitionFaceAnalyzer.State.EMPTY_LIBRARY -> {
                resultTitle.setText(
                    R.string.recognition_library_empty_result
                )

                resultTitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.liveness_warn
                    )
                )

                model?.let {
                    resultSubtitle.text = context.getString(
                        R.string.recognition_empty_library,
                        it.sdkName
                    )
                } ?: run {
                    resultSubtitle.text = ""
                }
            }

            RecognitionFaceAnalyzer.State.NO_FACE -> {
                resultTitle.setText(
                    R.string.recognition_video_no_face
                )

                resultTitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.white
                    )
                )

                resultSubtitle.setText(
                    R.string.recognition_video_first_face_hint
                )
            }
        }

        resultContainer.visibility = View.VISIBLE
    }

    private fun showRecognitionError() {
        resultTitle.text = "Recognition unavailable"
        resultSubtitle.text = ""
        resultContainer.visibility = View.VISIBLE
    }

    override fun onCameraReady(frontCamera: Boolean) {
        if (!isCurrentVideo(videoGeneration)) {
            return
        }

        videoStarting = false

        recognitionAnalyzer?.setMirrored(frontCamera)

        faceOverlayView.submit(null)

        resultTitle.text = ""
        resultSubtitle.text = ""
        resultContainer.visibility = View.GONE
    }

    override fun onCameraError(messageRes: Int) {
        if (!isCurrentVideo(videoGeneration)) {
            return
        }

        videoStarting = false

        resultTitle.text = "Camera unavailable"
        resultSubtitle.text = ""
        resultContainer.visibility = View.VISIBLE
    }

    private fun isCurrentVideo(generation: Int): Boolean {
        return !destroyed &&
            generation == videoGeneration &&
            recognitionAnalyzer != null
    }

    private fun postVideoUi(generation: Int, action: () -> Unit) {
        post {
            if (isCurrentVideo(generation)) {
                action()
            }
        }
    }

    private fun stopVideoRecognition() {
        videoGeneration++
        videoStarting = false

        cameraPreviewController?.stop()
        cameraPreviewController = null

        val analyzer = recognitionAnalyzer
        recognitionAnalyzer = null

        analyzer?.let {
            analysisExecutor?.execute {
                it.release()
            }
        }

        analysisExecutor?.shutdown()
        analysisExecutor = null

        faceOverlayView.submit(null)
        resultTitle.text = ""
        resultSubtitle.text = ""
        resultContainer.visibility = View.GONE
    }
    
    override fun onDetachedFromWindow() {
        stopVideoRecognition()

        destroyed = true

        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun installHierarchyFitter(view: ViewGroup) {
        view.setOnHierarchyChangeListener(
            object : ViewGroup.OnHierarchyChangeListener {

                override fun onChildViewRemoved(
                    parent: View?,
                    child: View?
                ) = Unit

                override fun onChildViewAdded(
                    parent: View?,
                    child: View?
                ) {
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