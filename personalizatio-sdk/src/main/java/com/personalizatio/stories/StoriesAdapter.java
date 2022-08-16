package com.personalizatio.stories;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.personalizatio.R;

import java.util.ArrayList;

class StoriesAdapter extends RecyclerView.Adapter<StoriesAdapter.ViewHolder> {

	private final ArrayList<Story> data;
	private final ClickListener listener;

	public interface ClickListener {
		void onStoryClick(int id);
	}

	public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		public final ShapeableImageView image;
		public final ShapeableImageView border;
		public final TextView name;
		private final ClickListener listener;
		private int story_index;

		public ViewHolder(View view, ClickListener listener) {
			super(view);
			image = view.findViewById(R.id.avatar);
			border = view.findViewById(R.id.avatar_border);
			name = view.findViewById(android.R.id.text1);
			this.listener = listener;
		}

		public void setRow(Story story, final int position) {
			Glide.with(image.getContext()).load(story.avatar).into(image);
			border.setStrokeWidth(story.viewed ? 0 : border.getContext().getResources().getDimension(R.dimen.story_avatar_border));
			story_index = position;
			image.setOnClickListener(this);
			name.setText(story.name);
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
