package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import com.personalizatio.Product;

import org.json.JSONObject;

import java.util.Objects;

public class ProductElement implements Element {

    private final String title;
    private Product item;

    public ProductElement(@NonNull JSONObject json) {
        title = json.optString("title", "");
        var itemJson = json.optJSONObject("item");
        if (itemJson != null) {
            item = new Product(itemJson);
        }
    }

    public String getTitle() {
        return title;
    }

    public Product getItem() {
        return item;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductElement that)) return false;
        return title.equals(that.title)
                && item.equals(that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, item);
    }

    @NonNull
    @Override
    public String toString() {
        return "ProductElement{" +
                "title='" + title + '\'' +
                ", item=" + item +
                '}';
    }
}
