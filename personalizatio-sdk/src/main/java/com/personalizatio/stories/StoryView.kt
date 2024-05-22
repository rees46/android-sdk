package com.personalizatio.stories

import android.annotation.SuppressLint

internal class StoryView @SuppressLint("ClickableViewAccessibility") constructor(
    stories_view: StoriesView?,
    state_listener: StoryDialog.OnProgressState?
) : ConstraintLayout(stories_view.getContext()), StoriesProgressView.StoriesListener, Player.Listener {
    private val stories_view: StoriesView? = stories_view
    private var story: Story? = null

    var pressTime: Long = 0L
    private var completeListener: Runnable? = null
    private var prevStoryListener: Runnable? = null

    val storiesProgressView: StoriesProgressView?
    private val onTouchListener: OnTouchListener?
    val mViewPager: ViewPager2?
    private var storiesStarted = false
    private var prevFocusState = true
    private val locked = false
    private val holders: HashMap<Integer?, PagerHolder?>? = HashMap()
    private val mute: ToggleButton?

    //Heading
    private val state_listener: StoryDialog.OnProgressState? = state_listener

    init {
        inflate(getContext(), R.layout.story_view, this)
        setLayoutParams(LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        onTouchListener = OnTouchListener { v, event ->
            if (storiesStarted) {
                when (event.getAction()) {
                    MotionEvent.ACTION_DOWN -> {
                        pressTime = System.currentTimeMillis()
                        pause()
                        //							setHeadingVisibility(GONE);
                        return@OnTouchListener false
                    }

                    MotionEvent.ACTION_UP -> {
                        val now: Long = System.currentTimeMillis()
                        if (LIMIT < now - pressTime) {
                            resume()
                        }
                        //							setHeadingVisibility(VISIBLE);
                        return@OnTouchListener LIMIT < now - pressTime
                    }
                }
            }
            false
        }
        storiesProgressView = findViewById(R.id.storiesProgressView)
        storiesProgressView.setColor(Color.parseColor(stories_view.settings.background_progress))
        mViewPager = findViewById(R.id.storiesViewPager)
        storiesProgressView.setStoriesListener(this)
        mViewPager.setClipToPadding(false)
        mViewPager.setClipChildren(false)
        mViewPager.setOffscreenPageLimit(1)
        mViewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            @Override
            fun onPageSelected(position: Int) {
                if (stories_view.player.player.isPlaying() || stories_view.player.player.isLoading()) {
                    stories_view.player.player.pause()
                }
                //При изменении слайда останавливаем видео на скрытых
                for (i in 0 until story.slides.size()) {
                    val holder = getHolder(i)
                    if (holder != null) {
                        if (i != position) {
                            holder.release()
                        }
                    }
                }
                if (storiesStarted) {
                    SDK.track_story("view", stories_view.code, story.id, story.slides.get(position).id)
                    playVideo()
                }
            }
        })

        stories_view.player.player.setVolume(if (stories_view.isMute()) 0f else 1f)
        stories_view.mute_listener = {
            stories_view.player.player.setVolume(1f)
        }

        //Управление звуком
        mute = findViewById(R.id.mute)
        mute.setOnClickListener { v ->
            stories_view.muteVideo(mute.isChecked())
            stories_view.player.player.setVolume(if (mute.isChecked()) 0f else 1f)
        }
        mute.setChecked(stories_view.isMute())
    }

    fun setStory(story: Story?, completeListener: Runnable?, prevStoryListener: Runnable?) {
        this.story = story
        this.completeListener = completeListener
        this.prevStoryListener = prevStoryListener

        storiesProgressView.setStoriesCount(story.slides.size())
        mViewPager.setAdapter(ViewPagerAdapter())
        //Хак, чтобы не срабатывал onPageSelected при открытии первой кампании
        mViewPager.setCurrentItem(if (story.start_position === 0) story.slides.size() else 0, false)
        //Устанавливаем позицию
        mViewPager.setCurrentItem(story.start_position, false)
    }

    @UnstableApi
    private fun playVideo() {
        if (storiesStarted) {
            val holder = getHolder(mViewPager.getCurrentItem())
            if (holder != null) {
                val slide: Story.Slide = story.slides.get(mViewPager.getCurrentItem())
                if (slide.type.equals("video")) {
                    slide.prepared = false
                    //Подготавливаем плеер
                    stories_view.player.player.addListener(this)
                    holder.story_item.video.setVisibility(GONE)
                    holder.story_item.image.setAlpha(1f)
                    holder.story_item.video.setPlayer(stories_view.player.player)
                    stories_view.player.prepare(slide.background)
                    storiesProgressView.pause()
                    mute.setChecked(stories_view.isMute())
                }
            }
        }
    }

    @Override
    fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> storiesProgressView.pause()
            Player.STATE_READY -> stories_view.player.player.play()
        }
    }

    @Override
    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            val holder = getHolder(mViewPager.getCurrentItem())
            holder.story_item.video.setVisibility(VISIBLE)
            holder.story_item.image.animate().alpha(0).setDuration(300)
            story.slides.get(mViewPager.getCurrentItem()).prepared = true
            resume()
        }
    }

    @Override
    fun onTracksChanged(@NonNull tracks: Tracks?) {
        if (stories_view.player.player.getContentDuration() > 0) {
            story.slides.get(mViewPager.getCurrentItem()).duration = stories_view.player.player.getContentDuration()
            updateDuration(mViewPager.getCurrentItem())
        }
        mute.setVisibility(if (tracks.isTypeSupported(C.TRACK_TYPE_AUDIO)) VISIBLE else GONE)
    }

    @Override
    fun onPlayerError(error: PlaybackException?) {
        Log.e(SDK.TAG, ("player error: " + error.getMessage()).toString() + ", story: " + story.id)
        val holder = getCurrentHolder()
        holder.story_item.reload_layout.setVisibility(VISIBLE)

        holder.story_item.reload.setOnClickListener { View ->
            val slide: Story.Slide = story.slides.get(mViewPager.getCurrentItem())
            if (slide.type.equals("video")) {
                holder.story_item.reload_layout.setVisibility(GONE)
                playVideo()
            } else {
                holder.story_item.update(slide, mViewPager.getCurrentItem(), stories_view.code, story.id)
            }
        }

        //Добавляем авто-обновление
        holder.story_item.reload.postDelayed({ holder.story_item.reload.callOnClick() }, 15000L)
    }

    @Override
    fun onVolumeChanged(volume: Float) {
        mute.setChecked(volume == 0f)
    }

    internal inner class PagerHolder(@NonNull view: View?) : RecyclerView.ViewHolder(view) {
        var story_item: StoryItemView? = view as StoryItemView?

        init {
            story_item.setStoriesView(stories_view)
            story_item.setOnPageListener(object : OnPageListener() {
                @Override
                fun onPrev() {
                    previousSlide()
                }

                @Override
                fun onNext() {
                    nextSlide()
                }

                @Override
                fun onPrepared(position: Int) {
                    if (story.slides.get(position).type.equals("image") && storiesStarted) {
                        try {
                            storiesProgressView.resume()
                        } catch (e: IndexOutOfBoundsException) {
                        }
                    }
                }

                @Override
                fun onLocked(lock: Boolean) {
                    locked = lock
                    state_listener.onState(!lock)
                    if (lock) {
                        pause()
                    } else {
                        resume()
                    }
                }
            })
        }

        fun bind(slide: Story.Slide?, position: Int) {
            holders.put(position, this)
            mute.setVisibility(GONE)
            story_item.update(slide, position, stories_view.code, story.id)
            story_item.setOnTouchListener(onTouchListener)

            //Устанавливаем загрузку видео, если биндим текущий элемент
            if (position == mViewPager.getCurrentItem()) {
                playVideo()
            }
        }

        fun release() {
            story_item.release()
        }
    }

    internal inner class ViewPagerAdapter : RecyclerView.Adapter<PagerHolder?>() {
        @NonNull
        @Override
        fun onCreateViewHolder(@NonNull parent: ViewGroup?, viewType: Int): PagerHolder? {
            return PagerHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.story_item, parent, false))
        }

        @Override
        fun onBindViewHolder(@NonNull holder: PagerHolder?, position: Int) {
            holder.bind(story.slides.get(position), position)
        }

        @Override
        fun getItemCount(): Int {
            return story.slides.size()
        }
    }

    @Override
    fun onWindowFocusChanged(state: Boolean) {
        super.onWindowFocusChanged(state)
        //Когда окно сворачивается, нужно остановить видео и прогресс
        if (storiesProgressView != null && storiesStarted && prevFocusState != state) {
            prevFocusState = state
            if (state) {
                resume()
            } else {
                pause()
            }
        }
    }

    fun updateDurations() {
        val durations = LongArray(story.slides.size())
        for (i in 0 until story.slides.size()) {
            durations[i] = story.slides.get(i).duration
        }
        storiesProgressView.setStoriesCountWithDurations(durations)
    }

    private fun getCurrentHolder(): PagerHolder? {
        return getHolder(mViewPager.getCurrentItem())
    }

    private fun getHolder(position: Int): PagerHolder? {
        return holders.get(position)
    }

    fun pause() {
        storiesProgressView.pause()
        stories_view.player.player.pause()
    }

    fun resume() {
        if (!locked && story.slides.get(mViewPager.getCurrentItem()).prepared) {
            storiesProgressView.resume()
            if (story.slides.get(mViewPager.getCurrentItem()).type.equals("video") && !stories_view.player.player.isLoading() && !stories_view.player.player.isPlaying()) {
                stories_view.player.player.play()
            }
        }
    }

    @Override
    fun onNext() {
        if (story.start_position + 1 >= story.slides.size()) {
            onComplete()
            return
        }
        mViewPager.setCurrentItem(++story.start_position, false)
        storiesProgressView.startStories(story.start_position)
    }

    @Override
    fun onStart(position: Int) {
        //Для текущего слайда
        if (position == mViewPager.getCurrentItem()) {
            val slide: Story.Slide = story.slides.get(position)
            //Если видео еще подгружается, приостанавливаем таймер анимации
            if (!slide.prepared) {
                storiesProgressView.pause()
            }
        }
    }

    @Override
    fun onPrev() {
        if (story.start_position <= 0) {
            prevStoryListener.run()
            return
        }
        mViewPager.setCurrentItem(--story.start_position, false)
        storiesProgressView.startStories(story.start_position)
    }

    @Override
    fun onComplete() {
        story.viewed = true
        story.start_position = 0
        updateDurations()
        completeListener.run()
    }

    fun updateDuration(position: Int) {
        storiesProgressView.updateStoryDuration(position, story.slides.get(position).duration)
    }

    fun startStories() {
        if (!storiesStarted) {
            storiesStarted = true
            playVideo()
            SDK.track_story("view", stories_view.code, story.id, story.slides.get(story.start_position).id)
        }
        if (!locked) {
            updateDurations()
            storiesProgressView.startStories(story.start_position)
            mViewPager.setCurrentItem(story.start_position, false)
        }
    }

    fun stopStories() {
        storiesStarted = false
        //Если кампания сториса не активна на экране, удаляем листенеры
        stories_view.player.player.removeListener(this)
        pause()
        storiesProgressView.destroy()
        val holder = getCurrentHolder()
        holder?.release()
    }

    private fun previousSlide() {
        if (storiesStarted && !locked) {
            updateDurations()
            onPrev()
        }
    }

    fun nextSlide() {
        if (storiesStarted && !locked) {
            onNext()
        }
    }

    fun release() {
        stories_view.player.player.removeListener(this)
        stories_view.player.player.stop()
        for (i in 0 until story.slides.size()) {
            val holder = getHolder(i)
            holder?.release()
        }
    }

    companion object {
        private const val LIMIT = 500L
    }
}
