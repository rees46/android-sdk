package com.personalizatio.stories.models;

import androidx.annotation.NonNull;

import com.personalizatio.Product;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Element {
    private String type;
    private String link;
    private String title;
    private String subtitle;
    private String icon;
    private String background;
    private String color;
    private Boolean text_bold = false;
    private Boolean text_italic = false;
    private String label_hide;
    private String label_show;
    private List<Product> products = new ArrayList<>();
    private Product item;
    private String text_input;
    private int y_offset;
    private String font_type;
    private int font_size = 14;
    private String text_align;
    private String text_color;
    private double text_line_spacing;
    private String text_background_color = "#FFFFFF";
    private String text_background_color_opacity = "0%";

    public Element(@NonNull JSONObject json) throws JSONException {
        if (json.has("type")) {
            type = json.getString("type");
        }
        if (json.has("link_android")) {
            link = json.getString("link_android");
        }
        if ((link == null || link.length() == 0) && json.has("link")) {
            link = json.getString("link");
        }
        if (json.has("icon")) {
            icon = json.getString("icon");
        }
        if (json.has("title")) {
            title = json.getString("title");
        }
        if (json.has("subtitle")) {
            subtitle = json.getString("subtitle");
        }
        if (json.has("background")) {
            background = json.getString("background");
        }
        if (json.has("color")) {
            color = json.getString("color");
        }
        if (json.has("text_bold")) {
            text_bold = json.getBoolean("text_bold");
        }
        if (json.has("bold")) {
            text_bold = json.getBoolean("bold");
        }
        if (json.has("italic")) {
            text_italic = json.getBoolean("italic");
        }
        if (json.has("labels")) {
            label_hide = json.getJSONObject("labels").getString("hide_carousel");
            label_show = json.getJSONObject("labels").getString("show_carousel");
        }
        if (json.has("products")) {
            JSONArray products = json.getJSONArray("products");
            for (int i = 0; i < products.length(); i++) {
                this.products.add(new Product(products.getJSONObject(i)));
            }
        }
        if (json.has("item") && type.equals("product")) {
            item = new Product(json.getJSONObject("item"));
        }
        if (json.has("text_input")) {
            text_input = json.getString("text_input");
        }
        if (json.has("y_offset")) {
            y_offset = json.getInt("y_offset");
        }
        if (json.has("font_type")) {
            font_type = json.getString("font_type");
        }
        if (json.has("font_size")) {
            font_size = json.getInt("font_size");
        }
        if (json.has("text_align")) {
            text_align = json.getString("text_align");
        }
        if (json.has("text_color")) {
            text_color = json.getString("text_color");
        }
        if (json.has("text_line_spacing")) {
            text_line_spacing = json.getDouble("text_line_spacing");
        }
        if (json.has("text_background_color")) {
            text_background_color = json.getString("text_background_color");
        }
        if (json.has("text_background_color_opacity")) {
            text_background_color_opacity = json.getString("text_background_color_opacity");
        }
    }

    public String getType() {
        return type;
    }

    public String getLink() {
        return type;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getTitle() {
        return title;
    }

    public String getIcon() {
        return icon;
    }

    public String getBackground() {
        return background;
    }

    public String getColor() {
        return color;
    }

    public Boolean getTextBold() {
        return text_bold;
    }

    public Boolean getTextItalic() {
        return text_italic;
    }

    public String getLabelHide() {
        return label_hide;
    }

    public String getLabelShow() {
        return label_show;
    }

    public Product getItem() {
        return item;
    }

    public String getTextInput() {
        return text_input;
    }

    public int getYOffset() {
        return y_offset;
    }

    public String getFontType() {
        return font_type;
    }

    public int getFontSize() {
        return font_size;
    }

    public String getTextAlign() {
        return text_align;
    }

    public String getTextColor() {
        return text_color;
    }

    public double getTextLineSpacing() {
        return text_line_spacing;
    }

    public String getTextBackgroundColor() {
        return text_background_color;
    }

    public String getTextBackgroundColorOpacity() {
        return text_background_color_opacity;
    }

    public List<Product> getProducts() {
        return products;
    }
}
