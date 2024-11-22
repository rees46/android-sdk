package com.personalization.sdk.data.mappers

import com.personalization.api.responses.initialization.CloseAction
import com.personalization.api.responses.initialization.Components
import com.personalization.api.responses.initialization.Link
import com.personalization.api.responses.initialization.Popup
import com.personalization.api.responses.initialization.PopupActions
import com.personalization.api.responses.initialization.SdkInitializationResponse
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.mapToSdkInitResponse(): SdkInitializationResponse? {
    return try {
        val popupObject = this.optJSONObject("popup")?.let { popupJson ->
            Popup(
                id = popupJson.optInt("id"),
                channels = popupJson.optJSONArray("channels")?.toList() ?: emptyList(),
                position = popupJson.optString("position"),
                delay = popupJson.optInt("delay"),
                html = popupJson.optString("html"),
                components = popupJson.optString("components")?.let {
                    JSONObject(it).run {
                        Components(
                            text = optString("text"),
                            image = optString("image"),
                            button = optString("button"),
                            header = optString("header"),
                            textEnabled = optString("text_enabled"),
                            imageEnabled = optString("image_enabled"),
                            headerEnabled = optString("header_enabled")
                        )
                    }
                },
                webPushSystem = popupJson.optBoolean("web_push_system"),
                popupActions = popupJson.optString("popup_actions")?.let {
                    JSONObject(it).run {
                        PopupActions(
                            link = optJSONObject("link")?.let { linkJson ->
                                Link(
                                    linkIos = linkJson.optString("link_ios"),
                                    linkWeb = linkJson.optString("link_web"),
                                    buttonText = linkJson.optString("button_text"),
                                    linkAndroid = linkJson.optString("link_android")
                                )
                            },
                            close = optJSONObject("close")?.let { closeJson ->
                                CloseAction(
                                    buttonText = closeJson.optString("button_text")
                                )
                            }
                        )
                    }
                }
            )
        }

        SdkInitializationResponse(
            did = this.optString("did"),
            seance = this.optString("seance"),
            currency = this.optString("currency"),
            emailCollector = this.optBoolean("email_collector"),
            hasEmail = this.optBoolean("has_email"),
            recommendations = this.optBoolean("recommendations"),
            lazyLoad = this.optBoolean("lazy_load"),
            autoCssRecommender = this.optBoolean("auto_css_recommender"),
            cms = this.optString("cms"),
            snippets = this.optJSONArray("snippets")?.toList() ?: emptyList(),
            popup = popupObject,
            search = null,
            webPushSettings = null,
            recone = this.optBoolean("recone")
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun JSONArray.toList(): List<String> {
    val list = mutableListOf<String>()
    for (i in 0 until length()) {
        list.add(optString(i))
    }
    return list
}
