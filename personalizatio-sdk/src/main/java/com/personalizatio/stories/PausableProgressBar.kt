package com.personalizatio.stories

import android.content.Context

internal class PausableProgressBar(
    @NonNull context: Context?,
    @Nullable attrs: AttributeSet?,
    @AttrRes defStyleAttr: Int
) : FrameLayout(context, attrs, defStyleAttr) {
    val frontProgressView: View?
    val maxProgressView: View?

    var animation: PausableScaleAnimation? = null
    private var duration = DEFAULT_PROGRESS_DURATION.toLong()
    private var callback: Callback? = null
    private var paused = false

    internal interface Callback {
        fun onStartProgress()

        fun onFinishProgress()
    }

    constructor(context: Context?) : this(context, null)

    constructor(@NonNull context: Context?, @Nullable attrs: AttributeSet?) : this(context, attrs, 0)

    init {
        LayoutInflater.from(context).inflate(R.layout.pausable_progress, this)
        frontProgressView = findViewById(R.id.front_progress)
        maxProgressView = findViewById(R.id.max_progress) // work around
    }

    fun setColor(color: Int) {
        frontProgressView.setBackgroundColor(color)
        maxProgressView.setBackgroundColor(color)
    }

    fun setDuration(duration: Long) {
        this.duration = duration
        if (animation != null) {
            animation.setDuration(duration)
        }
    }

    fun setCallback(@NonNull callback: Callback?) {
        this.callback = callback
    }

    fun setMax() {
        finishProgress(true)
    }

    fun setMin() {
        finishProgress(false)
    }

    fun setMinWithoutCallback() {
        maxProgressView.setBackgroundResource(R.color.progress_secondary)

        maxProgressView.setVisibility(VISIBLE)
        if (animation != null) {
            animation.setAnimationListener(null)
            animation.cancel()
        }
    }

    fun setMaxWithoutCallback() {
//		maxProgressView.setBackgroundResource(R.color.progress_max_active);

        maxProgressView.setVisibility(VISIBLE)
        if (animation != null) {
            animation.setAnimationListener(null)
            animation.cancel()
        }
    }

    private fun finishProgress(isMax: Boolean) {
        if (isMax) maxProgressView.setBackgroundResource(R.color.progress_max_active)
        maxProgressView.setVisibility(if (isMax) VISIBLE else GONE)
        if (animation != null) {
            animation.setAnimationListener(null)
            animation.cancel()
            if (callback != null) {
                callback.onFinishProgress()
            }
        }
    }

    fun startProgress() {
        maxProgressView.setVisibility(GONE)

        animation = PausableScaleAnimation(0f, 1f, 1f, 1f, Animation.ABSOLUTE, 0f, Animation.RELATIVE_TO_SELF, 0f)
        animation.setDuration(duration)
        animation.setInterpolator(LinearInterpolator())
        animation.setAnimationListener(object : AnimationListener() {
            @Override
            fun onAnimationStart(animation: Animation?) {
                frontProgressView.setVisibility(View.VISIBLE)
                if (callback != null) callback.onStartProgress()
            }

            @Override
            fun onAnimationRepeat(animation: Animation?) {
            }

            @Override
            fun onAnimationEnd(animation: Animation?) {
                if (callback != null && !paused) {
                    Log.d(SDK.TAG, "onAnimationEnd")
                    callback.onFinishProgress()
                }
            }
        })
        animation.setFillAfter(true)
        frontProgressView.startAnimation(animation)
    }

    fun pauseProgress() {
        if (animation != null) {
            paused = true
            animation.pause()
        }
    }

    fun resumeProgress() {
        if (animation != null) {
            paused = false
            animation.resume()
        }
    }

    fun clear() {
        if (animation != null) {
            animation.setAnimationListener(null)
            animation.cancel()
            animation = null
        }
    }

    class PausableScaleAnimation internal constructor(
        fromX: Float, toX: Float, fromY: Float,
        toY: Float, pivotXType: Int, pivotXValue: Float, pivotYType: Int,
        pivotYValue: Float
    ) : ScaleAnimation(
        fromX, toX, fromY, toY, pivotXType, pivotXValue, pivotYType,
        pivotYValue
    ) {
        private var mElapsedAtPause: Long = 0
        private var mPaused = false

        @Override
        fun getTransformation(currentTime: Long, outTransformation: Transformation?, scale: Float): Boolean {
            if (mPaused && mElapsedAtPause == 0L) {
                mElapsedAtPause = currentTime - getStartTime()
            }
            if (mPaused) {
                setStartTime(currentTime - mElapsedAtPause)
            }
            return super.getTransformation(currentTime, outTransformation, scale)
        }

        /***
         * pause animation
         */
        fun pause() {
            if (mPaused) return
            mElapsedAtPause = 0
            mPaused = true
        }

        /***
         * resume animation
         */
        fun resume() {
            mPaused = false
        }
    }

    companion object {
        private const val DEFAULT_PROGRESS_DURATION = 2000
    }
}
