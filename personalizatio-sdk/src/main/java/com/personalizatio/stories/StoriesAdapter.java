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
import com.google.android.material.shape.CornerTreatment;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.personalizatio.R;

import java.util.ArrayList;

final class StoriesAdapter extends RecyclerView.Adapter<StoriesAdapter.ViewHolder> {

	private final ArrayList<Story> data;
	private final ClickListener listener;
	private final StoriesView stories_view;

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
			final float scale = itemView.getResources().getDisplayMetrics().density;

			//Загружаем иконку
			Glide.with(image.getContext()).load(story.avatar).into(image);

			//Изменяем иконку на квадратную
			if( stories_view.settings.icon_display_format == Settings.ICON_DISPLAY_FORMAT.RECTANGLE ) {
				ShapeAppearanceModel shape = image.getShapeAppearanceModel().toBuilder()
						.setAllCorners(CornerFamily.ROUNDED, 0)
						.build();
				image.setShapeAppearanceModel(shape);
				border.setShapeAppearanceModel(shape);
			}

			//Загружаем в кеш картинку первого слайда
			if( story.slides.get(story.start_position).type.equals("image") ) {
				Glide.with(image.getContext()).load(story.slides.get(story.start_position).background).preload();
			}
			if( stories_view.settings.new_campaign_border_color != null ) {
				border.setStrokeWidth(border.getContext().getResources().getDimension(R.dimen.story_avatar_border));
				border.setStrokeColor(ColorStateList.valueOf(Color.parseColor(story.viewed ? stories_view.settings.visited_campaign_border_color : stories_view.settings.new_campaign_border_color)));
			} else {
				//Default border style for old api
				border.setStrokeWidth(story.viewed ? 0 : border.getContext().getResources().getDimension(R.dimen.story_avatar_border));
			}
			itemView.setAlpha(story.viewed ? stories_view.settings.visited_campaign_transparency : 1);
			//Размер аватарки
			ViewGroup.LayoutParams layoutParams = avatar_size.getLayoutParams();
			layoutParams.width = (int) (stories_view.settings.icon_size * scale + 0.5f);
			layoutParams.height = (int) (stories_view.settings.icon_size * scale + 0.5f);
			avatar_size.setLayoutParams(layoutParams);
			//Устанавливаем отступы
			itemView.setPadding(
					stories_view.settings.icon_padding_x != null ? (int) (stories_view.settings.icon_padding_x * scale) : itemView.getPaddingLeft(),
					stories_view.settings.icon_padding_top != null ? (int) (stories_view.settings.icon_padding_top * scale) : itemView.getPaddingTop(),
					stories_view.settings.icon_padding_x != null ? (int) (stories_view.settings.icon_padding_x * scale) : itemView.getPaddingRight(),
					stories_view.settings.icon_padding_bottom != null ? (int) (stories_view.settings.icon_padding_bottom * scale) : itemView.getPaddingBottom()
			);
			//Позиция
			story_index = position;
			image.setOnClickListener(this);
			name.setText(story.name);
			name.setTextColor(Color.parseColor(stories_view.settings.label_font_color));
			name.setTextSize(stories_view.settings.label_font_size);
			name.setTypeface(stories_view.settings.label_font_family);
			name.setWidth((int) ((stories_view.settings.label_width != null ? stories_view.settings.label_width : stories_view.settings.icon_size) * scale));
			//Пин
			pin.setVisibility(story.pinned ? View.VISIBLE : View.GONE);
			pin.setText(stories_view.settings.pin_symbol);
			ShapeAppearanceModel shapeAppearanceModel = new ShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, 50).build();
			MaterialShapeDrawable shapeDrawable = new MaterialShapeDrawable(shapeAppearanceModel);
			shapeDrawable.setFillColor(ColorStateList.valueOf(Color.parseColor(stories_view.settings.background_pin)));
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
	public StoriesAdapter(StoriesView view, ArrayList<Story> data, ClickListener listener) {
		stories_view = view;
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
