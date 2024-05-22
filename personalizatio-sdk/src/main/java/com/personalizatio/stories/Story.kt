package com.personalizatio.stories

import com.personalizatio.Product

internal class Story(@NonNull json: JSONObject?) {
    var id: Int = json.getInt("id")
    var avatar: String? = json.getString("avatar")
    var name: String? = json.getString("name")
    var viewed: Boolean = json.getBoolean("viewed")
    var pinned: Boolean = json.getBoolean("pinned")
    var start_position: Int = json.getInt("start_position")
    var slides: ArrayList<Slide?>? = ArrayList()

    init {
        val json_slides: JSONArray = json.getJSONArray("slides")
        for (i in 0 until json_slides.length()) {
            slides.add(Slide(json_slides.getJSONObject(i)))
        }
    }

    internal class Slide(@NonNull json: JSONObject?) : Serializable {
        var id: String? = json.getString("id")
        var background: String?
        var background_color: String? = null
        var preview: String? = null
        var type: String?
        var elements: ArrayList<Element?>?
        var duration: Long
        var prepared: Boolean = false

        init {
            background = json.getString("background")
            if (json.has("background_color")) {
                background_color = json.getString("background_color")
            }
            if (json.has("preview")) {
                preview = json.getString("preview")
            }
            type = json.getString("type")
            duration = json.optLong("duration", 5) * 1000L
            elements = ArrayList()
            val json_elements: JSONArray = json.getJSONArray("elements")
            for (i in 0 until json_elements.length()) {
                elements.add(Element(json_elements.getJSONObject(i)))
            }
        }

        internal class Element(@NonNull json: JSONObject?) {
            var type: String? = json.getString("type")
            var link: String? = null
            var title: String? = null
            var subtitle: String? = null
            var icon: String? = null
            var background: String? = null
            var color: String? = null
            var text_bold: Boolean? = false
            var label_hide: String? = null
            var label_show: String? = null
            var products: ArrayList<Product?>? = ArrayList()
            var item: Product? = null

            init {
                if (json.has("link_android")) {
                    link = json.getString("link_android")
                }
                if ((link == null || link.length() === 0) && json.has("link")) {
                    link = json.getString("link")
                }
                if (json.has("icon")) {
                    icon = json.getString("icon")
                }
                if (json.has("title")) {
                    title = json.getString("title")
                }
                if (json.has("subtitle")) {
                    subtitle = json.getString("subtitle")
                }
                if (json.has("background")) {
                    background = json.getString("background")
                }
                if (json.has("color")) {
                    color = json.getString("color")
                }
                if (json.has("text_bold")) {
                    text_bold = json.getBoolean("text_bold")
                }
                if (json.has("labels")) {
                    label_hide = json.getJSONObject("labels").getString("hide_carousel")
                    label_show = json.getJSONObject("labels").getString("show_carousel")
                }
                if (json.has("products")) {
                    val products: JSONArray = json.getJSONArray("products")
                    for (i in 0 until products.length()) {
                        this.products.add(Product(products.getJSONObject(i)))
                    }
                }
                if (json.has("item") && type.equals("product")) {
                    item = Product(json.getJSONObject("item"))
                }
            }
        }
    }
}
