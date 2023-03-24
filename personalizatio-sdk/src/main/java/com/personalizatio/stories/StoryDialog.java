package com.personalizatio.stories;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.personalizatio.R;
import com.personalizatio.SDK;

import java.util.ArrayList;

final class StoryDialog extends Dialog implements PullDismissLayout.Listener {

	private final ArrayList<Story> stories;
	private final ViewPagerAdapter adapter;
	private final Settings settings;
	private ViewPager2 mViewPager;

	private final Runnable completeListener;
	private final String code;
	private final int start_position;

	public interface OnProgressState {
		void onState(boolean running);
	}

	public StoryDialog(Context context, String code, ArrayList<Story> stories, Settings settings, int start_position, Runnable completeListener) {
		super(context, android.R.style.Theme_Translucent_NoTitleBar);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dialog_stories);
		Window window = getWindow();
		WindowManager.LayoutParams wlp = window.getAttributes();

		wlp.gravity = Gravity.CENTER;
		wlp.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
		window.setAttributes(wlp);
		window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

		this.code = code;
		this.stories = stories;
		this.start_position = start_position;
		this.settings = settings;
		adapter = new ViewPagerAdapter(running -> mViewPager.setUserInputEnabled(running));

		this.completeListener = completeListener;

		setupViews();
	}

	private void setupViews() {
		((PullDismissLayout) findViewById(R.id.pull_dismiss_layout)).setListener(this);
		ImageButton closeImageButton = findViewById(R.id.imageButton);
		closeImageButton.setOnClickListener(v -> {
			onDismissed();
		});
		closeImageButton.setColorFilter(Color.parseColor(settings.close_color));
		mViewPager = findViewById(R.id.view_pager);
		mViewPager.setClipToPadding(false);
		mViewPager.setClipChildren(false);
		mViewPager.setOffscreenPageLimit(1);

		//Превью слайдов только в горизонтальном экране
		if( getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ) {
			DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
			float nextItemVisiblePx = (dm.widthPixels - dm.heightPixels * 9f / 16) / 2f;
			float currentItemHorizontalMarginPx = nextItemVisiblePx + getContext().getResources().getDimension(R.dimen.viewpager_current_item_horizontal_margin);
			float pageTranslationX = nextItemVisiblePx + currentItemHorizontalMarginPx;
			mViewPager.setPageTransformer((page, position) -> {
				page.setTranslationX(-pageTranslationX * position);
				page.setScaleY(1 - (0.25f * Math.abs(position)));
				page.setScaleX(1 - (0.25f * Math.abs(position)));
				page.setAlpha(1 - (0.7f * Math.abs(position)));
			});
			mViewPager.addItemDecoration(new RecyclerView.ItemDecoration() {
				@Override
				public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
					super.getItemOffsets(outRect, view, parent, state);
					outRect.right = (int) currentItemHorizontalMarginPx;
					outRect.left = (int) currentItemHorizontalMarginPx;
				}
			});
		}

		mViewPager.setAdapter(adapter);
		//Хак, чтобы не срабатывал onPageSelected при открытии первой кампании
		mViewPager.setCurrentItem(start_position == 0 ? stories.size() : 0, false);
		//Устанавливаем позицию
		mViewPager.setCurrentItem(start_position, false);
		mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {

			@Override
			public void onPageSelected(int position) {
				for(int i = 0; i < stories.size(); i++ ) {
					PagerHolder holder = getHolder(i);
					if( holder != null ) {
						StoryView storyView = (StoryView) holder.itemView;
						if( i == position ) {
							storyView.startStories();
						} else {
							storyView.stopStories();
						}
					}
				}
			}

			@Override
			public void onPageScrollStateChanged(int state) {
				if( state == ViewPager2.SCROLL_STATE_IDLE ) {
					onReleased();
				}
			}
		});
	}

	private PagerHolder getCurrentHolder() {
		return getHolder(mViewPager.getCurrentItem());
	}

	private PagerHolder getHolder(int position) {
		return (PagerHolder) ((RecyclerView) mViewPager.getChildAt(0)).findViewHolderForAdapterPosition(position);
	}

	public void onDetachedFromWindow() {
		//При закрытии диалогового окна, возвращаем все метки в исходное
		for( Story story : stories ) {
			for( Story.Slide slide : story.slides ) {
				slide.prepared = false;
			}
		}
	}

	@Override
	public void onDismissed() {
		cancel();
	}

	@Override
	public void onReleased() {
		PagerHolder holder = getCurrentHolder();
		if( holder != null ) {
			StoryView storyView = (StoryView) holder.itemView;
			storyView.setHeadingVisibility(View.VISIBLE);
			storyView.resume();
		}
	}

	@Override
	public boolean onShouldInterceptTouchEvent() {
		return false;
	}

	static class PagerHolder extends RecyclerView.ViewHolder {
		public PagerHolder(@NonNull StoryView itemView) {
			super(itemView);
		}
	}

	class ViewPagerAdapter extends RecyclerView.Adapter<PagerHolder> {

		private final OnProgressState state_listener;

		public ViewPagerAdapter(OnProgressState state_listener) {
			this.state_listener = state_listener;
		}

		@NonNull
		@Override
		public PagerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new PagerHolder(new StoryView(parent.getContext(), code, settings, state_listener));
		}

		@Override
		public void onViewAttachedToWindow(@NonNull PagerHolder holder) {
			if( holder.getLayoutPosition() == mViewPager.getCurrentItem() ) {
				super.onViewAttachedToWindow(holder);
				StoryView storyView = (StoryView) holder.itemView;
				storyView.setHeadingVisibility(View.VISIBLE);
				storyView.startStories();
			}
		}

		@Override
		public void onBindViewHolder(@NonNull PagerHolder holder, int position) {
			((StoryView) holder.itemView).setStory(stories.get(position), () -> {
				if( position >= stories.size() || position + 1 >= stories.size() ) {
					cancel();
				} else {
					mViewPager.setCurrentItem(position + 1);
				}
				completeListener.run();
			}, () -> {
				if( position == 0 ) {
					cancel();
				} else {
					mViewPager.setCurrentItem(position - 1);
				}
			});
		}

		@Override
		public int getItemCount() {
			return stories.size();
		}
	}
}