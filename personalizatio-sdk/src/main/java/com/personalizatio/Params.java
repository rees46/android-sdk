package com.personalizatio;

import android.text.TextUtils;

import java.util.HashMap;

final public class Params {

	private final HashMap<String, String> params = new HashMap<>();

	interface ParamInterface {
		String getValue();
	}

	public enum Parameter implements ParamInterface {
		SEARCH_TYPE("type"),
		SEARCH_QUERY("search_query"),
		PAGE("page"),
		LIMIT("limit"),
		RECOMMENDER_TYPE("recommender_type"),
		RECOMMENDER_CODE("recommender_code"),
		ITEM("item"),
		CATEGORY("category"),
		LOCATIONS("locations"),
		BRANDS("brands"),
		EXCLUDE_BRANDS("exclude_brands"),
		CATEGORIES("categories"),
		DISCOUNT("discount");

		protected String value;
		Parameter(String v) {
			value = v;
		}
		public String getValue() {
			return value;
		}
	}

	public enum SEARCH_TYPE implements ParamInterface {
		INSTANT("instant_search"),
		FULL("full_search");
		protected String value;
		SEARCH_TYPE(String v) {
			value = v;
		}
		public String getValue() {
			return value;
		}
	}

	public Params put(Parameter param, String value) {
		params.put(param.value, value);
		return this;
	}

	public Params put(Parameter param, String[] value) {
		params.put(param.value, TextUtils.join(",", value));
		return this;
	}

	HashMap<String, String> build() {
		return params;
	}
}
