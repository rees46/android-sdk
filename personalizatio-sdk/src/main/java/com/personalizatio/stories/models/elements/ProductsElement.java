package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import com.personalizatio.Product;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProductsElement implements Element {

    private String labelHide;
    private String labelShow;
    private final List<Product> products = new ArrayList<>();

    public ProductsElement(@NonNull JSONObject json) throws JSONException {
        if (json.has("labels")) {
            var labelsJson = json.getJSONObject("labels");
            if (labelsJson.has("hide_carousel")) {
                labelHide = labelsJson.getString("hide_carousel");
            }
            if (labelsJson.has("show_carousel")) {
                labelShow = labelsJson.getString("show_carousel");
            }
        }
        if (json.has("products")) {
            JSONArray products = json.getJSONArray("products");
            for (int i = 0; i < products.length(); i++) {
                this.products.add(new Product(products.getJSONObject(i)));
            }
        }
    }

    public String getLabelHide() {
        return labelHide;
    }

    public String getLabelShow() {
        return labelShow;
    }

    public List<Product> getProducts() {
        return products;
    }
}
