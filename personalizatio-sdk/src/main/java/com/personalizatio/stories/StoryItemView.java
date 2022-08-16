package com.personalizatio.stories;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;

class StoryItemView extends ConstraintLayout {

	private ImageView image;
	private VideoView video;
	public MediaPlayer mediaPlayer;
	private Runnable onReadyToStart;

	public StoryItemView(@NonNull Context context) {
		super(context);
	}

	public StoryItemView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public StoryItemView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public StoryItemView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		init();
	}

	private void init() {
		image = findViewById(android.R.id.background);
		video = findViewById(android.R.id.widget_frame);
		image.setVisibility(View.GONE);
		video.setVisibility(View.GONE);
	}

	/**
	 * Загружает изображение слайда
	 * @param url String
	 * @param listener RequestListener<Drawable>
	 */
	public void loadImage(String url, RequestListener<Drawable> listener) {
		image.setVisibility(View.VISIBLE);
		Glide.with(getContext()).load(url).listener(listener).into(image);
	}

	/**
	 * Загружает видео слайда
	 * @param url String
	 * @param onPreparedListener Listener
	 * @param onErrorListener Listener
	 */
	public void loadVideo(String url, MediaPlayer.OnPreparedListener onPreparedListener, MediaPlayer.OnErrorListener onErrorListener) {
		video.setVisibility(View.VISIBLE);
		video.setVideoURI(Uri.parse(url));
		video.setOnPreparedListener(mp -> {
			mediaPlayer = mp;
			onPreparedListener.onPrepared(mp);
			if( onReadyToStart != null ) {
				onReadyToStart.run();
			}
		});
		video.setOnErrorListener(onErrorListener);
	}

	public void setOnReadyToStart(Runnable runnable) {
		onReadyToStart = runnable;
	}
}
