package com.personalization.stories.views

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import kotlin.math.abs
import kotlin.math.max

internal class PullDismissLayout : FrameLayout {
    var listener: Listener? = null
    var minFlingVelocity = 0f
    var animateAlpha = false
    private var dragHelper: ViewDragHelper? = null
    private var verticalTouchSlop = 0f

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        init(context)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int, defResStyle: Int) : super(
        context,
        attrs,
        defStyle,
        defResStyle
    ) {
        init(context)
    }

    private fun init(context: Context) {
        if (!isInEditMode) {
            val vc = ViewConfiguration.get(context)
            minFlingVelocity = vc.scaledMinimumFlingVelocity.toFloat()
            dragHelper = ViewDragHelper.create(this, ViewDragCallback(this))
        }
    }

    override fun computeScroll() {
        super.computeScroll()
        if (dragHelper?.continueSettling(true) == true) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        dragHelper?.let { dragHelper ->
            val action = MotionEventCompat.getActionMasked(event)

            var pullingDown = false

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    verticalTouchSlop = event.y
                    val dy = event.y - verticalTouchSlop
                    if (dy > dragHelper.touchSlop) {
                        pullingDown = true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - verticalTouchSlop
                    if (dy > dragHelper.touchSlop) {
                        pullingDown = true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> verticalTouchSlop = 0.0f
            }
            if (!dragHelper.shouldInterceptTouchEvent(event) && pullingDown) {
                if (dragHelper.viewDragState == ViewDragHelper.STATE_IDLE
                    && dragHelper.checkTouchSlop(ViewDragHelper.DIRECTION_VERTICAL)) {
                    val child = getChildAt(0)
                    if (child != null && listener?.onShouldInterceptTouchEvent() != true) {
                        dragHelper.captureChildView(child, event.getPointerId(0))
                        return dragHelper.viewDragState == ViewDragHelper.STATE_DRAGGING
                    }
                }
            }
        }

        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        dragHelper?.let { dragHelper ->
            dragHelper.processTouchEvent(event)
            return dragHelper.capturedView != null
        }

        return false
    }

    private class ViewDragCallback(private val pullDismissLayout: PullDismissLayout) : ViewDragHelper.Callback() {
        private var startTop = 0
        private var dragPercent = 0.0f
        private var capturedView: View? = null
        private var dismissed = false

        override fun tryCaptureView(view: View, i: Int): Boolean {
            return capturedView == null
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            return max(top.toDouble(), 0.0).toInt()
        }

        override fun onViewCaptured(view: View, activePointerId: Int) {
            capturedView = view
            startTop = view.top
            dragPercent = 0.0f
            dismissed = false
        }

        @SuppressLint("NewApi")
        override fun onViewPositionChanged(view: View, left: Int, top: Int, dx: Int, dy: Int) {
            val range = pullDismissLayout.height
            val moved = abs((top - startTop).toDouble()).toInt()
            if (range > 0) {
                dragPercent = moved.toFloat() / range.toFloat()
            }
            if (pullDismissLayout.animateAlpha) {
                view.alpha = 1.0f - dragPercent
                pullDismissLayout.invalidate()
            }
        }

        override fun onViewDragStateChanged(state: Int) {
            if (capturedView != null && dismissed && state == ViewDragHelper.STATE_IDLE) {
                pullDismissLayout.removeView(capturedView)
                pullDismissLayout.listener?.onDismissed()
            }
        }

        override fun onViewReleased(view: View, xv: Float, yv: Float) {
            dismissed = dragPercent >= 0.50f
                    || (abs(xv.toDouble()) > pullDismissLayout.minFlingVelocity && dragPercent > 0.20f)
            val finalTop = if (dismissed) pullDismissLayout.height else startTop
            if (!dismissed) {
                pullDismissLayout.listener?.onReleased()
            }
            pullDismissLayout.dragHelper?.settleCapturedViewAt(0, finalTop)
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
