package com.personalizatio.stories.models;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final public class Story {
	private final int id;
	private final String avatar;
	private final String name;
	private boolean viewed;
	private final boolean pinned;
	private int startPosition;
	private final List<Slide> slides;

	public Story(@NonNull JSONObject json) {
		id = json.optInt("id", 0);
		avatar = json.optString("avatar", "");
		name = json.optString("name", "");
		viewed = json.optBoolean("viewed", false);
		pinned = json.optBoolean("pinned", false);
		startPosition = json.optInt("start_position", 0);
		slides = new ArrayList<>();
		var slidesJsonArray = json.optJSONArray("slides");
		if (slidesJsonArray != null) {
			for( int i = 0; i < slidesJsonArray.length(); i++ ) {
				slides.add(new Slide(slidesJsonArray.optJSONObject(i)));
			}
		}
	}

	public int getId() {
		return id;
	}

	public String getAvatar() {
		return avatar;
	}

	public String getName() {
		return name;
	}

	public boolean isViewed() {
		return viewed;
	}

	public void setViewed(boolean viewed) {
		this.viewed = viewed;
	}

	public boolean isPinned() {
		return pinned;
	}

	public int getStartPosition() {
		return startPosition;
	}

	public void setStartPosition(int startPosition) {
		this.startPosition = startPosition;
	}

	public Slide getSlide(int position) {
		return slides.get(position);
	}

	public int getSlidesCount() {
		return slides.size();
	}

	public void resetStartPosition() {
		if (startPosition >= getSlidesCount() || startPosition < 0 ) {
			startPosition = 0;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Story story)) return false;
		return id == story.id
				&& viewed == story.viewed
				&& pinned == story.pinned
				&& startPosition == story.startPosition
				&& avatar.equals(story.avatar)
				&& name.equals(story.name)
				&& slides.equals(story.slides);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, avatar, name, viewed, pinned, startPosition, slides);
	}

	@NonNull
	@Override
	public String toString() {
		return String.format("Story{id='%s', avatar='%s', name='%s', viewed=%b, pinned=%b, startPosition=%d, slides=%s}",
				id, avatar, name, viewed, pinned, startPosition, slides);
	}
}
