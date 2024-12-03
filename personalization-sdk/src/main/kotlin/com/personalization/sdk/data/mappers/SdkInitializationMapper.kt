package com.personalization.sdk.data.mappers

import com.personalization.api.responses.initialization.SdkInitializationResponse
import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.sdk.data.mappers.popup.PopupDtoMapper
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_AUTO_CSS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_CMS
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_CURRENCY
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_DID
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_EMAIL_COLLECTOR
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_HAS_EMAIL
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_LAZY_LOAD
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_POPUP
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_RECOMMENDATION
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_RECONE
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_SEANCE
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_SNIPPETS
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
                popupDto = this.optJSONObject(PARAM_POPUP)?.let { PopupDtoMapper.map(it) },
                search = null,
                webPushSettings = null,
                recone = this.optBoolean(PARAM_RECONE)
            )
        } catch (exception: Exception) {
            JsonResponseErrorHandler(TAG, null).logError(
                message = "Error caught in mapToSdkInitResponse",
                exception = exception
            )
            null
        }
    }

    fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until this.length()) {
            list.add(this.optString(i))
        }
        return list
    }
}
