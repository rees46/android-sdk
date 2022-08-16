package com.personalizatio.stories;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.personalizatio.R;
import com.personalizatio.SDK;
import com.personalizatio.stories.callback.StoryCallbacks;
import com.personalizatio.stories.utils.PaletteExtraction;

import java.util.ArrayList;


public class ViewPagerAdapter extends PagerAdapter {

	private final Story story;
	private final Context context;
	private final StoryCallbacks storyCallbacks;
	private boolean storiesStarted = false;

	public ViewPagerAdapter(Story story, Context context, StoryCallbacks storyCallbacks) {
		this.story = story;
		this.context = context;
		this.storyCallbacks = storyCallbacks;
	}

	@Override
	public int getCount() {
		return story.slides.size();
	}

	@Override
	public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
		return view == object;
	}

	public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
		if( story.slides.get(position).type.equals("video") ) {
			StoryItemView view = (StoryItemView) object;
			Runnable runnable = () -> {
				try {
					if( !view.mediaPlayer.isPlaying() ) {
						view.mediaPlayer.seekTo(0);
						view.mediaPlayer.start();
					}
				} catch(IllegalStateException e) {}
			};
			//Если только открываем нужный слайд с видео, там медиа плеера может не быть
			if( view.mediaPlayer == null ) {
				view.setOnReadyToStart(runnable);
			} else {
				runnable.run();
			}
		}
	}

	@NonNull
	@Override
	public Object instantiateItem(@NonNull ViewGroup collection, final int position) {
		LayoutInflater inflater = LayoutInflater.from(context);

		Story.Slide slide = story.slides.get(position);

		final StoryItemView view = (StoryItemView) inflater.inflate(R.layout.story_item, collection, false);

		//Загуражем картинку
		if( slide.type.equals("image") ) {
			view.loadImage(slide.background, new RequestListener<Drawable>() {
				@Override
				public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
					storyCallbacks.nextStory();
					return false;
				}

				@Override
				public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
					if( resource != null ) {
						PaletteExtraction pe = new PaletteExtraction(view.findViewById(R.id.relativeLayout), ((BitmapDrawable) resource).getBitmap());
						pe.execute();
					}
					if( !storiesStarted && position == story.start_position ) {
						storiesStarted = true;
						storyCallbacks.startStories();
					}
					return false;
				}
			});
		}

		//Загружаем видео
		if( slide.type.equals("video") ) {
			slide.prepared = false;
			view.loadVideo(slide.background, mediaPlayer -> {
				slide.duration = mediaPlayer.getDuration();
				slide.prepared = true;
				if( !storiesStarted && position == story.start_position ) {
					storiesStarted = true;
					storyCallbacks.startStories();
				} else {
					storyCallbacks.updateDuration(position);
				}
			}, (mediaPlayer, i, i1) -> {
				storyCallbacks.nextStory();
				return false;
			});
		}

		collection.addView(view);

		return view;
	}

	@Override
	public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
		if( story.slides.get(position).type.equals("video") ) {
			View view = (View) object;
			final VideoView video = view.findViewById(android.R.id.widget_frame);
			video.stopPlayback();
		}
		(container).removeView((View) object);
	}
}