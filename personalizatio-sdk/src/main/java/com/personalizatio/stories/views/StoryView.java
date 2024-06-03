package com.personalizatio.stories.views;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.personalizatio.R;
import com.personalizatio.SDK;
import com.personalizatio.stories.models.Slide;
import com.personalizatio.stories.models.Story;
import com.personalizatio.stories.views.storyItem.StoryItemView;

import java.util.HashMap;

@SuppressLint("ViewConstructor")
final class StoryView extends ConstraintLayout implements StoriesProgressView.StoriesListener, Player.Listener {

	private final StoriesView storiesView;
	private Story story;

	private long pressTime = 0L;
	private final static long LIMIT = 500L;
	private Runnable completeListener;
	private Runnable prevStoryListener;

	private StoriesProgressView storiesProgressView;
	private OnTouchListener onTouchListener;
	private ViewPager2 mViewPager;
	private boolean storiesStarted = false;
	private boolean prevFocusState = true;
	private boolean locked = false;
	private final HashMap<Integer, PagerHolder> holders = new HashMap<>();
	private ToggleButton mute;

	//Heading
	private final StoryDialog.OnProgressState stateListener;

	private Pair<Integer, Integer> viewPagerSize = null;

	@SuppressLint("ClickableViewAccessibility")
	public StoryView(StoriesView storiesView, StoryDialog.OnProgressState stateListener) {
		super(storiesView.getContext());
		this.storiesView = storiesView;
		this.stateListener = stateListener;

		inflate(getContext(), R.layout.story_view, this);
		setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		initViews();
		setupViews();
	}

	private void initViews() {
		storiesProgressView = findViewById(R.id.storiesProgressView);

		mViewPager = findViewById(R.id.storiesViewPager);

		mute = findViewById(R.id.mute);
	}

