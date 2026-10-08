package expo.modules.facerecognition

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import expo.modules.facerecognition.camera.CameraPreviewController
import expo.modules.facerecognition.core.FaceEngine
import expo.modules.facerecognition.core.FaceOverlayView
import expo.modules.facerecognition.core.FaceRepository
import expo.modules.facerecognition.core.RecognitionFaceAnalyzer
import expo.modules.facerecognition.database.ScannerDatabase
import expo.modules.facerecognition.database.StudentRepository
import expo.modules.facerecognition.database.models.Student
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceRecognitionView(
    context: Context,
    appContext: AppContext
) : ExpoView(context, appContext), CameraPreviewController.Listener {

    val onStudentRecognized by EventDispatcher()
    
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

    private val resultContainer = LinearLayout(context)
    private val resultTitle = TextView(context)
    private val resultSubtitle = TextView(context)

    private val scannerDatabase = ScannerDatabase(context)
    private val studentRepository = StudentRepository(scannerDatabase)

    private var cameraPreviewController: CameraPreviewController? = null
    private var recognitionAnalyzer: RecognitionFaceAnalyzer? = null
    private var analysisExecutor: ExecutorService? = null
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

        resultContainer.orientation = LinearLayout.VERTICAL

        resultContainer.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            leftMargin = dp(16)
            rightMargin = dp(16)
            topMargin = dp(24)
        }

        resultContainer.setPadding(
            dp(20),
            dp(18),
            dp(20),
            dp(20)
        )

        resultContainer.setBackgroundResource(R.drawable.bg_chip)

        frameLayout.addView(resultContainer)

        resultTitle.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        resultTitle.gravity = Gravity.CENTER
        resultTitle.setTextColor(Color.WHITE)
        resultTitle.textSize = 18f
        resultTitle.setTypeface(null, Typeface.BOLD)

        resultContainer.addView(resultTitle)

        resultSubtitle.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(8)
        }

        resultSubtitle.gravity = Gravity.CENTER
        resultSubtitle.setTextColor(Color.rgb(190, 190, 190))
        resultSubtitle.textSize = 14f

        resultContainer.addView(resultSubtitle)

        resultTitle.setText(R.string.capture_move_closer)
        resultSubtitle.text = "Move closer to the camera"

        resultContainer.visibility = View.GONE
    }

    private fun setResultMessage(title: Int, subtitle: String, titleColor: Int) {
        resultTitle.setText(title)

        resultTitle.setTextColor(
            ContextCompat.getColor(
                context,
                titleColor
            )
        )

        resultSubtitle.text = subtitle
        resultSubtitle.visibility = View.VISIBLE

        resultContainer.visibility = View.VISIBLE
    }

    private fun initializeRecognition() {
        var ready = false

        try {
            ready = FaceEngine.ensureLaunched(context)

            if (ready) {
                repository = FaceRepository(
                    context.applicationContext
                )

                ready = repository?.open() == true
            }

            if (!ready) {
                repository?.close()
                repository = null
            }

            libraryEmpty = false
            recognitionInitialized = ready

            if (ready) {
                Log.e(
                    TAG,
                    "Recognition initialized successfully"
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
            Log.e(
                TAG,
                "Recognition initialization failed",
                e
            )

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
            resultSubtitle.text =
                "Face recognition could not be initialized"

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
        resultTitle.setTextColor(Color.WHITE)

        resultSubtitle.text = "Starting face recognition"

        resultContainer.visibility = View.VISIBLE

        analysisExecutor = Executors.newSingleThreadExecutor()

        val analyzer = RecognitionFaceAnalyzer(
            context = context,
            overlay = faceOverlayView,
            repository = repository,
            studentRepository = studentRepository,
            libraryEmpty = libraryEmpty,
            listener = object : RecognitionFaceAnalyzer.Listener {

                override fun onState(
                    state: RecognitionFaceAnalyzer.State,
                    student: Student?,
                    confidence: Float,
                    threshold: Float
                ) {
                    postVideoUi(generation) {
                        renderVideoState(
                            state,
                            student,
                            confidence,
                            threshold
                        )
                    }
                }

                override fun onSessionError() {
                    postVideoUi(generation) {
                        videoStarting = false

                        resultTitle.text = "Recognition error"
                        resultTitle.setTextColor(Color.WHITE)

                        resultSubtitle.text =
                            "Face recognition session failed"

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
        student: Student?,
        confidence: Float,
        threshold: Float
    ) {
        if (!isCurrentVideo(videoGeneration)) {
            return
        }

        when (state) {

            RecognitionFaceAnalyzer.State.MOVE_CLOSER -> {
                setResultMessage(
                    R.string.capture_move_closer,
                    "Move closer to the camera",
                    R.color.liveness_warn
                )
            }

            RecognitionFaceAnalyzer.State.HOLD_STILL -> {
                setResultMessage(
                    R.string.recognition_video_hold_still,
                    "Keep your face steady",
                    R.color.white
                )
            }

            RecognitionFaceAnalyzer.State.SEARCHING -> {
                setResultMessage(
                    R.string.recognition_searching,
                    "Checking your identity",
                    R.color.liveness_warn
                )
            }

            RecognitionFaceAnalyzer.State.MATCHED -> {
                if (student != null) {
                    onStudentRecognized(
                        mapOf(
                            "studentId" to student.studentId
                        )
                    )
                }
                
                resultTitle.setText(
                    R.string.recognition_match_found
                )

                resultTitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.liveness_accent
                    )
                )

                resultSubtitle.setTextColor(
                    Color.rgb(190, 190, 190)
                )

                resultSubtitle.text = if (student != null) {
                    "${student.fullName} · " +
                        "${formatYear(student.year)} · " +
                        "${student.course} · " +
                        "${student.section ?: "Section N/A"}"
                } else {
                    context.getString(
                        R.string.recognition_result_name_unknown
                    )
                }

                resultSubtitle.visibility = View.VISIBLE
                resultContainer.visibility = View.VISIBLE
            }

            RecognitionFaceAnalyzer.State.NO_MATCH -> {
                setResultMessage(
                    R.string.recognition_no_match,
                    "No enrolled student matched",
                    R.color.liveness_fail
                )
            }

            RecognitionFaceAnalyzer.State.EMPTY_LIBRARY -> {
                setResultMessage(
                    R.string.recognition_library_empty_result,
                    "No faces are currently enrolled",
                    R.color.liveness_warn
                )
            }

            RecognitionFaceAnalyzer.State.NO_FACE -> {
                setResultMessage(
                    R.string.recognition_video_no_face,
                    "Position your face inside the frame",
                    R.color.white
                )
            }
        }
    }

    private fun formatYear(year: Int?): String {
        return when (year) {
            null -> "Year N/A"
            1 -> "1st Year"
            2 -> "2nd Year"
            3 -> "3rd Year"
            else -> "${year}th Year"
        }
    }

    private fun showRecognitionError() {
        resultTitle.text = "Recognition unavailable"
        resultTitle.setTextColor(Color.WHITE)

        resultSubtitle.text = "Face recognition could not be initialized"

        resultContainer.visibility = View.VISIBLE
    }

    override fun onCameraReady(frontCamera: Boolean) {
        if (!isCurrentVideo(videoGeneration)) {
            return
        }

        videoStarting = false

        recognitionAnalyzer?.setMirrored(frontCamera)

        faceOverlayView.submit(null)

        resultContainer.visibility = View.GONE
    }

    override fun onCameraError(messageRes: Int) {
        if (!isCurrentVideo(videoGeneration)) {
            return
        }

        videoStarting = false

        resultTitle.text = "Camera unavailable"
        resultTitle.setTextColor(Color.WHITE)

        resultSubtitle.text =
            "Unable to start the camera"

        resultContainer.visibility = View.VISIBLE
    }

    private fun isCurrentVideo(generation: Int): Boolean {
        return !destroyed &&
            generation == videoGeneration &&
            recognitionAnalyzer != null
    }

    private fun postVideoUi( generation: Int, action: () -> Unit) {
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

        resultContainer.visibility = View.GONE
    }

    override fun onDetachedFromWindow() {
        stopVideoRecognition()
        destroyed = true

        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int {
        return (
            value *
                resources.displayMetrics.density
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
                    view.post {
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                view.width,
                                View.MeasureSpec.EXACTLY
                            ),
                            View.MeasureSpec.makeMeasureSpec(
                                view.height,
                                View.MeasureSpec.EXACTLY
                            )
                        )

                        view.layout(
                            0,
                            0,
                            view.measuredWidth,
                            view.measuredHeight
                        )
                    }
                }
            }
        )
    }
}