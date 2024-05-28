package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import com.personalizatio.Product;

import org.json.JSONException;
import org.json.JSONObject;

public class ProductElement implements Element {

    private String title;
    private Product item;

    public ProductElement(@NonNull JSONObject json) throws JSONException {
        if (json.has("title")) {
            title = json.getString("title");
        }
        if (json.has("item")) {
            item = new Product(json.getJSONObject("item"));
        }
    }

    public String getTitle() {
        return title;
    }

    public Product getItem() {
        return item;
    }
}
