package com.personalization.stories.models.elements

import org.json.JSONObject

interface LinkElement : Element {
    fun getLink(json: JSONObject): String {
        var link = json.optString("link_android", "")
        if (link.isEmpty()) {
            link = json.optString("link", "")
        }
        return link
    }

    val link: String
}
