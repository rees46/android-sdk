package com.personalizatio.stories;

import org.json.JSONException;
import org.json.JSONObject;

final class Settings {
	public final String color;
	public final int font_size;
	public final String background;
	public final int avatar_size;
	public final String close_color;
	public final String border_viewed;
	public final String background_pin;
	public final String border_not_viewed;
	public final int background_opacity;
	public final String background_progress;
	public final String pin_symbol;

	public Settings(JSONObject json) throws JSONException {
		color = json.getString("color");
		font_size = Integer.parseInt(json.getString("font_size"));
		background = json.getString("background");
		avatar_size = Integer.parseInt(json.getString("avatar_size"));
		close_color = json.getString("close_color");
		border_viewed = json.getString("border_viewed");
		background_pin = json.getString("background_pin");
		border_not_viewed = json.getString("border_not_viewed");
		background_opacity = Integer.parseInt(json.getString("background_opacity"));
		background_progress = json.getString("background_progress");
		pin_symbol = json.getString("pin_symbol");
	}
}
