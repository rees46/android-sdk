package com.personalizatio.stories.models;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final public class Story {
	private int id;
	private String avatar;
	private String name;
	private boolean viewed;
	private boolean pinned;
	private int start_position;
	private List<Slide> slides;

	public Story(@NonNull JSONObject json) throws JSONException {
		if (json.has("id")) {
			id = json.getInt("id");
		}
		if (json.has("avatar")) {
			avatar = json.getString("avatar");
		}
		if (json.has("name")) {
			name = json.getString("name");
		}
		if (json.has("viewed")) {
			viewed = json.getBoolean("viewed");
		}
		if (json.has("pinned")) {
			pinned = json.getBoolean("pinned");
		}
		if (json.has("start_position")) {
			start_position = json.getInt("start_position");
		}
		slides = new ArrayList<>();
		if (json.has("slides")) {
			JSONArray json_slides = json.getJSONArray("slides");
			for( int i = 0; i < json_slides.length(); i++ ) {
				slides.add(new Slide(json_slides.getJSONObject(i)));
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
		return start_position;
	}

	public void setStartPosition(int startPosition) {
		this.start_position = startPosition;
	}

	public Slide getSlide(int position) {
		return slides.get(position);
	}

	public int getSlidesCount() {
		return slides.size();
	}
}
