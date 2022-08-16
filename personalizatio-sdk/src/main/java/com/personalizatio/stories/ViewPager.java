package com.personalizatio.stories;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.personalizatio.SDK;

import java.lang.reflect.Field;

class ViewPager extends androidx.viewpager.widget.ViewPager {

	public ViewPager(@NonNull Context context) {
		super(context);
	}

	public ViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public void togglePause(boolean pause) {
		StoryItemView view = getCurrentView();
		if( view != null && view.mediaPlayer != null ) {
			if( pause ) {
				view.mediaPlayer.pause();
			} else {
				view.mediaPlayer.start();
			}
		}
	}

	private StoryItemView getCurrentView() {
		try {
			final int currentItem = getCurrentItem();
			for( int i = 0; i < getChildCount(); i++ ) {
				final View child = getChildAt(i);
				final ViewPager.LayoutParams layoutParams = (ViewPager.LayoutParams) child.getLayoutParams();

				Field f = layoutParams.getClass().getDeclaredField("position"); //NoSuchFieldException
				f.setAccessible(true);
				int position = (Integer) f.get(layoutParams); //IllegalAccessException

				if( !layoutParams.isDecor && currentItem == position ) {
					return (StoryItemView) child;
				}
			}
		} catch(NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
			Log.e(SDK.TAG, e.toString());
		}
		return null;
	}
}
