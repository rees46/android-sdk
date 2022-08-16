package com.personalizatio.stories;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
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
import com.personalizatio.R;
import com.personalizatio.SDK;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class StoriesView extends ConstraintLayout implements StoriesAdapter.ClickListener {

	private StoriesAdapter adapter;
	private final ArrayList<Story> list = new ArrayList<>();
	private StoryView story_view;
	private String code;

	public StoriesView(Context context, String code) {
		super(context);
		this.code = code;
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

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		initialize();
	}

	private void parseAttrs(AttributeSet attrs) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.StoriesView);
		code = typedArray.getString(R.styleable.StoriesView_code);
	}

	//Инициализация
	private void initialize() {
		View view = inflate(getContext(), R.layout.stories, this);
		RecyclerView stories = view.findViewById(R.id.stories);

		adapter = new StoriesAdapter(list, this);
		stories.setAdapter(adapter);

		Handler handler = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				adapter.notifyDataSetChanged();
			}
		};

		//Запрашиваем сторисы
		SDK.stories(code, new Api.OnApiCallbackListener() {
			@Override
			public void onSuccess(JSONObject response) {
				Log.d("stories", response.toString());
				try {
					JSONArray json_stories = response.getJSONArray("stories");
					for( int i = 0; i < json_stories.length(); i++ ) {
						list.add(new Story(json_stories.getJSONObject(i)));
					}
					handler.sendEmptyMessage(1);
				} catch(JSONException e) {
					Log.e(SDK.TAG, e.getMessage(), e);
				}
			}
		});
	}

	@Override
	public void onStoryClick(int index) {
		Story story = list.get(index);

		//Сбрасываем позицию
		if( story.start_position >= story.slides.size() || story.start_position < 0 ) {
			story.start_position = 0;
		}

		StoryView dialog = new StoryView(getContext(), code, story, () -> {
			adapter.notifyDataSetChanged();
			if( index + 1 < list.size() ) {
				onStoryClick(index + 1);
			}
		}, () -> {
			if( index - 1 >= 0 ) {
				list.get(index - 1).start_position = list.get(index - 1).slides.size() - 1;
				onStoryClick(index - 1);
			}
		});
		dialog.show();
	}
}
