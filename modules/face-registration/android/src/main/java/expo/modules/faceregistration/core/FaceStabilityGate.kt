package expo.modules.faceregistration.core

internal class FaceStabilityGate {

    companion object {
        const val WARMUP_MS = 1_000L
        const val PROGRESS_MS = 2_000L

        private const val FRAME_CENTER_TOLERANCE = 0.045f
        private const val FRAME_SIZE_TOLERANCE = 0.07f
        private const val ANCHOR_CENTER_TOLERANCE = 0.11f
        private const val ANCHOR_SIZE_TOLERANCE = 0.13f
    }

    private val warmupMs: Long
    private val progressMs: Long

    private var tracking = false
    private var trackId = 0
    private var stableSince = 0L

    private var anchorX = 0f
    private var anchorY = 0f
    private var anchorWidth = 0f
    private var anchorHeight = 0f

    private var previousX = 0f
    private var previousY = 0f
    private var previousWidth = 0f
    private var previousHeight = 0f

    constructor() : this(
        WARMUP_MS,
        PROGRESS_MS
    )

    constructor(
        warmupMs: Long,
        progressMs: Long
    ) {
        this.warmupMs = maxOf(
            0L,
            warmupMs
        )

        this.progressMs = maxOf(
            1L,
            progressMs
        )
    }

    fun update(
        id: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        now: Long
    ): Float {
        val width = maxOf(
            1f,
            right - left
        )

        val height = maxOf(
            1f,
            bottom - top
        )

        val centerX = (left + right) * 0.5f
        val centerY = (top + bottom) * 0.5f

        if (
            !tracking ||
            trackId != id ||
            now < stableSince ||
            !isStable(
                centerX,
                centerY,
                width,
                height
            )
        ) {
            begin(
                id,
                centerX,
                centerY,
                width,
                height,
                now
            )

            return -1f
        }

        previousX = centerX
        previousY = centerY
        previousWidth = width
        previousHeight = height

        val elapsed = now - stableSince

        if (elapsed < warmupMs) {
            return -1f
        }

        return minOf(
            1f,
            (elapsed - warmupMs) / progressMs.toFloat()
        )
    }

    fun reset() {
        tracking = false
        stableSince = 0L
    }

    private fun begin(
        id: Int,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        now: Long
    ) {
        tracking = true
        trackId = id
        stableSince = now

        anchorX = centerX
        previousX = centerX

        anchorY = centerY
        previousY = centerY

        anchorWidth = width
        previousWidth = width

        anchorHeight = height
        previousHeight = height
    }

    private fun isStable(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float
    ): Boolean {
        return centerDelta(
            centerX,
            centerY,
            previousX,
            previousY,
            previousWidth,
            previousHeight
        ) <= FRAME_CENTER_TOLERANCE &&
            sizeDelta(
                width,
                height,
                previousWidth,
                previousHeight
            ) <= FRAME_SIZE_TOLERANCE &&
            centerDelta(
                centerX,
                centerY,
                anchorX,
                anchorY,
                anchorWidth,
                anchorHeight
            ) <= ANCHOR_CENTER_TOLERANCE &&
            sizeDelta(
                width,
                height,
                anchorWidth,
                anchorHeight
            ) <= ANCHOR_SIZE_TOLERANCE
    }

    private fun centerDelta(
        x: Float,
        y: Float,
        referenceX: Float,
        referenceY: Float,
        referenceWidth: Float,
        referenceHeight: Float
    ): Float {
        val scale = maxOf(
            1f,
            maxOf(
                referenceWidth,
                referenceHeight
            )
        )

        return (
            kotlin.math.hypot(
                (x - referenceX).toDouble(),
                (y - referenceY).toDouble()
            ).toFloat()
        ) / scale
    }

    private fun sizeDelta(
        width: Float,
        height: Float,
        referenceWidth: Float,
        referenceHeight: Float
    ): Float {
        return maxOf(
            kotlin.math.abs(width - referenceWidth) /
                maxOf(1f, referenceWidth),
            kotlin.math.abs(height - referenceHeight) /
                maxOf(1f, referenceHeight)
        )
    }
}