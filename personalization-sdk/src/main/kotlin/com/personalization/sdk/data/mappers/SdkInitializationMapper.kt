package com.personalization.sdk.data.mappers

import com.personalization.api.responses.initialization.CloseAction
import com.personalization.api.responses.initialization.Components
import com.personalization.api.responses.initialization.Link
import com.personalization.api.responses.initialization.Popup
import com.personalization.api.responses.initialization.PopupActions
import com.personalization.api.responses.initialization.SdkInitializationResponse
import org.json.JSONArray
import org.json.JSONObject

object SdkInitializationMapper {

    private const val PARAM_ID = "id"
    private const val PARAM_CHANNELS = "channels"
    private const val PARAM_POSITION = "position"
    private const val PARAM_DELAY = "delay"
    private const val PARAM_HTML = "html"
    private const val PARAM_COMPONENTS = "components"
    private const val PARAM_TEXT = "text"
    private const val PARAM_IMAGE = "image"
    private const val PARAM_BUTTON = "button"
    private const val PARAM_HEADER = "header"
    private const val PARAM_TEXT_ENABLED = "text_enabled"
    private const val PARAM_IMAGE_ENABLED = "image_enabled"
    private const val PARAM_HEADER_ENABLED = "header_enabled"
    private const val PARAM_POPUP = "popup"
    private const val PARAM_WEB_PUSH_SYSTEM = "web_push_system"
    private const val PARAM_POPUP_ACTIONS = "popup_actions"
    private const val PARAM_LINK = "link"
    private const val PARAM_LINK_IOS = "link_ios"
    private const val PARAM_LINK_WEB = "link_web"
    private const val PARAM_BUTTON_TEXT = "button_text"
    private const val PARAM_LINK_ANDROID = "link_android"
    private const val PARAM_CLOSE = "close"
    private const val PARAM_DID = "did"
    private const val PARAM_SEANCE = "seance"
    private const val PARAM_CURRENCY = "currency"
    private const val PARAM_EMAIL_COLLECTOR = "email_collector"
    private const val PARAM_HAS_EMAIL = "has_email"
    private const val PARAM_RECOMMENDATION = "recommendations"
    private const val PARAM_LAZY_LOAD = "lazy_load"
    private const val PARAM_AUTO_CSS = "auto_css_recommender"
    private const val PARAM_CMS = "cms"
    private const val PARAM_SNIPPETS = "snippets"
    private const val PARAM_RECONE = "recone"

    fun JSONObject.mapToSdkInitResponse(): SdkInitializationResponse? {
        return try {
            val popupObject = this.optJSONObject(PARAM_POPUP)?.let { popupJson ->
                Popup(
                    id = popupJson.optInt(PARAM_ID),
                    channels = popupJson.optJSONArray(PARAM_CHANNELS)?.toList() ?: emptyList(),
                    position = popupJson.optString(PARAM_POSITION),
                    delay = popupJson.optInt(PARAM_DELAY),
                    html = popupJson.optString(PARAM_HTML),
                    components = popupJson.optString(PARAM_COMPONENTS)?.let {
                        JSONObject(it).run {
                            Components(
                                text = optString(PARAM_TEXT),
                                image = optString(PARAM_IMAGE),
                                button = optString(PARAM_BUTTON),
                                header = optString(PARAM_HEADER),
                                textEnabled = optString(PARAM_TEXT_ENABLED),
                                imageEnabled = optString(PARAM_IMAGE_ENABLED),
                                headerEnabled = optString(PARAM_HEADER_ENABLED)
                            )
                        }
                    },
                    webPushSystem = popupJson.optBoolean(PARAM_WEB_PUSH_SYSTEM),
                    popupActions = popupJson.optString(PARAM_POPUP_ACTIONS)?.let {
                        JSONObject(it).run {
                            PopupActions(
                                link = optJSONObject(PARAM_LINK)?.let { linkJson ->
                                    Link(
                                        linkIos = linkJson.optString(PARAM_LINK_IOS),
                                        linkWeb = linkJson.optString(PARAM_LINK_WEB),
                                        buttonText = linkJson.optString(PARAM_BUTTON_TEXT),
                                        linkAndroid = linkJson.optString(PARAM_LINK_ANDROID)
                                    )
                                },
                                close = optJSONObject(PARAM_CLOSE)?.let { closeJson ->
                                    CloseAction(
                                        buttonText = closeJson.optString(PARAM_BUTTON_TEXT)
                                    )
                                }
                            )
                        }
                    }
                )
            }

            SdkInitializationResponse(
                did = this.optString(PARAM_DID),
                seance = this.optString(PARAM_SEANCE),
                currency = this.optString(PARAM_CURRENCY),
                emailCollector = this.optBoolean(PARAM_EMAIL_COLLECTOR),
                hasEmail = this.optBoolean(PARAM_HAS_EMAIL),
                recommendations = this.optBoolean(PARAM_RECOMMENDATION),
                lazyLoad = this.optBoolean(PARAM_LAZY_LOAD),
                autoCssRecommender = this.optBoolean(PARAM_AUTO_CSS),
                cms = this.optString(PARAM_CMS),
                snippets = this.optJSONArray(PARAM_SNIPPETS)?.toList() ?: emptyList(),
                popup = popupObject,
                search = null,
                webPushSettings = null,
                recone = this.optBoolean(PARAM_RECONE)
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
}
