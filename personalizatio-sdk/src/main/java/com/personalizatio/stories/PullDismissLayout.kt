package com.personalizatio.stories

import android.annotation.SuppressLint

internal class PullDismissLayout : FrameLayout {
    private var listener: Listener? = null
    private var dragHelper: ViewDragHelper? = null
    private var minFlingVelocity = 0f
    private var verticalTouchSlop = 0f
    private var animateAlpha = false

    constructor(@NonNull context: Context?) : super(context) {
        init(context)
    }

    constructor(@NonNull context: Context?, @Nullable attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(@NonNull context: Context?, @Nullable attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(context)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(@NonNull context: Context?, @Nullable attrs: AttributeSet?, defStyle: Int, defResStyle: Int) : super(
        context,
        attrs,
        defStyle,
        defResStyle
    ) {
        init(context)
    }

    private fun init(@NonNull context: Context?) {
        if (!isInEditMode()) {
            val vc: ViewConfiguration = ViewConfiguration.get(context)
            minFlingVelocity = vc.getScaledMinimumFlingVelocity() as Float
            dragHelper = ViewDragHelper.create(this, ViewDragCallback(this))
        }
    }

    fun computeScroll() {
        super.computeScroll()
        if (dragHelper != null && dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        val action: Int = MotionEventCompat.getActionMasked(event)

        var pullingDown = false

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                verticalTouchSlop = event.getY()
                val dy: Float = event.getY() - verticalTouchSlop
                if (dy > dragHelper.getTouchSlop()) {
                    pullingDown = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dy: Float = event.getY() - verticalTouchSlop
                if (dy > dragHelper.getTouchSlop()) {
                    pullingDown = true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> verticalTouchSlop = 0.0f
        }
        if (!dragHelper.shouldInterceptTouchEvent(event) && pullingDown) {
            if (dragHelper.getViewDragState() === ViewDragHelper.STATE_IDLE &&
                dragHelper.checkTouchSlop(ViewDragHelper.DIRECTION_VERTICAL)
            ) {
                val child: View = getChildAt(0)
                if (child != null && !listener.onShouldInterceptTouchEvent()) {
                    dragHelper.captureChildView(child, event.getPointerId(0))
                    return dragHelper.getViewDragState() === ViewDragHelper.STATE_DRAGGING
                }
            }
        }
        return false
    }

    fun onTouchEvent(event: MotionEvent?): Boolean {
        dragHelper.processTouchEvent(event)
        return dragHelper.getCapturedView() != null
    }

    fun setMinFlingVelocity(velocity: Float) {
        minFlingVelocity = velocity
    }

    fun setAnimateAlpha(b: Boolean) {
        animateAlpha = b
    }

    fun setListener(l: Listener?) {
        listener = l
    }

    private class ViewDragCallback private constructor(private val pullDismissLayout: PullDismissLayout?) :
        ViewDragHelper.Callback() {
        private var startTop = 0
        private var dragPercent = 0.0f
        private var capturedView: View? = null
        private var dismissed = false

        fun tryCaptureView(@NonNull view: View?, i: Int): Boolean {
            return capturedView == null
        }

        fun clampViewPositionVertical(@NonNull child: View?, top: Int, dy: Int): Int {
            return Math.max(top, 0)
        }

        fun onViewCaptured(view: View?, activePointerId: Int) {
            capturedView = view
            startTop = view.getTop()
            dragPercent = 0.0f
            dismissed = false
        }

        @SuppressLint(["NewApi"])
        fun onViewPositionChanged(@NonNull view: View?, left: Int, top: Int, dx: Int, dy: Int) {
            val range: Int = pullDismissLayout.getHeight()
            val moved: Int = Math.abs(top - startTop)
            if (range > 0) {
                dragPercent = moved.toFloat() / range.toFloat()
            }
            if (pullDismissLayout.animateAlpha) {
                view.setAlpha(1.0f - dragPercent)
                pullDismissLayout.invalidate()
            }
        }

        fun onViewDragStateChanged(state: Int) {
            if (capturedView != null && dismissed && state == ViewDragHelper.STATE_IDLE) {
                pullDismissLayout.removeView(capturedView)
                if (pullDismissLayout.listener != null) {
                    pullDismissLayout.listener.onDismissed()
                }
            }
        }

        fun onViewReleased(@NonNull view: View?, xv: Float, yv: Float) {
            dismissed =
                dragPercent >= 0.50f || (Math.abs(xv) > pullDismissLayout.minFlingVelocity && dragPercent > 0.20f)
            val finalTop = if (dismissed) pullDismissLayout.getHeight() else startTop
            if (!dismissed && pullDismissLayout.listener != null) {
                pullDismissLayout.listener.onReleased()
            }
            pullDismissLayout.dragHelper.settleCapturedViewAt(0, finalTop)
            pullDismissLayout.invalidate()
        }
    }

    interface Listener {
        /**
         * Layout is pulled down to dismiss
         * Good time to finish activity, remove fragment or any view
         */
        fun onDismissed()

        fun onReleased()

        /**
         * Convenient method to avoid layout_color overriding event
         * If you have a RecyclerView or ScrollerView in our layout_color your can
         * avoid PullDismissLayout to handle event.
         *
         * @return true when ignore pull down event, f
         * false for allow PullDismissLayout handle event
         */
        fun onShouldInterceptTouchEvent(): Boolean
    }
}
