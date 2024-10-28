package com.personalization.stories.views

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.personalization.OnLinkClickListener
import com.personalization.R
import com.personalization.SDK
import com.personalization.stories.Player
import com.personalization.stories.Settings
import com.personalization.stories.models.Story
import com.personalization.stories.viewAdapters.StoriesAdapter
import com.personalization.stories.viewAdapters.StoriesAdapter.ClickListener

class StoriesView : ConstraintLayout, ClickListener {
    private lateinit var storiesRecyclerView: RecyclerView
    private lateinit var adapter: StoriesAdapter
    private lateinit var player: Player

    lateinit var code: String
    private var needOpeningWebView: Boolean = true

    private val stories: MutableList<Story> = ArrayList()
    private var observer: ContentObserver? = null

    val settings: Settings = Settings()
    var itemClickListener: OnLinkClickListener? = null
    var isMute: Boolean = true
        private set
    var muteListener: Runnable? = null

    constructor(
        context: Context?,
        code: String,
        needOpeningWebView: Boolean
    ) : super(context!!) {
        this.code = code
        this.needOpeningWebView = needOpeningWebView
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        parseAttrs(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!,
        attrs,
        defStyleAttr
    ) {
        parseAttrs(attrs)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context!!, attrs, defStyleAttr, defStyleRes) {
        parseAttrs(attrs)
    }

    init {
        inflate(context, R.layout.stories, this)

        initViews()
        setupViews()
    }

    private fun initViews() {
        storiesRecyclerView = findViewById(R.id.stories)
    }

    private fun setupViews() {
        adapter = StoriesAdapter(this, stories, this)
        storiesRecyclerView.adapter = adapter

        player = Player(context)

        settings.failed_load_text = resources.getString(R.string.failed_load_text)
    }

    private fun parseAttrs(attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.StoriesView)
        val code = typedArray.getString(R.styleable.StoriesView_code)
        val openingWebView = typedArray.getBoolean(R.styleable.StoriesView_need_opening_web_view, true)
        if (code == null) {
            SDK.error("Code is set incorrectly")
            return
        }
        this.code = code
        this.needOpeningWebView = openingWebView
    }

    @SuppressLint("NotifyDataSetChanged")
    internal fun updateStories(stories: List<Story>) {
        this.stories.clear()
        this.stories.addAll(stories)

        Handler(context.mainLooper).post {
            registerObserver()
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * Called when the story object has been removed from the screen and is no longer needed
     */
    fun release() {
        player.release()
    }

    internal fun preparePlayer(url: String) {
        player.prepare(url)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterObserver()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onStoryClick(id: Int) {
        val story = stories[id]

        if (story.startPosition >= story.slidesCount || story.startPosition < 0) {
            story.startPosition = 0
        }

        showStories(
            stories = stories,
            startPosition = id,
            completeShowStory = { adapter.notifyDataSetChanged() },
        )
    }

    fun muteVideo(mute: Boolean) {
        this.isMute = mute
    }

    internal fun showStories(
        stories: List<Story>,
        startPosition: Int,
        completeShowStory: () -> Unit = { },
        cancelShowStory: () -> Unit = { }
    ) {
        val dialog = StoryDialog(
            storiesView = this,
            stories = stories,
            startPosition = startPosition,
            completeShowStory = completeShowStory,
            cancelShowStory = cancelShowStory,
            needOpeningWebView = needOpeningWebView
        )
        dialog.show()
    }

    private fun registerObserver() {
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    isMute = manager.isStreamMute(AudioManager.STREAM_MUSIC)
                    muteListener?.run()
                }
            }
        }
        context.contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI,
            true,
            observer as ContentObserver
        )
    }

    private fun unregisterObserver() {
        if (observer != null) {
            context.contentResolver.unregisterContentObserver(observer!!)
        }
    }
}
