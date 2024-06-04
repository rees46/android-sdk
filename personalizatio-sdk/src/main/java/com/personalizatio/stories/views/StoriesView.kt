package com.personalizatio.stories.views

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.personalizatio.Api.OnApiCallbackListener
import com.personalizatio.OnLinkClickListener
import com.personalizatio.R
import com.personalizatio.SDK
import com.personalizatio.stories.Player
import com.personalizatio.stories.Settings
import com.personalizatio.stories.models.Story
import com.personalizatio.stories.viewAdapters.StoriesAdapter
import com.personalizatio.stories.viewAdapters.StoriesAdapter.ClickListener
import org.json.JSONException
import org.json.JSONObject

class StoriesView : ConstraintLayout, ClickListener {
    private var adapter: StoriesAdapter? = null
    private val list: MutableList<Story> = ArrayList()
    private var observer: ContentObserver? = null
	val settings: Settings = Settings()
	var code: String? = null
	var player: Player? = null
    var clickListener: OnLinkClickListener? = null
    var isMute: Boolean = true
        private set
    var muteListener: Runnable? = null

    constructor(context: Context?, code: String?) : super(context!!) {
        this.code = code
        initialize()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        parseAttrs(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr) {
        parseAttrs(attrs)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
            : super(context!!, attrs, defStyleAttr, defStyleRes) {
        parseAttrs(attrs)
    }

    /**
     * Вызывать, когда объект сторисов удален с экрана и больше не нужен
     */
    fun release() {
        player?.release()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterObserver()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        initialize()
    }

    private fun parseAttrs(attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.StoriesView)
        this.code = typedArray.getString(R.styleable.StoriesView_code)
    }

    //Инициализация
    private fun initialize() {
        val view = inflate(context, R.layout.stories, this)
        val stories = view.findViewById<RecyclerView>(R.id.stories)

        adapter = StoriesAdapter(this, list, this)
        stories.adapter = adapter

        val handler: Handler = object : Handler(Looper.getMainLooper()) {
            @SuppressLint("NotifyDataSetChanged")
            override fun handleMessage(msg: Message) {
                registerObserver()
                adapter?.notifyDataSetChanged()
            }
        }

        //Плеер для просмотра видео
        player = Player(context)

        settings.failed_load_text = resources.getString(R.string.failed_load_text)

        //Запрашиваем сторисы
        this.code?.let {
            SDK.stories(it, object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    response?.let { response ->
                        Log.d("stories", response.toString())
                        try {
                            val jsonStories = response.getJSONArray("stories")
                            for (i in 0 until jsonStories.length()) {
                                list.add(Story(jsonStories.getJSONObject(i)))
                            }
                            handler.sendEmptyMessage(1)
                        } catch (e: JSONException) {
                            Log.e(SDK.TAG, e.message, e)
                        }
                    }
                }
            })
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onStoryClick(index: Int) {
        val story = list[index]

        //Сбрасываем позицию
        val startPosition = story.startPosition
        if (startPosition >= story.slidesCount || startPosition < 0) {
            story.startPosition = 0
        }

        val dialog = StoryDialog(this, list, index) {
            adapter?.notifyDataSetChanged()
        }
        dialog.show()
    }

    fun muteVideo(mute: Boolean) {
        this.isMute = mute
    }

    fun registerObserver() {
        observer = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    isMute = manager.isStreamMute(AudioManager.STREAM_MUSIC)
                    if (muteListener != null) {
                        muteListener!!.run()
                    }
                }
            }
        }
        context.contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI,
            true,
            observer as ContentObserver
        )
    }

    fun unregisterObserver() {
        if (observer != null) {
            context.contentResolver.unregisterContentObserver(observer!!)
        }
    }
}
