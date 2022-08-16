package com.personalizatio.stories;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.bumptech.glide.Glide;
import com.personalizatio.R;
import com.personalizatio.SDK;
import com.personalizatio.stories.callback.StoryCallbacks;


class StoryView extends Dialog implements StoriesProgressView.StoriesListener, StoryCallbacks, PullDismissLayout.Listener {

	private final Story story;
	private final String code;

	private StoriesProgressView storiesProgressView;

	private final Runnable completeListener;
	private final Runnable prevStoryListener;

	private ViewPager mViewPager;

	//Heading
	private TextView titleTextView, subtitleTextView;
	private CardView titleCardView;
	private ImageView titleIconImageView;
	private ConstraintLayout header;
	private Button button;
	private View elements_layout;

	public StoryView(Context context, String code, Story story, Runnable completeListener, Runnable prevStoryListener) {
		super(context, android.R.style.Theme_Translucent_NoTitleBar);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dialog_stories);
		Window window = getWindow();
		WindowManager.LayoutParams wlp = window.getAttributes();

		wlp.gravity = Gravity.CENTER;
		wlp.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
		window.setAttributes(wlp);
		window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

		this.story = story;
		this.code = code;
		this.completeListener = completeListener;
		this.prevStoryListener = prevStoryListener;

		setupViews();
		setupStories();
	}

	@Override
	public void onWindowFocusChanged(boolean state) {
		super.onWindowFocusChanged(state);
		if( state ) {
			storiesProgressView.resume();
		} else {
			storiesProgressView.pause();
		}
	}

	private void setupStories() {
		storiesProgressView.setStoriesCount(story.slides.size());
		updateDurations();
		updateElements();
		mViewPager.setAdapter(new ViewPagerAdapter(story, getContext(), this));
		mViewPager.setCurrentItem(story.start_position, false);
	}

	long pressTime = 0L;
	long limit = 500L;
	private final View.OnTouchListener onTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch( event.getAction() ) {
				case MotionEvent.ACTION_DOWN:
					pressTime = System.currentTimeMillis();
					storiesProgressView.pause();
					setHeadingVisibility(View.GONE);
					return false;
				case MotionEvent.ACTION_UP:
					long now = System.currentTimeMillis();
					storiesProgressView.resume();
					setHeadingVisibility(View.VISIBLE);
					return limit < now - pressTime;
			}
			return false;
		}
	};

	private void setupViews() {
		((PullDismissLayout) findViewById(R.id.pull_dismiss_layout)).setListener(this);
		View prev = findViewById(R.id.reverse);
		View next = findViewById(R.id.skip);
		prev.setOnTouchListener(onTouchListener);
		next.setOnTouchListener(onTouchListener);
		prev.setOnClickListener((View) -> previousStory());
		next.setOnClickListener((View) -> nextStory());
		storiesProgressView = findViewById(R.id.storiesProgressView);
		mViewPager = findViewById(R.id.storiesViewPager);
		titleTextView = findViewById(R.id.title_textView);
		subtitleTextView = findViewById(R.id.subtitle_textView);
		titleIconImageView = findViewById(R.id.title_imageView);
		titleCardView = findViewById(R.id.titleCardView);
		header = findViewById(R.id.header);
		button = findViewById(android.R.id.button1);
		elements_layout = findViewById(R.id.elements_layout);
		storiesProgressView.setStoriesListener(this);
		mViewPager.setOnTouchListener((v, event) -> true);

		ImageButton closeImageButton = findViewById(R.id.imageButton);
		closeImageButton.setOnClickListener(v -> {
			onDismissed();
		});

		mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
				SDK.track_story("view", code, story.id, story.slides.get(position).id);
			}

			@Override
			public void onPageSelected(int position) {
			}

			@Override
			public void onPageScrollStateChanged(int state) {
			}
		});
	}

	@Override
	public void onNext() {
		if( story.start_position + 1 >= story.slides.size() ) {
			onComplete();
			return;
		}
		mViewPager.setCurrentItem(++story.start_position, false);
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
			onDismissed();
			return;
		}
		mViewPager.setCurrentItem(--story.start_position, false);
		updateElements();
	}

	@Override
	public void onComplete() {
		story.viewed = true;
		story.start_position = 0;
		cancel();
		completeListener.run();
	}

	public void updateDurations() {
		long[] durations = new long[story.slides.size()];
		for( int i = 0; i < story.slides.size(); i++ ) {
			durations[i] = story.slides.get(i).duration;
		}
		storiesProgressView.setStoriesCountWithDurations(durations);
	}

	@Override
	public void updateDuration(int position) {
		storiesProgressView.updateStoryDuration(position, story.slides.get(position).duration);
		//Получили длинну видео, значит подгрузилось, можно продолжать анимацию
		if( position == mViewPager.getCurrentItem() ) {
			storiesProgressView.resume();
		}
	}

	@Override
	public void startStories() {
		updateDurations();
		storiesProgressView.startStories(story.start_position);
		mViewPager.setCurrentItem(story.start_position, false);
		updateElements();
	}

	private void previousStory() {
		onPrev();
		updateDurations();
		storiesProgressView.startStories(story.start_position);
	}

	@Override
	public void nextStory() {
		onNext();
		storiesProgressView.startStories(story.start_position);
	}

	private void updateElements() {
		Story.Slide slide = story.slides.get(story.start_position);

		//Скрываем все элементы
		header.setVisibility(View.GONE);
		button.setVisibility(View.GONE);

		//Отображаем необходимые элементы
		for( Story.Slide.Element element : slide.elements ) {
			switch( element.type ) {
				case "header":
					updateHeader(element, slide.id);
					break;
				case "button":
					button.setVisibility(View.VISIBLE);
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
					if( element.text_bold ) {
						button.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
					}
					button.setOnTouchListener((View v, MotionEvent event) -> {
						if( event.getAction() == MotionEvent.ACTION_UP ) {
							getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(element.link)));
							SDK.track_story("click", code, story.id, slide.id);
						}
						return true;
					});
					break;
				case "products":
					//todo v2
					break;
			}
		}
	}

	private void updateHeader(Story.Slide.Element element, int slide_id) {
		if( element.type.equals("header") ) {
			header.setVisibility(View.VISIBLE);
			header.setOnTouchListener((View v, MotionEvent event) -> {
				if( event.getAction() == MotionEvent.ACTION_UP ) {
					getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(element.link)));
					SDK.track_story("click", code, story.id, slide_id);
				}
				return true;
			});

			if( element.icon != null ) {
				titleCardView.setVisibility(View.VISIBLE);
				if( getContext() == null ) return;
				Glide.with(getContext()).load(element.icon).into(titleIconImageView);
			} else {
				titleCardView.setVisibility(View.GONE);
			}

			if( element.title != null ) {
				titleTextView.setVisibility(View.VISIBLE);
				titleTextView.setText(element.title);
			} else {
				titleTextView.setVisibility(View.GONE);
			}

			if( element.subtitle != null ) {
				subtitleTextView.setVisibility(View.VISIBLE);
				subtitleTextView.setText(element.subtitle);
			} else {
				subtitleTextView.setVisibility(View.GONE);
			}
		}
	}

	private void setHeadingVisibility(int visibility) {
		elements_layout.animate().alpha(visibility == View.GONE ? 0 : 1).setStartDelay(visibility == View.GONE ? 100 : 0).setDuration(200);

		mViewPager.togglePause(visibility == View.GONE);
	}

	@Override
	public void onDismissed() {
		storiesProgressView.destroy();
		cancel();
	}

	@Override
	public void onReleased() {
		storiesProgressView.resume();
		setHeadingVisibility(View.VISIBLE);
	}

	@Override
	public boolean onShouldInterceptTouchEvent() {
		return false;
	}
}