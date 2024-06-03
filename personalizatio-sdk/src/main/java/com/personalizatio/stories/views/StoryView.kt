package com.personalizatio.stories.views

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.util.Pair
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.ToggleButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.personalizatio.R
import com.personalizatio.SDK
import com.personalizatio.stories.models.Slide
import com.personalizatio.stories.models.Story
import com.personalizatio.stories.views.StoriesProgressView.StoriesListener
import com.personalizatio.stories.views.StoryDialog.OnProgressState
import com.personalizatio.stories.views.storyItem.StoryItemView
import com.personalizatio.stories.views.storyItem.StoryItemView.OnPageListener

@SuppressLint("ViewConstructor")
internal class StoryView @SuppressLint("ClickableViewAccessibility") constructor(
    private val storiesView: StoriesView, //Heading
    private val stateListener: OnProgressState) : ConstraintLayout(storiesView.context), StoriesListener, Player.Listener {
    private var story: Story? = null

    private var pressTime = 0L
    private var completeListener: Runnable? = null
    private var prevStoryListener: Runnable? = null

    private lateinit var storiesProgressView: StoriesProgressView
    private var onTouchListener: OnTouchListener? = null
    private lateinit var mViewPager: ViewPager2
    private var storiesStarted = false
    private var prevFocusState = true
    private var locked = false
    private val holders = HashMap<Int, PagerHolder>()
    private lateinit var mute: ToggleButton

    private var viewPagerSize: Pair<Int, Int>? = null

    init {
        inflate(context, R.layout.story_view, this)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        initViews()
        setupViews();
    }

    private fun initViews() {
        storiesProgressView = findViewById(R.id.storiesProgressView)

        mViewPager = findViewById(R.id.storiesViewPager)

        mute = findViewById(R.id.mute)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupViews() {
        onTouchListener = OnTouchListener { _: View?, event: MotionEvent ->
            if (storiesStarted) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        pressTime = System.currentTimeMillis()
                        pause()
                        //							setHeadingVisibility(GONE);
                        return@OnTouchListener false
                    }

                    MotionEvent.ACTION_UP -> {
                        val now = System.currentTimeMillis()
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

        storiesProgressView.color = Color.parseColor(storiesView.settings.background_progress)
        storiesProgressView.storiesListener = this

        mViewPager.clipToPadding = false
        mViewPager.clipChildren = false
        mViewPager.offscreenPageLimit = 1
        mViewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val player = com.personalizatio.stories.Player.player
                player?.apply {
                    if (player.isPlaying || player.isLoading) {
                        player.pause()
                    }
                }
                //При изменении слайда останавливаем видео на скрытых
                for (i in 0 until story!!.slidesCount) {
                    if (i != position) {
                        getHolder(i)?.release()
                    }
                }
                if (storiesStarted) {
                    story?.apply {
                        SDK.track_story("view", storiesView.code, id, getSlide(position).id)
                        playVideo()
                    }
                }
            }
        })

        com.personalizatio.stories.Player?.apply {
            player!!.volume = if (storiesView.isMute) 0f else 1f
            storiesView.muteListener = Runnable {
                player!!.volume = 1f
            }

            //Управление звуком
            mute.setOnClickListener {
                storiesView.muteVideo(mute.isChecked)
                player!!.volume = if (mute.isChecked) 0f else 1f
            }
        }

        mute.isChecked = storiesView.isMute
    }

    fun setStory(story: Story, completeListener: Runnable, prevStoryListener: Runnable) {
        this.story = story
        this.completeListener = completeListener
        this.prevStoryListener = prevStoryListener

        val slidesCount = story.slidesCount
        storiesProgressView.setStoriesCount(slidesCount)
        mViewPager.adapter = ViewPagerAdapter()
        //Хак, чтобы не срабатывал onPageSelected при открытии первой кампании
        mViewPager.setCurrentItem(if (story.startPosition == 0) slidesCount else 0, false)
        //Устанавливаем позицию
        mViewPager.setCurrentItem(story.startPosition, false)
    }

    @UnstableApi
    private fun playVideo() {
        if (storiesStarted) {
            val holder = getHolder(mViewPager.currentItem)

            if (holder != null) {
                val slide = story?.getSlide(mViewPager.currentItem)
                if (slide?.type == "video") {
                    slide.isPrepared = false
                    //Подготавливаем плеер
                    val player = com.personalizatio.stories.Player.player
                    player?.addListener(this)
                    holder.storyItem.video.visibility = GONE
                    holder.storyItem.image.alpha = 1f
                    holder.storyItem.video.player = player
                    storiesView.player!!.prepare(slide.background)
                    storiesProgressView.pause()
                    mute.isChecked = storiesView.isMute
                }
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: @Player.State Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> storiesProgressView.pause()
            Player.STATE_READY -> com.personalizatio.stories.Player.player?.play()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            val holder = getHolder(mViewPager.currentItem)
            holder?.apply {
                holder.storyItem.video.visibility = VISIBLE
                holder.storyItem.image.animate().alpha(0f).setDuration(300)
            }
            story?.getSlide(mViewPager.currentItem)?.isPrepared = true
            resume()
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        val player = com.personalizatio.stories.Player.player
        var contentDuration = 0L
        if(player != null)
        {
            contentDuration = player.contentDuration
        }

        if (contentDuration > 0) {
            val currentItem = mViewPager.currentItem
            story?.getSlide(currentItem)?.duration = contentDuration
            updateDuration(currentItem)
        }

        mute.visibility = if (tracks.isTypeSupported(C.TRACK_TYPE_AUDIO)) VISIBLE else GONE
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(SDK.TAG, "player error: " + error.message + ", story: " + story?.id)
        val holder = currentHolder
        holder?.storyItem?.apply {
            reloadLayout.visibility = VISIBLE
            reload.setOnClickListener {
                val slide = story?.getSlide(mViewPager.currentItem)
                slide?.apply {
                    if (type == "video") {
                        holder.storyItem.reloadLayout.visibility = GONE
                        playVideo()
                    } else {
                        storiesView.code?.let { code ->
                            holder.storyItem.update(this, mViewPager.currentItem, code, story!!.id)
                        }
                    }
                }
            }
            //Добавляем авто-обновление
            reload.postDelayed({ holder.storyItem.reload.callOnClick() }, 15000L)
        }
    }

    override fun onVolumeChanged(volume: Float) {
        mute.isChecked = volume == 0f
    }

    internal inner class PagerHolder(view: View) : RecyclerView.ViewHolder(view) {
        val storyItem: StoryItemView = view as StoryItemView

        init {
            if (viewPagerSize == null) {
                val viewPagerHeight = mViewPager.height
                val params = storiesProgressView.layoutParams as LayoutParams
                val viewPagerTopOffset = params.height + params.bottomMargin + params.topMargin
                viewPagerSize = Pair(viewPagerHeight, viewPagerTopOffset)
            }
            storyItem.setViewSize(viewPagerSize!!.first, viewPagerSize!!.second)
            storyItem.setOnPageListener(object : OnPageListener {
                override fun onPrev() {
                    previousSlide()
                }

                override fun onNext() {
                    nextSlide()
                }

                override fun onPrepared(position: Int) {
                    if (story?.getSlide(position)?.type == "image" && storiesStarted) {
                        try {
                            storiesProgressView.resume()
                        } catch (_: IndexOutOfBoundsException) {
                        }
                    }
                }

                override fun onLocked(lock: Boolean) {
                    locked = lock
                    stateListener.onState(!lock)
                    if (lock) {
                        pause()
                    } else {
                        resume()
                    }
                }
            })
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(slide: Slide?, position: Int) {
            holders[position] = this
            mute.visibility = GONE
            slide?.apply { storyItem.update(this, position, storiesView.code!!, story!!.id) }
            onTouchListener?.apply { storyItem.setOnTouchListener(this)  }

            //Устанавливаем загрузку видео, если биндим текущий элемент
            if (position == mViewPager.currentItem) {
                playVideo()
            }
        }

        fun release() {
            storyItem.release()
        }
    }

    internal inner class ViewPagerAdapter : RecyclerView.Adapter<PagerHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerHolder {
            return PagerHolder(StoryItemView(storiesView))
        }

        override fun onBindViewHolder(holder: PagerHolder, position: Int) {
            holder.bind(story?.getSlide(position), position)
        }

        override fun getItemCount(): Int {
            return story?.slidesCount ?: 0
        }
    }

    override fun onWindowFocusChanged(state: Boolean) {
        super.onWindowFocusChanged(state)
        //Когда окно сворачивается, нужно остановить видео и прогресс
        if (storiesStarted && prevFocusState != state) {
            prevFocusState = state
            if (state) {
                resume()
            } else {
                pause()
            }
        }
    }

    fun updateDurations() {
        story?.apply {
            val slidesCount = slidesCount
            val durations = LongArray(slidesCount)
            for (i in 0 until slidesCount) {
                durations[i] = getSlide(i).duration
            }
            storiesProgressView.setStoriesCountWithDurations(durations)
        }
    }

    private val currentHolder: PagerHolder?
        get() = getHolder(mViewPager.currentItem)

    private fun getHolder(position: Int): PagerHolder? {
        return holders[position]
    }

    fun pause() {
        storiesProgressView.pause()
        com.personalizatio.stories.Player.player?.pause()
    }

    fun resume() {
        val slide = story?.getSlide(mViewPager.currentItem)

        slide?.apply {
            if (!locked && isPrepared) {
                storiesProgressView.resume()
                val player = com.personalizatio.stories.Player.player
                player?.apply {
                    if (type == "video" && !isLoading && !isPlaying) {
                        this.play()
                    }
                }
            }
        }
    }

    override fun onNext() {
        story?.apply {
            var startPosition = startPosition
            if (startPosition + 1 >= slidesCount) {
                onComplete()
                return
            }
            startPosition++
            this.startPosition = startPosition
            mViewPager.setCurrentItem(startPosition, false)
            storiesProgressView.startStories(startPosition)
        }
    }

    override fun onStart(position: Int) {
        //Для текущего слайда
        if (position == mViewPager.currentItem) {
            val slide = story?.getSlide(position)
            //Если видео еще подгружается, приостанавливаем таймер анимации
            if (slide?.isPrepared != true) {
                storiesProgressView.pause()
            }
        }
    }

    override fun onPrev() {
        story?.apply {
            var startPosition = startPosition
            if (startPosition <= 0) {
                prevStoryListener!!.run()
                return
            }
            startPosition--
            this.startPosition = startPosition
            mViewPager.setCurrentItem(startPosition, false)
            storiesProgressView.startStories(startPosition)
        }
    }

    override fun onComplete() {
        story?.apply {
            isViewed = true
            startPosition = 0
        }
        updateDurations()
        completeListener?.run()
    }

    fun updateDuration(position: Int) {
        storiesProgressView.updateStoryDuration(position, story?.getSlide(position)?.duration ?: 0)
    }

    fun startStories() {
        story?.apply {
            val startPosition = startPosition

            if (!storiesStarted) {
                storiesStarted = true
                playVideo()
                SDK.track_story("view", storiesView.code, id, getSlide(startPosition).id)
            }

            if (!locked) {
                updateDurations()
                storiesProgressView.startStories(startPosition)
                mViewPager.setCurrentItem(startPosition, false)
            }
        }
    }

    fun stopStories() {
        storiesStarted = false
        //Если кампания сториса не активна на экране, удаляем листенеры
        com.personalizatio.stories.Player.player?.removeListener(this)
        pause()
        storiesProgressView.destroy()
        val holder = currentHolder
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
        com.personalizatio.stories.Player.player?.removeListener(this)
        com.personalizatio.stories.Player.player?.stop()
        val slidesCount = story?.slidesCount ?: 0
        for (i in 0 until slidesCount) {
            val holder = getHolder(i)
            holder?.release()
        }
    }

    companion object {
        private const val LIMIT = 500L
    }
}
