package com.personalizatio.stories;

import org.json.JSONException;
import org.json.JSONObject;

final class Product {
	public final String name;
	public final String image;
	public final String oldprice;
	public final String price;
	public final String discount;
	public final String url;

	public Product(JSONObject product) throws JSONException {
		name = product.getString("name");
		image = product.getString("picture");
		if( product.has("oldprice") && product.getDouble("oldprice") > 0 ) {
			oldprice = product.getString("oldprice_formatted");
		} else {
			oldprice = null;
		}
		price = product.getString("price_formatted");
		url = product.getString("url");
		if( product.has("discount") && product.getDouble("oldprice") > 0 && product.getDouble("oldprice") > product.getDouble("price") ) {
			discount = product.getString("discount");
		} else {
			discount = null;
		}
	}
}
