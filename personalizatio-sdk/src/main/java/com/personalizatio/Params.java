package com.personalizatio;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

final public class Params extends AbstractParams<Params> {

	private int item_count = 0;

	/**
	 * Основные параметры
	 */
	public enum Parameter implements ParamInterface {
		LIMIT("limit"),
		ITEM("item_id"),
		LOCATIONS("locations"),
		/**
		 * Available sizes: 120, 140, 160, 180, 200, 220, 310, 520
		 */
		IMAGE_SIZE("resize_image"),
		BRANDS("brands"),
		EXCLUDE_BRANDS("exclude_brands"),
		CATEGORIES("categories"),
		DISCOUNT("discount"),
		FULL_CART("full_cart"),
		ORDER_ID("order_id"),
		ORDER_PRICE("order_price"),
		CATEGORY_ID("category_id"),
		SEARCH_QUERY("search_query"),
		EXTENDED("extended"),
		;

		protected String value;
		Parameter(String v) {
			value = v;
		}
		@Override
		public String getValue() {
			return value;
		}
	}

	/**
	 * Типы рекомендаций
	 */
	final public static class RecommendedBy {
		public enum TYPE {
			RECOMMENDATION("dynamic"),
			TRIGGER("chain"),
			DIGEST_MAIL("digest_mail"),
			WEB_PUSH_DIGEST("web_push_digest"),
			INSTANT_SEARCH("instant_search"),
			FULL_SEARCH("full_search"),
			;

			protected String value;
			TYPE(String v) {
				value = v;
			}
			public String getValue() {
				return value;
			}
		}

		String type;
		String code;
		public RecommendedBy(TYPE type) {
			this.type = type.getValue();
		}
		public RecommendedBy(TYPE type, String code) {
			this.type = type.getValue();
			this.code = code;
		}
	}

	/**
	 * Товар
	 */
	final public static class Item {
		public enum COLUMN {
			ID("item_id"),
			AMOUNT("amount"),
			FASHION_SIZE("fashion_size"),
			;
			String value;
			COLUMN(String v) {
				value = v;
			}
		}

		final HashMap<String, String> columns = new HashMap<>();
		public Item(@NonNull String id) {
			columns.put(COLUMN.ID.value, id);
		}
		public Item set(COLUMN column, @NonNull String value) {
			columns.put(column.value, value);
			return this;
		}
		public Item set(COLUMN column, @NonNull int value) {
			return set(column, String.valueOf(value));
		}
		public Item set(COLUMN column, @NonNull boolean value) {
			return set(column, value ? "1" : "0");
		}
	}

	public enum TrackEvent {
		VIEW("view"),
		CATEGORY("category"),
		CART("cart"),
		REMOVE_FROM_CART("remove_from_cart"),
		PURCHASE("purchase"),
		SEARCH("search"),
		;
		String value;
		TrackEvent(String v) {
			value = v;
		}
	}

	/**
	 * Вставка параметров рекомендаций
	 */
	public Params put(RecommendedBy recommended_by) {
		params.put(InternalParameter.RECOMMENDED_BY.getValue(), recommended_by.type);
		if( recommended_by.code != null ) {
			params.put(InternalParameter.RECOMMENDED_CODE.getValue(), recommended_by.code);
		}
		return this;
	}

	/**
	 * Вставка товара
	 */
	public Params put(Item item) {
		for( Map.Entry<String, String> entry : item.columns.entrySet() ) {
			params.put(entry.getKey() + "[" + item_count + "]", entry.getValue());
		}
		item_count++;

		return this;
	}

	//---------------Private---------->

	enum InternalParameter implements ParamInterface {
		SEARCH_TYPE("type"),
		SEARCH_QUERY("search_query"),
		RECOMMENDER_TYPE("recommender_type"),
		RECOMMENDER_CODE("recommender_code"),
		EVENT("event"),
		RECOMMENDED_BY("recommended_by"),
		RECOMMENDED_CODE("recommended_code"),
		;

		protected String value;
		InternalParameter(String v) {
			value = v;
		}
		@Override
		public String getValue() {
			return value;
		}
	}

}
