package expo.modules.faceregistration.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import expo.modules.faceregistration.R

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    class Frame(
        val imageWidth: Int,
        val imageHeight: Int,
        val mirrored: Boolean,
        val rects: Array<RectF>,
        val color: Int,
        val progress: Float = -1f,
        val progressColor: Int = color
    )

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val mapped = RectF()
    private val progressBounds = RectF()

    @Volatile
    private var frame: Frame? = null

    init {
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = dp(3f)
        boxPaint.strokeCap = Paint.Cap.ROUND
        boxPaint.color = ContextCompat.getColor(
            context,
            R.color.liveness_accent
        )

        progressTrackPaint.style = Paint.Style.STROKE
        progressTrackPaint.strokeWidth = dp(7f)
        progressTrackPaint.color = 0x66000000

        progressPaint.style = Paint.Style.STROKE
        progressPaint.strokeWidth = dp(7f)
        progressPaint.strokeCap = Paint.Cap.ROUND
    }

    fun submit(f: Frame?) {
        frame = f
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val f = frame

        if (
            f == null ||
            f.rects.isEmpty() ||
            f.imageWidth <= 0 ||
            f.imageHeight <= 0
        ) {
            return
        }

        val vw = width.toFloat()
        val vh = height.toFloat()

        val scale = maxOf(
            vw / f.imageWidth,
            vh / f.imageHeight
        )

        val dx = (vw - f.imageWidth * scale) / 2f
        val dy = (vh - f.imageHeight * scale) / 2f

        boxPaint.color = f.color

        for (i in f.rects.indices) {
            val r = f.rects[i]

            var left = r.left
            var right = r.right

            if (f.mirrored) {
                val l = f.imageWidth - right
                right = f.imageWidth - left
                left = l
            }

            mapped.set(
                left * scale + dx,
                r.top * scale + dy,
                right * scale + dx,
                r.bottom * scale + dy
            )

            drawBrackets(canvas, mapped)

            if (i == 0 && f.progress >= 0f) {
                drawProgressRing(
                    canvas,
                    mapped,
                    f.progress,
                    f.progressColor
                )
            }
        }
    }

    private fun drawProgressRing(canvas: Canvas, face: RectF, progress: Float, color: Int) {
        val size = maxOf(
            face.width(),
            face.height()
        ) + dp(28f)

        val cx = face.centerX()
        val cy = face.centerY()

        progressBounds.set(
            cx - size / 2f,
            cy - size / 2f,
            cx + size / 2f,
            cy + size / 2f
        )

        canvas.drawOval(
            progressBounds,
            progressTrackPaint
        )

        progressPaint.color = color

        val clamped = progress.coerceIn(0f, 1f)

        canvas.drawArc(
            progressBounds,
            -90f,
            maxOf(1f, clamped * 360f),
            false,
            progressPaint
        )
    }

    private fun drawBrackets(canvas: Canvas, r: RectF) {
        val len = minOf(
            r.width(),
            r.height()
        ) * 0.22f

        val radius = dp(6f)

        canvas.drawLine(
            r.left,
            r.top + len,
            r.left,
            r.top + radius,
            boxPaint
        )

        canvas.drawLine(
            r.left + radius,
            r.top,
            r.left + len,
            r.top,
            boxPaint
        )

        canvas.drawArc(
            r.left,
            r.top,
            r.left + 2f * radius,
            r.top + 2f * radius,
            180f,
            90f,
            false,
            boxPaint
        )

        canvas.drawLine(
            r.right - len,
            r.top,
            r.right - radius,
            r.top,
            boxPaint
        )

        canvas.drawLine(
            r.right,
            r.top + radius,
            r.right,
            r.top + len,
            boxPaint
        )

        canvas.drawArc(
            r.right - 2f * radius,
            r.top,
            r.right,
            r.top + 2f * radius,
            270f,
            90f,
            false,
            boxPaint
        )

        canvas.drawLine(
            r.left,
            r.bottom - len,
            r.left,
            r.bottom - radius,
            boxPaint
        )

        canvas.drawLine(
            r.left + radius,
            r.bottom,
            r.left + len,
            r.bottom,
            boxPaint
        )

        canvas.drawArc(
            r.left,
            r.bottom - 2f * radius,
            r.left + 2f * radius,
            r.bottom,
            90f,
            90f,
            false,
            boxPaint
        )

        canvas.drawLine(
            r.right - len,
            r.bottom,
            r.right - radius,
            r.bottom,
            boxPaint
        )

        canvas.drawLine(
            r.right,
            r.bottom - radius,
            r.right,
            r.bottom - len,
            boxPaint
        )

        canvas.drawArc(
            r.right - 2f * radius,
            r.bottom - 2f * radius,
            r.right,
            r.bottom,
            0f,
            90f,
            false,
            boxPaint
        )
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    fun clearFace() {
        frame = null
        postInvalidateOnAnimation()
    }
}