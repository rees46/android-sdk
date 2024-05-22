package com.personalizatio.stories

import android.annotation.TargetApi

class StoriesView : ConstraintLayout, StoriesAdapter.ClickListener {
    private var adapter: StoriesAdapter? = null
    private val list: ArrayList<Story?>? = ArrayList()
    private var observer: ContentObserver? = null
    val settings: Settings? = Settings()
    var code: String? = null
    var player: Player? = null

    @Nullable
    var click_listener: OnLinkClickListener? = null
    var mute: Boolean = true
    var mute_listener: Runnable? = null

    constructor(context: Context?, code: String?) : super(context) {
        this.code = code
        initialize()
    }

    constructor(context: Context?, @Nullable attrs: AttributeSet?) : super(context, attrs) {
        parseAttrs(attrs)
    }

    constructor(context: Context?, @Nullable attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        parseAttrs(attrs)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        parseAttrs(attrs)
    }

    /**
     * Вызывать, когда объект сторисов удален с экрана и больше не нужен
     */
    fun release() {
        player.release()
    }

    @Override
    protected fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterObserver()
    }

    @Override
    protected fun onFinishInflate() {
        super.onFinishInflate()
        initialize()
    }

    private fun parseAttrs(attrs: AttributeSet?) {
        val typedArray: TypedArray = getContext().obtainStyledAttributes(attrs, R.styleable.StoriesView)
        code = typedArray.getString(R.styleable.StoriesView_code)
    }

    //Инициализация
    private fun initialize() {
        val view: View = inflate(getContext(), R.layout.stories, this)
        val stories: RecyclerView = view.findViewById(R.id.stories)

        adapter = StoriesAdapter(this, list, this)
        stories.setAdapter(adapter)

        val handler: Handler = object : Handler(Looper.getMainLooper()) {
            @Override
            fun handleMessage(msg: Message?) {
                registerObserver()
                adapter.notifyDataSetChanged()
            }
        }

        //Плеер для просмотра видео
        player = Player(getContext())

        settings.failed_load_text = getResources().getString(R.string.failed_load_text)

        //Запрашиваем сторисы
        SDK.stories(code, object : OnApiCallbackListener() {
            @Override
            fun onSuccess(response: JSONObject?) {
                Log.d("stories", response.toString())
                try {
                    val json_stories: JSONArray = response.getJSONArray("stories")
                    for (i in 0 until json_stories.length()) {
                        list.add(Story(json_stories.getJSONObject(i)))
                    }
                    handler.sendEmptyMessage(1)
                } catch (e: JSONException) {
                    Log.e(SDK.TAG, e.getMessage(), e)
                }
            }
        })
    }

    @Override
    fun onStoryClick(index: Int) {
        val story: Story = list.get(index)

        //Сбрасываем позицию
        if (story.start_position >= story.slides.size() || story.start_position < 0) {
            story.start_position = 0
        }

        val dialog: StoryDialog = StoryDialog(this, list, index) {
            adapter.notifyDataSetChanged()
        }
        dialog.show()
    }

    /**
     * Устанавливает слушатель клика по элементам
     * @param listener OnLinkClickListener
     */
    fun setOnLinkClickListener(@Nullable listener: OnLinkClickListener?) {
        this.click_listener = listener
    }

    fun muteVideo(mute: Boolean) {
        this.mute = mute
    }

    fun isMute(): Boolean {
        return mute
    }

    fun registerObserver() {
        observer = object : ContentObserver(Handler()) {
            @Override
            fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                val manager: AudioManager = getContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mute = manager.isStreamMute(AudioManager.STREAM_MUSIC)
                    if (mute_listener != null) {
                        mute_listener.run()
                    }
                }
            }
        }
        getContext().getContentResolver()
            .registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, observer)
    }

    fun unregisterObserver() {
        if (observer != null) {
            getContext().getContentResolver().unregisterContentObserver(observer)
        }
    }
}