	@SuppressLint("ClickableViewAccessibility")
	private void setupViews() {
		onTouchListener = (v, event) -> {
			if( storiesStarted ) {
				switch( event.getAction() ) {
					case MotionEvent.ACTION_DOWN:
						pressTime = System.currentTimeMillis();
						pause();
//							setHeadingVisibility(GONE);
						return false;
					case MotionEvent.ACTION_UP:
						long now = System.currentTimeMillis();
						if( LIMIT < now - pressTime ) {
							resume();
						}
//							setHeadingVisibility(VISIBLE);
						return LIMIT < now - pressTime;
				}
			}
			return false;
		};

		storiesProgressView.setColor(Color.parseColor(storiesView.getSettings().background_progress));
		storiesProgressView.setStoriesListener(this);

		mViewPager.setClipToPadding(false);
		mViewPager.setClipChildren(false);
		mViewPager.setOffscreenPageLimit(1);
		mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				var player = storiesView.getPlayer().getPlayer();
				if( player.isPlaying() || player.isLoading() ) {
					player.pause();
				}
				//При изменении слайда останавливаем видео на скрытых
				for( int i = 0; i < story.getSlidesCount(); i++ ) {
					PagerHolder holder = getHolder(i);
					if( holder != null ) {
						if( i != position ) {
							holder.release();
						}
					}
				}
				if( storiesStarted ) {
					SDK.track_story("view", storiesView.getCode(), story.getId(), story.getSlide(position).getId());
					playVideo();
				}
			}
		});

		storiesView.getPlayer().getPlayer().setVolume(storiesView.isMute() ? 0f : 1f);
		storiesView.setMuteListener(() -> {
			storiesView.getPlayer().getPlayer().setVolume(1f);
		});

		//Управление звуком
		mute.setOnClickListener(v -> {
			storiesView.muteVideo(mute.isChecked());
			storiesView.getPlayer().getPlayer().setVolume(mute.isChecked() ? 0f : 1f);
		});
		mute.setChecked(storiesView.isMute());
	}

	public void setStory(Story story, Runnable completeListener, Runnable prevStoryListener) {
		this.story = story;
		this.completeListener = completeListener;
		this.prevStoryListener = prevStoryListener;

		var slidesCount = story.getSlidesCount();
		storiesProgressView.setStoriesCount(slidesCount);
		mViewPager.setAdapter(new ViewPagerAdapter());
		//Хак, чтобы не срабатывал onPageSelected при открытии первой кампании
		mViewPager.setCurrentItem(story.getStartPosition() == 0 ? slidesCount : 0, false);
		//Устанавливаем позицию
		mViewPager.setCurrentItem(story.getStartPosition(), false);
	}

	@UnstableApi
	private void playVideo() {
		if( storiesStarted ) {
			PagerHolder holder = getHolder(mViewPager.getCurrentItem());
			if( holder != null ) {
				Slide slide = story.getSlide(mViewPager.getCurrentItem());
				if( slide.getType().equals("video") ) {
					slide.setPrepared(false);
					//Подготавливаем плеер
					var player = storiesView.getPlayer().getPlayer();
					player.addListener(this);
					holder.getStoryItem().getVideo().setVisibility(GONE);
					holder.getStoryItem().getImage().setAlpha(1f);
					holder.getStoryItem().getVideo().setPlayer(player);
					storiesView.getPlayer().prepare(slide.getBackground());
					storiesProgressView.pause();
					mute.setChecked(storiesView.isMute());
				}
			}
		}
	}

	@Override
	public void onPlaybackStateChanged(@Player.State int playbackState) {
		switch( playbackState ) {
			case Player.STATE_BUFFERING:
				storiesProgressView.pause();
				break;
			case Player.STATE_READY:
				storiesView.getPlayer().getPlayer().play();
				break;
		}
	}

	@Override
	public void onIsPlayingChanged(boolean isPlaying) {
		if( isPlaying ) {
			PagerHolder holder = getHolder(mViewPager.getCurrentItem());
			holder.getStoryItem().getVideo().setVisibility(VISIBLE);
			holder.getStoryItem().getImage().animate().alpha(0).setDuration(300);
			story.getSlide(mViewPager.getCurrentItem()).setPrepared(true);
			resume();
		}
	}

	@Override
	public void onTracksChanged(@NonNull Tracks tracks) {
		var contentDuration = storiesView.getPlayer().getPlayer().getContentDuration();

		if(contentDuration > 0) {
			int currentItem = mViewPager.getCurrentItem();
			story.getSlide(currentItem).setDuration(contentDuration);
			updateDuration(currentItem);
		}

		mute.setVisibility(tracks.isTypeSupported(C.TRACK_TYPE_AUDIO) ? VISIBLE : GONE);
	}

	@Override
	public void onPlayerError(PlaybackException error) {
		Log.e(SDK.TAG, "player error: " + error.getMessage() + ", story: " + story.getId());
		PagerHolder holder = getCurrentHolder();
		holder.getStoryItem().reload_layout.setVisibility(VISIBLE);

		holder.getStoryItem().reload.setOnClickListener((View) -> {
			Slide slide = story.getSlide(mViewPager.getCurrentItem());
			if( slide.getType().equals("video") ) {
				holder.getStoryItem().reload_layout.setVisibility(GONE);
				playVideo();
			} else {
				holder.getStoryItem().update(slide, mViewPager.getCurrentItem(), storiesView.getCode(), story.getId());
			}
		});

		//Добавляем авто-обновление
		holder.getStoryItem().reload.postDelayed(() -> holder.getStoryItem().reload.callOnClick(), 15000L);
	}

	@Override
	public void onVolumeChanged(float volume) {
		mute.setChecked(volume == 0);
	}

	class PagerHolder extends RecyclerView.ViewHolder {
		private StoryItemView storyItem;

		public PagerHolder(@NonNull View view) {
			super(view);

			storyItem = (StoryItemView) view;
			if(viewPagerSize == null) {
				var viewPagerHeight = mViewPager.getHeight();
				var params = (ConstraintLayout.LayoutParams) storiesProgressView.getLayoutParams();
				var viewPagerTopOffset = params.height + params.bottomMargin + params.topMargin;
				viewPagerSize = new Pair<>(viewPagerHeight, viewPagerTopOffset);
			}
			storyItem.setViewSize(viewPagerSize.first, viewPagerSize.second);
			storyItem.setOnPageListener(new StoryItemView.OnPageListener() {
				@Override
				public void onPrev() {
					previousSlide();
				}

				@Override
				public void onNext() {
					nextSlide();
				}

				@Override
				public void onPrepared(int position) {
					if( story.getSlide(position).getType().equals("image") && storiesStarted ) {
						try {
							storiesProgressView.resume();
						} catch(IndexOutOfBoundsException e) {
						}
					}
				}

				@Override
				public void onLocked(boolean lock) {
					locked = lock;
					stateListener.onState(!lock);
					if( lock ) {
						pause();
					} else {
						resume();
					}
				}
			});
		}

		public void bind(Slide slide, int position) {
			holders.put(position, this);
			mute.setVisibility(GONE);
			storyItem.update(slide, position, storiesView.getCode(), story.getId());
			storyItem.setOnTouchListener(onTouchListener);

			//Устанавливаем загрузку видео, если биндим текущий элемент
			if( position == mViewPager.getCurrentItem() ) {
				playVideo();
			}
		}

		public void release() {
			storyItem.release();
		}

		public StoryItemView getStoryItem() {
			return storyItem;
		}
	}

	class ViewPagerAdapter extends RecyclerView.Adapter<PagerHolder> {

		@NonNull
		@Override
		public PagerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new PagerHolder(new StoryItemView(storiesView));
		}

		@Override
		public void onBindViewHolder(@NonNull PagerHolder holder, int position) {
			holder.bind(story.getSlide(position), position);
		}

		@Override
		public int getItemCount() {
			return story.getSlidesCount();
		}
	}

	@Override
	public void onWindowFocusChanged(boolean state) {
		super.onWindowFocusChanged(state);
		//Когда окно сворачивается, нужно остановить видео и прогресс
		if( storiesProgressView != null && storiesStarted && prevFocusState != state ) {
			prevFocusState = state;
			if( state ) {
				resume();
			} else {
				pause();
			}
		}
	}

	public void updateDurations() {
		var slidesCount = story.getSlidesCount();
		long[] durations = new long[slidesCount];
		for( int i = 0; i < slidesCount; i++ ) {
			durations[i] = story.getSlide(i).getDuration();
		}
		storiesProgressView.setStoriesCountWithDurations(durations);
	}

	private PagerHolder getCurrentHolder() {
		return getHolder(mViewPager.getCurrentItem());
	}

	private PagerHolder getHolder(int position) {
		return holders.get(position);
	}

	public void pause() {
		storiesProgressView.pause();
		storiesView.getPlayer().getPlayer().pause();
	}

	public void resume() {
		var slide = story.getSlide(mViewPager.getCurrentItem());

		if( !locked && slide.isPrepared() ) {
			storiesProgressView.resume();
			var player = storiesView.getPlayer().getPlayer();
			if(slide.getType().equals("video") && !player.isLoading() && !player.isPlaying() ) {
				player.play();
			}
		}
	}

	@Override
	public void onNext() {
		var startPosition = story.getStartPosition();
		if(startPosition + 1 >= story.getSlidesCount()) {
			onComplete();
			return;
		}
		startPosition++;
		story.setStartPosition(startPosition);
		mViewPager.setCurrentItem(startPosition, false);
		storiesProgressView.startStories(startPosition);
	}

	@Override
	public void onStart(int position) {
		//Для текущего слайда
		if( position == mViewPager.getCurrentItem() ) {
			Slide slide = story.getSlide(position);
			//Если видео еще подгружается, приостанавливаем таймер анимации
			if( !slide.isPrepared() ) {
				storiesProgressView.pause();
			}
		}
	}

	@Override
	public void onPrev() {
		var startPosition = story.getStartPosition();
		if( startPosition <= 0 ) {
			prevStoryListener.run();
			return;
		}
		startPosition--;
		story.setStartPosition(startPosition);
		mViewPager.setCurrentItem(startPosition, false);
		storiesProgressView.startStories(startPosition);
	}

	@Override
	public void onComplete() {
		story.setViewed(true);
		story.setStartPosition(0);
		updateDurations();
		completeListener.run();
	}

	public void updateDuration(int position) {
		storiesProgressView.updateStoryDuration(position, story.getSlide(position).getDuration());
	}

	public void startStories() {
		var startPosition = story.getStartPosition();

		if( !storiesStarted ) {
			storiesStarted = true;
			playVideo();
			SDK.track_story("view", storiesView.getCode(), story.getId(), story.getSlide(startPosition).getId());
		}

		if( !locked ) {
			updateDurations();
			storiesProgressView.startStories(startPosition);
			mViewPager.setCurrentItem(startPosition, false);
		}
	}

	public void stopStories() {
		storiesStarted = false;
		//Если кампания сториса не активна на экране, удаляем листенеры
		storiesView.getPlayer().getPlayer().removeListener(this);
		pause();
		storiesProgressView.destroy();
		PagerHolder holder = getCurrentHolder();
		if( holder != null ) {
			holder.release();
		}
	}

	private void previousSlide() {
		if( storiesStarted && !locked ) {
			updateDurations();
			onPrev();
		}
	}

	public void nextSlide() {
		if( storiesStarted && !locked ) {
			onNext();
		}
	}

	public void release() {
		storiesView.getPlayer().getPlayer().removeListener(this);
		storiesView.getPlayer().getPlayer().stop();
		for( int i = 0; i < story.getSlidesCount(); i++ ) {
			PagerHolder holder = getHolder(i);
			if( holder != null ) {
				holder.release();
			}
		}
	}
}
