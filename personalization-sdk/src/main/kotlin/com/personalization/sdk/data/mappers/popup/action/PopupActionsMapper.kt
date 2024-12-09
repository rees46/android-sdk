package com.personalization.sdk.data.mappers.popup.action

import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.sdk.data.mappers.popup.link.LinkMapper
import com.personalization.sdk.data.models.dto.popUp.PopupActions
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_CLOSE
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_LINK
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_PUSH_SUBSCRIPTION
import com.personalization.sdk.data.models.params.SdkInitializationParams.TAG
import org.json.JSONObject

object PopupActionsMapper {
    fun map(json: JSONObject): PopupActions? = try {
        PopupActions(
            link = json.optJSONObject(PARAM_LINK)?.let { LinkMapper.map(it) },
            close = json.optJSONObject(PARAM_CLOSE)?.let { CloseActionMapper.map(it) },
            pushSubscribe = json.optJSONObject(PARAM_PUSH_SUBSCRIPTION)
                ?.let { PushSubscriptionMapper.map(it) }
        )
    } catch (exception: Exception) {
        JsonResponseErrorHandler(TAG, null).logError(
            message = "Error mapping PopupActions",
            exception = exception
        )
        null
    }
}
