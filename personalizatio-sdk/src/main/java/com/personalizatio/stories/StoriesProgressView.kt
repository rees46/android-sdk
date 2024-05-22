package com.personalizatio.stories

import android.annotation.TargetApi

internal class StoriesProgressView : LinearLayout {
    private val PROGRESS_BAR_LAYOUT_PARAM: LayoutParams? = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1)
    private val SPACE_LAYOUT_PARAM: LayoutParams? = LayoutParams(5, LayoutParams.WRAP_CONTENT)

    val progressBars: List<PausableProgressBar?>? = ArrayList()

    private var storiesCount = -1
    private var color = 0

    /**
     * pointer of running animation
     */
    var current: Int = -1
    private var storiesListener: StoriesListener? = null
    var isComplete: Boolean = false

    private var isSkipStart = false
    private var isReverseStart = false

    interface StoriesListener {
        fun onNext()
        fun onStart(position: Int)

        fun onPrev()

        fun onComplete()
    }

    constructor(context: Context?) : this(context, null)

    constructor(context: Context?, @Nullable attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context?, @Nullable attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        init(context, attrs)
    }

    private fun init(context: Context?, @Nullable attrs: AttributeSet?) {
        setOrientation(LinearLayout.HORIZONTAL)
        val typedArray: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.StoriesProgressView)
        storiesCount = typedArray.getInt(R.styleable.StoriesProgressView_progressCount, 0)
        typedArray.recycle()
        bindViews()
    }

    private fun bindViews() {
        progressBars.clear()
        removeAllViews()

        for (i in 0 until storiesCount) {
            val p: PausableProgressBar? = createProgressBar()
            progressBars.add(p)
            addView(p)
            if ((i + 1) < storiesCount) {
                addView(createSpace())
            }
        }
    }

    private fun createProgressBar(): PausableProgressBar? {
        val p: PausableProgressBar = PausableProgressBar(getContext())
        p.setColor(color)
        p.setLayoutParams(PROGRESS_BAR_LAYOUT_PARAM)
        return p
    }

    private fun createSpace(): View? {
        val v: View = View(getContext())
        v.setLayoutParams(SPACE_LAYOUT_PARAM)
        return v
    }

    /**
     * Set story count and create views
     *
     * @param storiesCount story count
     */
    fun setStoriesCount(storiesCount: Int) {
        this.storiesCount = storiesCount
        bindViews()
    }

    fun setColor(color: Int) {
        this.color = color
    }

    /**
     * Set storiesListener
     *
     * @param storiesListener StoriesListener
     */
    fun setStoriesListener(storiesListener: StoriesListener?) {
        this.storiesListener = storiesListener
    }

    /**
     * Skip current story
     */
    fun skip() {
        if (isSkipStart || isReverseStart) return
        if (isComplete) return
        if (current < 0) return
        val p: PausableProgressBar? = progressBars.get(current)
        isSkipStart = true
        p.setMax()
    }

    /**
     * Reverse current story
     */
    fun reverse() {
        if (isSkipStart || isReverseStart) return
        if (isComplete) return
        if (current < 0) return
        val p: PausableProgressBar? = progressBars.get(current)
        isReverseStart = true
        p.setMin()
    }

    /**
     * Set a story's duration
     *
     * @param duration millisecond
     */
    fun setStoryDuration(duration: Long) {
        for (i in 0 until progressBars.size()) {
            progressBars.get(i).setDuration(duration)
            progressBars.get(i).setCallback(callback(i))
        }
    }

    /**
     * Set stories count and each story duration
     *
     * @param durations milli
     */
    fun setStoriesCountWithDurations(@NonNull durations: LongArray?) {
        storiesCount = durations.size
        bindViews()
        for (i in 0 until progressBars.size()) {
            progressBars.get(i).setDuration(durations.get(i))
            progressBars.get(i).setCallback(callback(i))
        }
    }

    fun updateStoryDuration(@NonNull position: Int, duration: Long) {
        progressBars.get(position).setDuration(duration)
    }

    private fun callback(index: Int): PausableProgressBar.Callback? {
        return object : Callback() {
            @Override
            fun onStartProgress() {
                current = index
                if (storiesListener != null) storiesListener.onStart(current)
            }

            @Override
            fun onFinishProgress() {
                if (isReverseStart) {
                    if (storiesListener != null) storiesListener.onPrev()
                    if (0 <= (current - 1)) {
                        val p: PausableProgressBar? = progressBars.get(current - 1)
                        p.setMinWithoutCallback()
                        progressBars.get(--current).startProgress()
                    } else {
                        progressBars.get(current).startProgress()
                    }
                    isReverseStart = false
                    return
                }
                val next = current + 1
                if (next <= (progressBars.size() - 1)) {
                    if (storiesListener != null) storiesListener.onNext()
                    progressBars.get(next).startProgress()
                } else {
                    isComplete = true
                    if (storiesListener != null) storiesListener.onComplete()
                }
                isSkipStart = false
            }
        }
    }

    /**
     * Start progress animation
     */
    fun startStories() {
        progressBars.get(0).startProgress()
    }

    /**
     * Start progress animation from specific progress
     */
    fun startStories(from: Int) {
        for (i in 0 until from) {
            progressBars.get(i).setMaxWithoutCallback()
        }
        progressBars.get(from).startProgress()
    }

    /**
     * Need to call when Activity or Fragment destroy
     */
    fun destroy() {
        for (p in progressBars) {
            p.clear()
        }
    }

    /**
     * Pause story
     */
    fun pause() {
        if (current < 0 || current >= progressBars.size()) return
        progressBars.get(current).pauseProgress()
    }

    /**
     * Resume story
     */
    fun resume() {
        if (current < 0 || current >= progressBars.size()) return
        progressBars.get(current).resumeProgress()
    }
}
