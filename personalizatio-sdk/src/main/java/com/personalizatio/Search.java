package com.personalizatio;

import org.json.JSONObject;

final class Search {

	private final JSONObject options;
	public JSONObject blank;

	Search(JSONObject params) {
		options = params;
	}
}
