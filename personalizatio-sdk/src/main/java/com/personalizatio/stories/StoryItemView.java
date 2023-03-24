package com.personalizatio.stories;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.personalizatio.SDK;

import java.io.IOException;

final class StoryItemView extends ConstraintLayout {

	public ImageView image;
	private ScalableVideoView video;
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

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
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
	 *
	 * @param url      String
	 * @param listener RequestListener<Drawable>
	 */
	public void loadImage(String url, RequestListener<Drawable> listener) {
		image.setVisibility(View.VISIBLE);
		Glide.with(getContext()).load(url).listener(listener).into(image);
	}

	/**
	 * Загружает видео слайда
	 *
	 * @param url                String
	 * @param onPreparedListener Listener
	 * @param onErrorListener    Listener
	 */
	public void loadVideo(String url, MediaPlayer.OnPreparedListener onPreparedListener, MediaPlayer.OnErrorListener onErrorListener) {
		video.setVisibility(View.VISIBLE);
		try {
			video.setDataSource(url);
			video.setOnErrorListener(onErrorListener);
			video.prepareAsync(mp -> {
				Log.d(SDK.TAG, url + " on prepared");
				mediaPlayer = mp;
				mp.start();
				mp.pause();
				onPreparedListener.onPrepared(mp);
				if( onReadyToStart != null ) {
					onReadyToStart.run();
				}
			});
		} catch(IOException e) {
			Log.w(SDK.TAG, e.getMessage());
		}
	}

	public void setOnReadyToStart(Runnable runnable) {
		onReadyToStart = runnable;
	}
}
