package com.personalization.stories.views

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.personalization.R
import com.personalization.stories.StoryState
import com.personalization.stories.models.Story
import kotlin.math.abs

class StoryDialog(
    private val storiesView: StoriesView,
    private val stories: List<Story>,
    private val startPosition: Int,
    private val completeShowStory: () -> Unit,
    private val cancelShowStory: () -> Unit
) : Dialog(
    storiesView.context,
    android.R.style.Theme_Translucent_NoTitleBar
), PullDismissLayout.Listener {

    private val storyViews = HashMap<Int, StoryView>()
    private val adapter: ViewPagerAdapter
    private var mViewPager: ViewPager2

    fun interface OnStoryStateListener {
        fun onStoryStateChanged(storyState: StoryState)
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_stories)
        val window = window
        val wlp = window!!.attributes

        wlp.gravity = Gravity.CENTER
        wlp.flags = wlp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        window.attributes = wlp
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        mViewPager = findViewById(R.id.view_pager)

        adapter = ViewPagerAdapter { storyState ->
            when (storyState) {
                StoryState.RUNNING -> mViewPager.isUserInputEnabled = true
                StoryState.PAUSE -> mViewPager.isUserInputEnabled = false
                StoryState.CLOSE -> cancel()
            }
        }

        setOnCancelListener {
            for (i in stories.indices) {
                getHolder(i)?.release()
            }

            cancelShowStory()
        }

        setupViews()
    }

    private fun setupViews() {
        (findViewById<View>(R.id.pull_dismiss_layout) as PullDismissLayout).listener = this

        val closeImageButton = findViewById<ImageButton>(R.id.imageButton)
        closeImageButton.setOnClickListener { onDismissed() }
        closeImageButton.setColorFilter(Color.parseColor(storiesView.settings.close_color))

        mViewPager.clipToPadding = false
        mViewPager.clipChildren = false
        mViewPager.offscreenPageLimit = 1

        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val dm = context.resources.displayMetrics
            val nextItemVisiblePx = (dm.widthPixels - dm.heightPixels * 9f / 16) / 2f
            val currentItemHorizontalMarginPx =
                nextItemVisiblePx + context.resources.getDimension(R.dimen.viewpager_current_item_horizontal_margin)
            val pageTranslationX = nextItemVisiblePx + currentItemHorizontalMarginPx
            mViewPager.setPageTransformer { page: View, position: Float ->
                val absPosition = abs(position.toDouble())
                val verticalScale = (1 - (0.25f * absPosition)).toFloat()
                page.translationX = -pageTranslationX * position
                page.scaleY = verticalScale
                page.scaleX = verticalScale
                page.alpha = (1 - (0.7f * absPosition)).toFloat()
            }
            mViewPager.addItemDecoration(object : ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    super.getItemOffsets(outRect, view, parent, state)
                    val margin = currentItemHorizontalMarginPx.toInt()
                    outRect.right = margin
                    outRect.left = margin
                }
            })
        }

        mViewPager.adapter = adapter

        //Hack to prevent onPageSelected from triggering when opening the first campaign
        mViewPager.setCurrentItem(if (startPosition == 0) stories.size else 0, false)

        mViewPager.setCurrentItem(startPosition, false)
        mViewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in stories.indices) {
                    if (i != position) {
                        getHolder(i)?.stopStories()
                    }
                }
                getHolder(position)?.startStories()
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    onReleased()
                }
            }
        })
    }

    private val currentHolder: StoryView?
        get() = getHolder(mViewPager.currentItem)

    private fun getHolder(position: Int): StoryView? {
        return storyViews[position]
    }

    override fun onDetachedFromWindow() {
        for (story in stories) {
            for (i in 0 until story.slidesCount) {
                story.getSlide(i).isPrepared = false
            }
        }
    }

    override fun onDismissed() {
        cancel()
    }

    override fun onReleased() {
        currentHolder?.resume()
    }

    override fun onShouldInterceptTouchEvent(): Boolean {
        return false
    }

    internal class PagerHolder(itemView: StoryView) : RecyclerView.ViewHolder(itemView)

    internal inner class ViewPagerAdapter(
        private val storyStateListener: OnStoryStateListener
    ) :
        RecyclerView.Adapter<PagerHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerHolder {
            return PagerHolder(StoryView(storiesView, storyStateListener))
        }

        override fun onViewAttachedToWindow(holder: PagerHolder) {
            if (holder.layoutPosition == mViewPager.currentItem) {
                super.onViewAttachedToWindow(holder)
                val storyView = holder.itemView as StoryView
                storyView.startStories()
            }
        }

        override fun onBindViewHolder(holder: PagerHolder, position: Int) {
            val view = holder.itemView as StoryView
            storyViews[position] = view
            view.setStory(stories[position], {
                if (position >= stories.size || position + 1 >= stories.size) {
                    cancel()
                } else {
                    mViewPager.currentItem = position + 1
                }

                completeShowStory()
            }, {
                if (position == 0) {
                    cancel()
                } else {
                    mViewPager.currentItem = position - 1
                }
            })
        }

        override fun getItemCount(): Int {
            return stories.size
        }
    }
}
