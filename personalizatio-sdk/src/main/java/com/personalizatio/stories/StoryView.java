package com.personalizatio.stories;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.Slide;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.personalizatio.R;
import com.personalizatio.SDK;

import java.util.Timer;
import java.util.TimerTask;

final class StoryView extends ConstraintLayout implements StoriesProgressView.StoriesListener {

	private Story story;
	private final String code;

	long pressTime = 0L;
	private final static long LIMIT = 500L;
	private Runnable completeListener;
	private Runnable prevStoryListener;

	public final StoriesProgressView storiesProgressView;
	public final ViewPager2 mViewPager;
	private boolean storiesStarted = false;
	private boolean prevFocusState = true;

	//Heading
	private final TextView titleTextView, subtitleTextView;
	private final CardView titleCardView;
	private final ImageView titleIconImageView;
	private final ConstraintLayout header;
	private final Button button;
	private final Button button_products;
	private final ViewGroup elements_layout;
	private RecyclerView products;
	private ProductsAdapter products_adapter;
	private final StoryDialog.OnProgressState state_listener;

	public StoryView(Context context, String code, Settings settings, StoryDialog.OnProgressState state_listener) {
		super(context);
		this.code = code;
		this.state_listener = state_listener;

		inflate(getContext(), R.layout.story_view, this);
		setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		View prev = findViewById(R.id.reverse);
		View next = findViewById(R.id.skip);
		OnTouchListener onTouchListener = new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if( storiesStarted ) {
					switch( event.getAction() ) {
						case MotionEvent.ACTION_DOWN:
							pressTime = System.currentTimeMillis();
							pause();
							setHeadingVisibility(GONE);
							return false;
						case MotionEvent.ACTION_UP:
							long now = System.currentTimeMillis();
							resume();
							setHeadingVisibility(VISIBLE);
							return LIMIT < now - pressTime;
					}
				}
				return false;
			}
		};
		prev.setOnTouchListener(onTouchListener);
		next.setOnTouchListener(onTouchListener);
		prev.setOnClickListener((View) -> previousStory());
		next.setOnClickListener((View) -> nextStory());
		storiesProgressView = findViewById(R.id.storiesProgressView);
		storiesProgressView.setColor(Color.parseColor(settings.background_progress));
		mViewPager = findViewById(R.id.storiesViewPager);
		titleTextView = findViewById(R.id.title_textView);
		subtitleTextView = findViewById(R.id.subtitle_textView);
		titleIconImageView = findViewById(R.id.title_imageView);
		titleCardView = findViewById(R.id.titleCardView);
		header = findViewById(R.id.header);
		button = findViewById(android.R.id.button1);
		button_products = findViewById(android.R.id.button2);
		elements_layout = findViewById(R.id.elements_layout);
		storiesProgressView.setStoriesListener(this);
		mViewPager.setOnTouchListener((v, event) -> true);
		mViewPager.setClipToPadding(false);
		mViewPager.setClipChildren(false);
		mViewPager.setOffscreenPageLimit(1);
		mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				//При изменении слайда останавливаем видео на скрытых
				for( int i = 0; i < story.slides.size(); i++ ) {
					PagerHolder holder = getHolder(i);
					if( holder != null ) {
						if( i == position ) {
							holder.playVideo();
						} else {
							holder.pauseVideo();
						}
					}
				}
				if( storiesStarted ) {
					SDK.track_story("view", code, story.id, story.slides.get(position).id);
				}
			}
		});

		products_adapter = new ProductsAdapter();
		products = findViewById(android.R.id.list);
		products.setAdapter(products_adapter);
	}

	public void setStory(Story story, Runnable completeListener, Runnable prevStoryListener) {
		this.story = story;
		this.completeListener = completeListener;
		this.prevStoryListener = prevStoryListener;

		storiesProgressView.setStoriesCount(story.slides.size());
		mViewPager.setAdapter(new ViewPagerAdapter());
		//Хак, чтобы не срабатывал onPageSelected при открытии первой кампании
		mViewPager.setCurrentItem(story.start_position == 0 ? story.slides.size() : 0, false);
		//Устанавливаем позицию
		mViewPager.setCurrentItem(story.start_position, false);

		updateElements();
	}

	class PagerHolder extends RecyclerView.ViewHolder {
		public StoryItemView story_item;
		private Timer video_seek_timer;

		public PagerHolder(@NonNull View view) {
			super(view);
			story_item = (StoryItemView) view;
		}

		public void bind(Story.Slide slide, int position) {
			slide.prepared = false;
			//Загуражем картинку
			if( slide.type.equals("image") ) {
				story_item.loadImage(slide.background, new RequestListener<Drawable>() {
					@Override
					public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
						nextStory();
						return false;
					}

					@Override
					public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
						slide.prepared = true;
						return false;
					}
				});
			}

			//Загружаем видео
			if( slide.type.equals("video") ) {
				//Загружаем превью
				if( slide.preview != null ) {
					story_item.loadImage(slide.preview, new RequestListener<Drawable>() {
						@Override
						public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
							return false;
						}

						@Override
						public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
							return false;
						}
					});
				}

				//Подготавливаем видео
				int story_id = story.id;
				story_item.loadVideo(slide.background, mediaPlayer -> {
					slide.duration = mediaPlayer.getDuration() + 1;
					slide.prepared = true;
					//Добавляем проверку, что объект story не изменился пока шла загрузка видео
					if( story.id == story_id ) {
						updateDuration(position);
						mediaPlayer.setOnCompletionListener(mp -> {
							if( video_seek_timer != null ) {
								video_seek_timer.cancel();
								video_seek_timer = null;
							}
							if( position == story.start_position && !button_products.isActivated() ) {
								nextStory();
							}
						});
						if( storiesStarted && position == story.start_position && !button_products.isActivated() ) {
							mediaPlayer.start();
						}
					}
				}, (mediaPlayer, i, i1) -> {
					Log.w(SDK.TAG, "Video " + slide.background + " load failed: (" + i + ", " + i1 + ")");
					if( position == story.start_position && !button_products.isActivated() ) {
						nextStory();
					}
					return false;
				});
			}
		}

		public void playVideo() {
			if( video_seek_timer != null ) {
				video_seek_timer.cancel();
				video_seek_timer = null;
			}
			Runnable runnable = () -> {
				try {
					if( story_item.mediaPlayer != null && storiesStarted && !button_products.isActivated() ) {
						story_item.mediaPlayer.seekTo(0);
						story_item.mediaPlayer.start();
						storiesProgressView.startStories(story.start_position);
						//Отключаем автоматическиц прогресс для видео
						PausableProgressBar bar = storiesProgressView.progressBars.get(story.start_position);
						bar.animation.setAnimationListener(null);
						bar.animation.pause();
						bar.frontProgressView.setScaleX(0f);
						bar.frontProgressView.setPivotX(0);
						video_seek_timer = new Timer();
						video_seek_timer.schedule(new TimerTask() {
							@Override
							public void run() {
								try {
									if( story_item.mediaPlayer.isPlaying() && story_item.mediaPlayer.getDuration() > 0 ) {
										bar.frontProgressView.setScaleX((float) story_item.mediaPlayer.getCurrentPosition() / story_item.mediaPlayer.getDuration());
									}
								} catch(IllegalStateException e) {
								}
							}
						}, 0, 50);
					}
				} catch(IllegalStateException e) {
				}
			};
			//Если только открываем нужный слайд с видео, там медиа плеера может не быть
			if( story_item.mediaPlayer == null ) {
				story_item.setOnReadyToStart(runnable);
			} else {
				runnable.run();
			}
		}

		public void pauseVideo() {
			story_item.setOnReadyToStart(null);
			if( video_seek_timer != null ) {
				video_seek_timer.cancel();
				video_seek_timer = null;
			}
			try {
				if( story_item.mediaPlayer != null && story_item.mediaPlayer.isPlaying() ) {
					story_item.mediaPlayer.pause();
				}
			} catch(IllegalStateException e) {
			}
		}

		public void release() {
			story_item.release();
		}
	}

	class ViewPagerAdapter extends RecyclerView.Adapter<PagerHolder> {

		@NonNull
		@Override
		public PagerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new PagerHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.story_item, parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull PagerHolder holder, int position) {
			holder.bind(story.slides.get(position), position);
		}

		@Override
		public int getItemCount() {
			return story.slides.size();
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
		long[] durations = new long[story.slides.size()];
		for( int i = 0; i < story.slides.size(); i++ ) {
			durations[i] = story.slides.get(i).duration;
		}
		storiesProgressView.setStoriesCountWithDurations(durations);
	}

	private void updateElements() {
		Story.Slide slide = story.slides.get(story.start_position);

		//Скрываем все элементы
		header.setVisibility(GONE);
		button.setVisibility(GONE);
		button_products.setVisibility(GONE);
		products.setVisibility(GONE);

		//Отображаем необходимые элементы
		for( Story.Slide.Element element : slide.elements ) {
			switch( element.type ) {
				case "header":
					updateHeader(element, slide.id);
					break;
				case "button":
					button.setVisibility(VISIBLE);
					button.setText(element.title);
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
						button.setBackgroundTintList(ColorStateList.valueOf(element.background == null ? button.getContext().getResources().getColor(R.color.primary) : Color.parseColor(element.background)));
					} else {
						button.setBackgroundColor(element.background == null ? button.getContext().getResources().getColor(R.color.primary) : Color.parseColor(element.background));
					}
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
						button.setTextColor(ColorStateList.valueOf(element.color == null ? button.getContext().getResources().getColor(R.color.white) : Color.parseColor(element.color)));
					} else {
						button.setTextColor(element.color == null ? button.getContext().getResources().getColor(R.color.white) : Color.parseColor(element.color));
					}
					button.setTypeface(Typeface.defaultFromStyle(element.text_bold ? Typeface.BOLD : Typeface.NORMAL));
					button.setOnClickListener(view -> {
						try {
							Log.d(SDK.TAG, "open link: " + element.link);
							getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(element.link)));
							SDK.track_story("click", code, story.id, slide.id);
						} catch(ActivityNotFoundException | NullPointerException e) {
							Log.e(SDK.TAG, e.getMessage(), e);
							Toast.makeText(getContext(), "Unknown error", Toast.LENGTH_SHORT).show();
						}
					});
					break;
				case "products":
					products_adapter.setProducts(element.products, code, story.id, slide.id);
					button_products.setVisibility(VISIBLE);
					button_products.setText(element.label_show);
					button_products.setOnClickListener(view -> {
						button_products.setActivated(!button_products.isActivated());
						button_products.setText(button_products.isActivated() ? element.label_hide : element.label_show);

						//Анимация появления товаров
						ConstraintSet set = new ConstraintSet();
						set.clone((ConstraintLayout) elements_layout);

						Transition transition = new ChangeBounds();
						transition.addTarget(button_products.getId());
						transition.addTarget(button.getId());

						Transition transition2 = new Slide(Gravity.BOTTOM);
						transition2.addTarget(products.getId());

						TransitionSet transitions = new TransitionSet();
						transitions.addTransition(transition);
						transitions.addTransition(transition2);

						TransitionManager.beginDelayedTransition(elements_layout, transitions);
						products.setVisibility(button_products.isActivated() ? VISIBLE : GONE);
						//--->

						state_listener.onState(!button_products.isActivated());
						if( button_products.isActivated() ) {
							pause();
						} else {
							resume();
						}
					});
					break;
			}
		}
	}

	private void updateHeader(Story.Slide.Element element, int slide_id) {
		if( element.type.equals("header") ) {
			header.setVisibility(VISIBLE);
			header.setOnTouchListener((View v, MotionEvent event) -> {
				if( event.getAction() == MotionEvent.ACTION_UP ) {
					getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(element.link)));
					SDK.track_story("click", code, story.id, slide_id);
				}
				return true;
			});

			if( element.icon != null ) {
				titleCardView.setVisibility(VISIBLE);
				if( getContext() == null ) return;
				Glide.with(getContext()).load(element.icon).into(titleIconImageView);
			} else {
				titleCardView.setVisibility(GONE);
			}

			if( element.title != null ) {
				titleTextView.setVisibility(VISIBLE);
				titleTextView.setText(element.title);
			} else {
				titleTextView.setVisibility(GONE);
			}

			if( element.subtitle != null ) {
				subtitleTextView.setVisibility(VISIBLE);
				subtitleTextView.setText(element.subtitle);
			} else {
				subtitleTextView.setVisibility(GONE);
			}
		}
	}

	private PagerHolder getCurrentHolder() {
		return getHolder(mViewPager.getCurrentItem());
	}

	private PagerHolder getHolder(int position) {
		return (PagerHolder) ((RecyclerView) mViewPager.getChildAt(0)).findViewHolderForAdapterPosition(position);
	}

	public void pause() {
		storiesProgressView.pause();
		PagerHolder holder = getCurrentHolder();
		if( holder != null ) {
			holder.story_item.setOnReadyToStart(null);
			if( holder.story_item.mediaPlayer != null ) {
				try {
					holder.story_item.mediaPlayer.pause();
				} catch(IllegalStateException e) {
				}
			}
		}
	}

	public void resume() {
		if( !button_products.isActivated() && story.slides.get(mViewPager.getCurrentItem()).prepared ) {
			storiesProgressView.resume();
			PagerHolder holder = getCurrentHolder();
			if( holder != null && holder.story_item.mediaPlayer != null ) {
				try {
					holder.story_item.mediaPlayer.start();
				} catch(IllegalStateException e) {
				}
			}
		}
	}

	public void setHeadingVisibility(int visibility) {
		elements_layout.animate().alpha(visibility == GONE ? 0 : 1).setStartDelay(visibility == GONE ? 100 : 0).setDuration(200);
	}

	@Override
	public void onNext() {
		if( story.start_position + 1 >= story.slides.size() ) {
			onComplete();
			return;
		}
		mViewPager.setCurrentItem(++story.start_position, false);
		storiesProgressView.startStories(story.start_position);
		updateElements();
	}

	@Override
	public void onStart(int position) {
		//Для текущего слайда
		if( position == mViewPager.getCurrentItem() ) {
			Story.Slide slide = story.slides.get(position);
			//Если видео еще подгружается, приостанавливаем таймер анимации
			if( !slide.prepared && slide.type.equals("video") ) {
				storiesProgressView.pause();
			}
		}
	}

	@Override
	public void onPrev() {
		if( story.start_position <= 0 ) {
			prevStoryListener.run();
			return;
		}
		mViewPager.setCurrentItem(--story.start_position, false);
		storiesProgressView.startStories(story.start_position);
		updateElements();
	}

	@Override
	public void onComplete() {
		story.viewed = true;
		story.start_position = 0;
		updateDurations();
		completeListener.run();
	}

	public void updateDuration(int position) {
		storiesProgressView.updateStoryDuration(position, story.slides.get(position).duration);
		//Получили длинну видео, значит подгрузилось, можно продолжать анимацию
		if( position == mViewPager.getCurrentItem() && storiesStarted ) {
			PagerHolder holder = getCurrentHolder();
			if( holder != null ) {
				holder.playVideo();
			}
		}
	}

	public void startStories() {
		storiesStarted = true;
		updateDurations();
		storiesProgressView.startStories(story.start_position);
		mViewPager.setCurrentItem(story.start_position, false);
		updateElements();
		PagerHolder holder = getCurrentHolder();
		if( holder != null ) {
			SDK.track_story("view", code, story.id, story.slides.get(story.start_position).id);
			holder.playVideo();
		}
	}

	public void stopStories() {
		storiesStarted = false;
		updateDurations();
		PagerHolder holder = getCurrentHolder();
		if( holder != null ) {
			holder.pauseVideo();
			holder.release();
		}
	}

	private void previousStory() {
		if( storiesStarted && !button_products.isActivated() ) {
			updateDurations();
			onPrev();
		}
	}

	public void nextStory() {
		if( storiesStarted && !button_products.isActivated() ) {
			onNext();
		}
	}

	public void release() {
		for( int i = 0; i < story.slides.size(); i++ ) {
			PagerHolder holder = getHolder(i);
			if( holder != null ) {
				holder.release();
			}
		}
	}
}
