package com.personalization.sdk.data.mappers.popup

import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.sdk.data.mappers.SdkInitializationMapper.toStringList
import com.personalization.sdk.data.mappers.popup.action.PopupActionsMapper
import com.personalization.sdk.data.mappers.popup.component.ComponentsMapper
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_CHANNELS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_COMPONENTS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_DELAY
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_HTML
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_ID
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_POPUP_ACTIONS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_POSITION
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_WEB_PUSH_SYSTEM
import com.personalization.sdk.data.models.params.SdkInitializationParams.TAG
import org.json.JSONObject

object PopupDtoMapper {
    fun map(json: JSONObject): PopupDto? = try {
        PopupDto(
            id = json.optInt(PARAM_ID),
            channels = json.optJSONArray(PARAM_CHANNELS)?.toStringList() ?: emptyList(),
            position = Position.fromString(json.optString(PARAM_POSITION)) ?: Position.UNKNOWN,
            delay = json.optInt(PARAM_DELAY),
            html = json.optString(PARAM_HTML),
            components = json.optString(PARAM_COMPONENTS)?.let {
                ComponentsMapper.map(JSONObject(it))
            },
            webPushSystem = json.optBoolean(PARAM_WEB_PUSH_SYSTEM),
            popupActions = json.optString(PARAM_POPUP_ACTIONS)?.let {
                PopupActionsMapper.map(JSONObject(it))
            }
        )
    } catch (exception: Exception) {
        JsonResponseErrorHandler(TAG, null).logError(
            message = "Error mapping PopupDto",
            exception = exception
        )
        null
    }
}
