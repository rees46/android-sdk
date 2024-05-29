package com.personalizatio;

import androidx.annotation.NonNull;

import org.json.JSONObject;

final public class Product {
	private final String id;
	private final String name;
	private final String brand;
	private final String image;
	private final String oldPrice;
	private final String price;
	private final String discount;
	private final String url;
	private final String deeplink;
	private final String promocode;
	private final String priceWithPromocode;
	private final String discountPercent;

	public Product(@NonNull JSONObject json) {
		id = json.optString("id", "");
		name = json.optString("name", "");
		image = json.optString("image_url", "");
		var oldPriceValue = json.optDouble("oldprice", 0);
		if (oldPriceValue > 0) {
			oldPrice = json.optString("oldprice_formatted", "");
		}
		else {
			oldPrice = "";
		}
		brand = json.optString("brand", "");
		promocode = json.optString("promocode", "");
		priceWithPromocode = json.optString("price_with_promocode_formatted", "");
		discountPercent = json.optString("discount_percent", "");
		price = json.optString("price_formatted", "");
		url = json.optString("url", "");
		deeplink = json.optString("deeplink_android", "");
		var priceValue = json.optDouble("price", 0);
		if (oldPriceValue > 0 && oldPriceValue > priceValue) {
			discount = json.optString("discount", "");
		}
		else {
			discount = "";
		}
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getBrand() {
		return brand;
	}

	public String getImage() {
		return image;
	}

	public String getOldPrice() {
		return oldPrice;
	}

	public String getPrice() {
		return price;
	}

	public String getDiscount() {
		return discount;
	}

	public String getUrl() {
		return url;
	}

	public String getDeeplink() {
		return deeplink;
	}

	public String getPromocode() {
		return promocode;
	}

	public String getPriceWithPromocode() {
		return priceWithPromocode;
	}

	public String getDiscountPercent() {
		return discountPercent;
	}
}
