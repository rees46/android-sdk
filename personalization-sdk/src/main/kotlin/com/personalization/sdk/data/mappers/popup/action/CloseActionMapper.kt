package com.personalization.sdk.data.mappers.popup.action

import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.sdk.data.models.dto.popUp.CloseAction
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_BUTTON_TEXT
import com.personalization.sdk.data.models.params.SdkInitializationParams.TAG
import org.json.JSONObject

object CloseActionMapper {
    fun map(json: JSONObject): CloseAction? = try {
        CloseAction(
            buttonText = json.optString(PARAM_BUTTON_TEXT)
        )
    } catch (exception: Exception) {
        JsonResponseErrorHandler(TAG, null).logError(
            message = "Error mapping CloseAction",
            exception = exception
        )
        null
    }
}
