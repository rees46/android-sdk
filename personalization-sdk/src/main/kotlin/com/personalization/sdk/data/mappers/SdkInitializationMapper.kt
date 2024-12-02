package com.personalization.sdk.data.mappers

import com.personalization.api.responses.initialization.SdkInitializationResponse
import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.sdk.data.models.dto.popUp.CloseAction
import com.personalization.sdk.data.models.dto.popUp.Components
import com.personalization.sdk.data.models.dto.popUp.Link
import com.personalization.sdk.data.models.dto.popUp.PopupActions
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_AUTO_CSS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_BUTTON
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_BUTTON_TEXT
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_CHANNELS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_CLOSE
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_CMS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_COMPONENTS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_CURRENCY
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_DELAY
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_DID
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_EMAIL_COLLECTOR
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_HAS_EMAIL
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_HEADER
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_HEADER_ENABLED
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_HTML
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_ID
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_IMAGE
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_IMAGE_ENABLED
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_LAZY_LOAD
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_LINK
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_LINK_ANDROID
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_LINK_IOS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_LINK_WEB
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_POPUP
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_POPUP_ACTIONS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_POSITION
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_RECOMMENDATION
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_RECONE
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_SEANCE
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_SNIPPETS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_TEXT
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_TEXT_ENABLED
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_WEB_PUSH_SYSTEM
import com.personalization.sdk.data.models.params.SdkInitializationParams.TAG
import org.json.JSONArray
import org.json.JSONObject


object SdkInitializationMapper {

    fun JSONObject.mapToSdkInitResponse(): SdkInitializationResponse? {
        return try {
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
                snippets = this.optJSONArray(PARAM_SNIPPETS)?.toStringList() ?: emptyList(),
                popupDto = this.optJSONObject(PARAM_POPUP)?.mapToPopupDto(),
                search = null, // Placeholder for search mapping if needed
                webPushSettings = null, // Placeholder for web push settings if needed
                recone = this.optBoolean(PARAM_RECONE)
            )
        } catch (exception: Exception) {
            JsonResponseErrorHandler(TAG, null).logError(
                "Error caught in mapToSdkInitResponse",
                exception
            )
            null
        }
    }

    private fun JSONObject.mapToPopupDto(): PopupDto? = try {
        PopupDto(
            id = this.optInt(PARAM_ID),
            channels = this.optJSONArray(PARAM_CHANNELS)?.toStringList() ?: emptyList(),
            position = Position.fromString(this.optString(PARAM_POSITION)) ?: Position.UNKNOWN,
            delay = this.optInt(PARAM_DELAY),
            html = this.optString(PARAM_HTML),
            components = this.optString(PARAM_COMPONENTS)?.toComponents(),
            webPushSystem = this.optBoolean(PARAM_WEB_PUSH_SYSTEM),
            popupActions = this.optString(PARAM_POPUP_ACTIONS)?.toPopupActions()
        )
    } catch (exception: Exception) {
        JsonResponseErrorHandler(TAG, null).logError(
            "Error mapping PopupDto",
            exception
        )
        null
    }

    private fun String.toComponents(): Components? = try {
        val json = JSONObject(this)
        Components(
            text = json.optString(PARAM_TEXT),
            image = json.optString(PARAM_IMAGE),
            button = json.optString(PARAM_BUTTON),
            header = json.optString(PARAM_HEADER),
            textEnabled = json.optString(PARAM_TEXT_ENABLED),
            imageEnabled = json.optString(PARAM_IMAGE_ENABLED),
            headerEnabled = json.optString(PARAM_HEADER_ENABLED)
        )
    } catch (exception: Exception) {
        JsonResponseErrorHandler(TAG, null).logError(
            "Error mapping Components",
            exception
        )
        null
    }

    private fun String.toPopupActions(): PopupActions? = try {
        val json = JSONObject(this)
        PopupActions(
            link = json.optJSONObject(PARAM_LINK)?.toLink(),
            close = json.optJSONObject(PARAM_CLOSE)?.toCloseAction()
        )
    } catch (exception: Exception) {
        JsonResponseErrorHandler(TAG, null).logError(
            "Error mapping PopupActions",
            exception
        )
        null
    }

    private fun JSONObject.toLink(): Link? = try {
        Link(
            linkIos = this.optString(PARAM_LINK_IOS),
            linkWeb = this.optString(PARAM_LINK_WEB),
            buttonText = this.optString(PARAM_BUTTON_TEXT),
            linkAndroid = this.optString(PARAM_LINK_ANDROID)
        )
    } catch (exception: Exception) {
        JsonResponseErrorHandler(TAG, null).logError(
            "Error mapping Link",
            exception
        )
        null
    }

    private fun JSONObject.toCloseAction(): CloseAction? = try {
        CloseAction(
            buttonText = this.optString(PARAM_BUTTON_TEXT)
        )
    } catch (exception: Exception) {
        JsonResponseErrorHandler(TAG, null).logError(
            "Error mapping CloseAction",
            exception
        )
        null
    }

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until this.length()) {
            list.add(this.optString(i))
        }
        return list
    }
}

