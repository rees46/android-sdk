package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import com.personalizatio.Product;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProductsElement implements Element {

    private String labelHide;
    private String labelShow;
    private final List<Product> products = new ArrayList<>();

    public ProductsElement(@NonNull JSONObject json) {
        var labelsJson = json.optJSONObject("labels");
        if (labelsJson != null) {
            labelHide = json.optString("hide_carousel", "");
            labelShow = json.optString("show_carousel", "");
        }
        var productsJsonArray = json.optJSONArray("products");
        if (productsJsonArray != null)
        {
            for (int i = 0; i < productsJsonArray.length(); i++) {
                products.add(new Product(productsJsonArray.optJSONObject(i)));
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductsElement that)) return false;
        return labelHide.equals(that.labelHide)
                && labelShow.equals(that.labelShow)
                && products.equals(that.products);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labelHide, labelShow, products);
    }

    @NonNull
    @Override
    public String toString() {
        return "ProductsElement{" +
                "labelHide='" + labelHide + '\'' +
                ", labelShow='" + labelShow + '\'' +
                ", products=" + products +
                '}';
    }
}
