package com.personalizatio.stories.viewAdapters;

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
import com.personalizatio.stories.Settings;
import com.personalizatio.stories.models.Story;
import com.personalizatio.stories.views.StoriesView;

import java.util.List;

final public class StoriesAdapter extends RecyclerView.Adapter<StoriesAdapter.ViewHolder> {

	private final List<Story> data;
	private final ClickListener listener;
	private final StoriesView storiesView;

	public interface ClickListener {
		void onStoryClick(int id);
	}

	public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		private final ShapeableImageView image;
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
			Glide.with(image.getContext()).load(story.getAvatar()).into(image);

			var settings = storiesView.getSettings();

			//Изменяем иконку на квадратную
			if (settings.icon_display_format == Settings.ICON_DISPLAY_FORMAT.RECTANGLE) {
				ShapeAppearanceModel shape = image.getShapeAppearanceModel().toBuilder()
						.setAllCorners(CornerFamily.ROUNDED, 0)
						.build();
				image.setShapeAppearanceModel(shape);
				border.setShapeAppearanceModel(shape);
			}

			//Загружаем в кеш картинку первого слайда
			var firstSlide = story.getSlide(story.getStartPosition());
			if (firstSlide.getType().equals("image")) {
				Glide.with(image.getContext()).load(firstSlide.getBackground()).preload();
			}
			if (storiesView.getSettings().new_campaign_border_color != null) {
				border.setStrokeWidth(border.getContext().getResources().getDimension(R.dimen.story_avatar_border));
				border.setStrokeColor(ColorStateList.valueOf(Color.parseColor(story.isViewed()
						? settings.visited_campaign_border_color
						: settings.new_campaign_border_color)));
			} else {
				//Default border style for old api
				border.setStrokeWidth(story.isViewed() ? 0 : border.getContext().getResources().getDimension(R.dimen.story_avatar_border));
			}
			itemView.setAlpha(story.isViewed() ? settings.visited_campaign_transparency : 1);
			//Размер аватарки
			ViewGroup.LayoutParams layoutParams = avatar_size.getLayoutParams();
			var layoutParamsSize = (int) (settings.icon_size * scale + 0.5f);
			layoutParams.width = layoutParamsSize;
			layoutParams.height = layoutParamsSize;
			avatar_size.setLayoutParams(layoutParams);
			//Устанавливаем отступы
			itemView.setPadding(
					settings.icon_padding_x != null ? (int) (settings.icon_padding_x * scale) : itemView.getPaddingLeft(),
					settings.icon_padding_top != null ? (int) (settings.icon_padding_top * scale) : itemView.getPaddingTop(),
					settings.icon_padding_x != null ? (int) (settings.icon_padding_x * scale) : itemView.getPaddingRight(),
					settings.icon_padding_bottom != null ? (int) (settings.icon_padding_bottom * scale) : itemView.getPaddingBottom()
			);
			//Позиция
			story_index = position;
			image.setOnClickListener(this);
			name.setText(story.getName());
			name.setTextColor(Color.parseColor(settings.label_font_color));
			name.setTextSize(settings.label_font_size);
			name.setTypeface(settings.label_font_family);
			name.setWidth((int) ((settings.label_width != null ? settings.label_width : settings.icon_size) * scale));
			//Пин
			pin.setVisibility(story.isPinned() ? View.VISIBLE : View.GONE);
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
	public StoriesAdapter(StoriesView view, List<Story> data, ClickListener listener) {
		storiesView = view;
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
