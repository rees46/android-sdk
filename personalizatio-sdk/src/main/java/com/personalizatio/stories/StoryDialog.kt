package com.personalizatio.stories

import android.app.Dialog

internal class StoryDialog(
    stories_view: StoriesView?,
    stories: ArrayList<Story?>?,
    start_position: Int,
    completeListener: Runnable?
) : Dialog(stories_view.getContext(), android.R.style.Theme_Translucent_NoTitleBar), PullDismissLayout.Listener {
    private val stories: ArrayList<Story?>?
    private val storyViews: HashMap<Integer?, StoryView?>? = HashMap()
    private val adapter: ViewPagerAdapter?
    private var mViewPager: ViewPager2? = null

    private val completeListener: Runnable?
    private val start_position: Int
    private val stories_view: StoriesView?

    interface OnProgressState {
        fun onState(running: Boolean)
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_stories)
        val window: Window = getWindow()
        val wlp: WindowManager.LayoutParams = window.getAttributes()

        wlp.gravity = Gravity.CENTER
        wlp.flags = wlp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        window.setAttributes(wlp)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        this.stories_view = stories_view
        this.stories = stories
        this.start_position = start_position
        adapter = ViewPagerAdapter(OnProgressState { running: Boolean -> mViewPager.setUserInputEnabled(running) })

        this.completeListener = completeListener

        setOnCancelListener { dialog ->
            for (i in 0 until stories.size()) {
                val holder: StoryView? = getHolder(i)
                if (holder != null) {
                    holder.release()
                }
            }
        }

        setupViews()
    }

    private fun setupViews() {
        (findViewById(R.id.pull_dismiss_layout) as PullDismissLayout?).setListener(this)
        val closeImageButton: ImageButton = findViewById(R.id.imageButton)
        closeImageButton.setOnClickListener { v ->
            onDismissed()
        }
        closeImageButton.setColorFilter(Color.parseColor(stories_view.settings.close_color))
        mViewPager = findViewById(R.id.view_pager)
        mViewPager.setClipToPadding(false)
        mViewPager.setClipChildren(false)
        mViewPager.setOffscreenPageLimit(1)

        //Превью слайдов только в горизонтальном экране
        if (getContext().getResources().getConfiguration().orientation === Configuration.ORIENTATION_LANDSCAPE) {
            val dm: DisplayMetrics = getContext().getResources().getDisplayMetrics()
            val nextItemVisiblePx: Float = (dm.widthPixels - dm.heightPixels * 9f / 16) / 2f
            val currentItemHorizontalMarginPx: Float = nextItemVisiblePx + getContext().getResources()
                .getDimension(R.dimen.viewpager_current_item_horizontal_margin)
            val pageTranslationX = nextItemVisiblePx + currentItemHorizontalMarginPx
            mViewPager.setPageTransformer { page, position ->
                page.setTranslationX(-pageTranslationX * position)
                page.setScaleY(1 - (0.25f * Math.abs(position)))
                page.setScaleX(1 - (0.25f * Math.abs(position)))
                page.setAlpha(1 - (0.7f * Math.abs(position)))
            }
            mViewPager.addItemDecoration(object : ItemDecoration() {
                @Override
                fun getItemOffsets(
                    @NonNull outRect: Rect?,
                    @NonNull view: View?,
                    @NonNull parent: RecyclerView?,
                    @NonNull state: RecyclerView.State?
                ) {
                    super.getItemOffsets(outRect, view, parent, state)
                    outRect.right = currentItemHorizontalMarginPx.toInt()
                    outRect.left = currentItemHorizontalMarginPx.toInt()
                }
            })
        }

        mViewPager.setAdapter(adapter)
        //Хак, чтобы не срабатывал onPageSelected при открытии первой кампании
        mViewPager.setCurrentItem(if (start_position == 0) stories.size() else 0, false)
        //Устанавливаем позицию
        mViewPager.setCurrentItem(start_position, false)
        mViewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            @Override
            fun onPageSelected(position: Int) {
                for (i in 0 until stories.size()) {
                    val holder: StoryView? = getHolder(i)
                    if (holder != null) {
                        if (i != position) {
                            holder.stopStories()
                        }
                    }
                }
                val holder: StoryView? = getHolder(position)
                if (holder != null) {
                    holder.startStories()
                }
            }

            @Override
            fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    onReleased()
                }
            }
        })
    }

    private fun getCurrentHolder(): StoryView? {
        return getHolder(mViewPager.getCurrentItem())
    }

    private fun getHolder(position: Int): StoryView? {
        return storyViews.get(position)
    }

    fun onDetachedFromWindow() {
        //При закрытии диалогового окна, возвращаем все метки в исходное
        for (story in stories) {
            for (slide in story.slides) {
                slide.prepared = false
            }
        }
    }

    @Override
    fun onDismissed() {
        cancel()
    }

    @Override
    fun onReleased() {
        val holder: StoryView? = getCurrentHolder()
        if (holder != null) {
            holder.resume()
        }
    }

    @Override
    fun onShouldInterceptTouchEvent(): Boolean {
        return false
    }

    internal class PagerHolder(@NonNull itemView: StoryView?) : RecyclerView.ViewHolder(itemView)

    internal inner class ViewPagerAdapter(private val state_listener: OnProgressState?) :
        RecyclerView.Adapter<PagerHolder?>() {
        @NonNull
        @Override
        fun onCreateViewHolder(@NonNull parent: ViewGroup?, viewType: Int): PagerHolder? {
            return PagerHolder(StoryView(stories_view, state_listener))
        }

        @Override
        fun onViewAttachedToWindow(@NonNull holder: PagerHolder?) {
            if (holder.getLayoutPosition() === mViewPager.getCurrentItem()) {
                super.onViewAttachedToWindow(holder)
                val storyView: StoryView? = holder.itemView as StoryView?
                storyView.startStories()
            }
        }

        @Override
        fun onBindViewHolder(@NonNull holder: PagerHolder?, position: Int) {
            val view: StoryView? = holder.itemView as StoryView?
            storyViews.put(position, view)
            view.setStory(stories.get(position), {
                if (position >= stories.size() || position + 1 >= stories.size()) {
                    cancel()
                } else {
                    mViewPager.setCurrentItem(position + 1)
                }
                completeListener.run()
            }, {
                if (position == 0) {
                    cancel()
                } else {
                    mViewPager.setCurrentItem(position - 1)
                }
            })
        }

        @Override
        fun getItemCount(): Int {
            return stories.size()
        }
    }
}
