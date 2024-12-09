package com.personalization.sdk.data.mappers.popup.action

import com.personalization.sdk.data.models.dto.popUp.PushSubscribe
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_BUTTON_TEXT
import org.json.JSONObject

object PushSubscriptionMapper {
    fun map(json: JSONObject): PushSubscribe = PushSubscribe(
        buttonText = json.optString(PARAM_BUTTON_TEXT)
    )
}
