package com.personalizatio.stories;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.personalizatio.R;

import java.util.ArrayList;

final class StoriesAdapter extends RecyclerView.Adapter<StoriesAdapter.ViewHolder> {

	private final ArrayList<Story> data;
	private final ClickListener listener;
	public Settings settings;

	public interface ClickListener {
		void onStoryClick(int id);
	}

	class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		public final ShapeableImageView image;
		public final ShapeableImageView border;
		public final ViewGroup avatar_size;
		public final TextView name;
		public final TextView pin;
		private final ClickListener listener;
		private int story_index;

		public ViewHolder(View view, ClickListener listener) {
			super(view);
			image = view.findViewById(R.id.avatar);
			border = view.findViewById(R.id.avatar_border);
			avatar_size = view.findViewById(R.id.avatar_size);
			name = view.findViewById(android.R.id.text1);
			pin = view.findViewById(android.R.id.text2);
			this.listener = listener;
		}

		public void setRow(Story story, final int position) {
			Glide.with(image.getContext()).load(story.avatar).into(image);
			if( settings.border_not_viewed != null ) {
				border.setStrokeWidth(border.getContext().getResources().getDimension(R.dimen.story_avatar_border));
				border.setStrokeColor(ColorStateList.valueOf(Color.parseColor(story.viewed ? settings.border_viewed : settings.border_not_viewed)));
			} else {
				//Default border style for old api
				border.setStrokeWidth(story.viewed ? 0 : border.getContext().getResources().getDimension(R.dimen.story_avatar_border));
			}
			//Размер аватарки
			final float scale = itemView.getResources().getDisplayMetrics().density;
			ViewGroup.LayoutParams layoutParams = avatar_size.getLayoutParams();
			layoutParams.width = (int) (settings.avatar_size * scale + 0.5f);
			layoutParams.height = (int) (settings.avatar_size * scale + 0.5f);
			avatar_size.setLayoutParams(layoutParams);
			//Размер основного фона
			layoutParams = itemView.getLayoutParams();
			layoutParams.width = (int) (settings.avatar_size * scale + 0.5f);
			layoutParams.height = (int) (settings.avatar_size * scale + 0.5f + settings.font_size * scale);
			itemView.setLayoutParams(layoutParams);
			//Позиция
			story_index = position;
			image.setOnClickListener(this);
			name.setText(story.name);
			name.setTextColor(Color.parseColor(settings.color));
			name.setTextSize(settings.font_size);
			//Пин
			pin.setVisibility(story.pinned ? View.VISIBLE : View.GONE);
			pin.setText(settings.pin_symbol);
			ShapeAppearanceModel shapeAppearanceModel = new ShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, 50).build();
			MaterialShapeDrawable shapeDrawable = new MaterialShapeDrawable(shapeAppearanceModel);
			shapeDrawable.setFillColor(ColorStateList.valueOf(Color.parseColor(settings.background_pin)));
			ViewCompat.setBackground(pin, shapeDrawable);
		}

		@Override
		public void onClick(View view) {
			listener.onStoryClick(story_index);
		}
	}

	/**
	 * Initialize the dataset of the Adapter.
	 */
	public StoriesAdapter(ArrayList<Story> data, ClickListener listener) {
		this.data = data;
		this.listener = listener;
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
		View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.story_avatar, viewGroup, false);

		return new ViewHolder(view, listener);
	}

	@Override
	public void onBindViewHolder(ViewHolder viewHolder, final int position) {
		viewHolder.setRow(data.get(position), position);
	}

	@Override
	public int getItemCount() {
		return data.size();
	}
}
