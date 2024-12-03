package com.personalization.sdk.data.mappers.popup.link

import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.sdk.data.models.dto.popUp.Link
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_BUTTON_TEXT
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_LINK_ANDROID
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_LINK_IOS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_LINK_WEB
import com.personalization.sdk.data.models.params.SdkInitializationParams.TAG
import org.json.JSONObject

object LinkMapper {
    fun map(json: JSONObject): Link? = try {
        Link(
            linkIos = json.optString(PARAM_LINK_IOS),
            linkWeb = json.optString(PARAM_LINK_WEB),
            buttonText = json.optString(PARAM_BUTTON_TEXT),
            linkAndroid = json.optString(PARAM_LINK_ANDROID)
        )
    } catch (exception: Exception) {
        JsonResponseErrorHandler(TAG, null).logError(
            message = "Error mapping Link",
            exception = exception
        )
        null
    }
}
