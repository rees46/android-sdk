package com.personalization.sdk.data.mappers.popup.component

import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.sdk.data.models.dto.popUp.Components
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_BUTTON
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_HEADER
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_HEADER_ENABLED
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_IMAGE
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_IMAGE_ENABLED
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_TEXT
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_TEXT_ENABLED
import com.personalization.sdk.data.models.params.SdkInitializationParams.TAG
import org.json.JSONObject

object ComponentsMapper {
    fun map(json: JSONObject): Components? = try {
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
            message = "Error mapping Components",
            exception = exception
        )
        null
    }
}
