package com.personalizatio.stories.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.personalizatio.Api;
import com.personalizatio.OnLinkClickListener;
import com.personalizatio.R;
import com.personalizatio.SDK;
import com.personalizatio.stories.Player;
import com.personalizatio.stories.Settings;
import com.personalizatio.stories.StoriesUtils;
import com.personalizatio.stories.viewAdapters.StoriesAdapter;
import com.personalizatio.stories.models.Story;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final public class StoriesView extends ConstraintLayout implements StoriesAdapter.ClickListener, SDK.ShowStoriesRequestListener {

	private StoriesAdapter adapter;
	private final List<Story> list = new ArrayList<>();
	private ContentObserver observer;
	private final Settings settings = new Settings();
	private String code;
	private Player player;
	@Nullable private OnLinkClickListener clickListener;
	private boolean mute = true;
	private Runnable muteListener;

	public StoriesView(Context context, String code) {
		super(context);
		this.setCode(code);
		initialize();
	}

	public StoriesView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		parseAttrs(attrs);
	}

	public StoriesView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		parseAttrs(attrs);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public StoriesView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		parseAttrs(attrs);
	}

	public Settings getSettings() {
		return settings;
	}

	public Player getPlayer() {
		return player;
	}

	public void setPlayer(Player player) {
		this.player = player;
	}

	/**
	 * Вызывать, когда объект сторисов удален с экрана и больше не нужен
	 */
	public void release() {
		getPlayer().release();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		unregisterObserver();
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		initialize();
	}

	private void parseAttrs(AttributeSet attrs) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.StoriesView);
		setCode(typedArray.getString(R.styleable.StoriesView_code));
	}

	//Инициализация
	private void initialize() {
		View view = inflate(getContext(), R.layout.stories, this);
		RecyclerView stories = view.findViewById(R.id.stories);

		adapter = new StoriesAdapter(this, list, this);
		stories.setAdapter(adapter);

		Handler handler = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				registerObserver();
				adapter.notifyDataSetChanged();
			}
		};

		//Плеер для просмотра видео
		setPlayer(new Player(getContext()));

		getSettings().failed_load_text = getResources().getString(R.string.failed_load_text);

		//Запрашиваем сторисы
		SDK.stories(getCode(), new Api.OnApiCallbackListener() {
			@Override
			public void onSuccess(JSONObject response) {
				Log.d("stories", response.toString());
				var stories = StoriesUtils.GetStories(response);

				if(!stories.isEmpty()) {
					handler.sendEmptyMessage(1);

					list.addAll(stories);
				}
			}
		});

		SDK.setShowStoriesRequestListener(this);
	}

    @Override
	public void onStoryClick(int index) {
		Story story = list.get(index);
		story.resetStartPosition();

		showStories(list, index, () -> adapter.notifyDataSetChanged());
	}

	/**
	 * Устанавливает слушатель клика по элементам
	 * @param listener OnLinkClickListener
	 */
	public void setOnLinkClickListener(@Nullable OnLinkClickListener listener) {
		this.setClickListener(listener);
	}

	public void muteVideo(boolean mute) {
		this.mute = mute;
	}

	public boolean isMute() {
		return mute;
	}

	public void registerObserver() {
		observer = new ContentObserver(new Handler()) {
			@Override
			public void onChange(boolean selfChange) {
				super.onChange(selfChange);
				AudioManager manager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
					mute = manager.isStreamMute(AudioManager.STREAM_MUSIC);
					if (muteListener != null) {
						muteListener.run();
					}
				}
			}
		};
		getContext().getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, observer);
	}

	public void unregisterObserver() {
		if( observer != null ) {
			getContext().getContentResolver().unregisterContentObserver(observer);
		}
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	@Nullable
	public OnLinkClickListener getClickListener() {
		return clickListener;
	}

	public void setClickListener(@Nullable OnLinkClickListener clickListener) {
		this.clickListener = clickListener;
	}

	public void setMuteListener(Runnable muteListener) {
		this.muteListener = muteListener;
	}

	@Override
	public void onShowStoriesRequest(List<Story> stories) {
		Handler handler = new Handler(getContext().getMainLooper());
		handler.post(() -> showStories(stories, 0, () -> {}));
	}

	private void showStories(List<Story> stories, int startPosition, Runnable completeListener) {
		StoryDialog dialog = new StoryDialog(this, stories, startPosition, completeListener);
		dialog.show();
	}
}
