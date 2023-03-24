package com.personalizatio.stories;

import android.util.Log;

import com.personalizatio.SDK;

import org.json.JSONException;
import org.json.JSONObject;

class Product {
	public final String name;
	public final String image;
	public final String oldprice;
	public final String price;
	public final String discount;
	public final String url;

	public Product(JSONObject product) throws JSONException {
		name = product.getString("name");
		image = product.getString("picture");
		if( product.has("oldprice") && !product.getString("oldprice").equals("null") ) {
			oldprice = product.getString("oldprice");
		} else {
			oldprice = null;
		}
		price = product.getString("price");
		url = product.getString("url");
		if( product.has("discount") && !product.getString("discount").equals("null") ) {
			discount = product.getString("discount");
		} else {
			discount = null;
		}
	}
}
