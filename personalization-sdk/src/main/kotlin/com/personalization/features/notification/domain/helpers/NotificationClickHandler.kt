package com.personalization.features.notification.domain.helpers

import android.os.Bundle
import com.personalization.errors.ParsingJsonError
import com.personalization.features.notification.domain.model.NotificationConstants.CODE_PARAM
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ID
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TYPE
import com.personalization.features.notification.domain.model.NotificationConstants.TRACK_CLICKED
import com.personalization.features.notification.domain.model.NotificationConstants.TYPE_PARAM
import org.json.JSONException
import org.json.JSONObject

object NotificationClickHandler {

    fun handleNotificationClick(
        extras: Bundle?,
        sendAsync: (String, JSONObject) -> Unit,
        onResult: (type: String, code: String) -> Unit
    ) {
        if (extras == null) {
            return
        } else {
            val type = extras.getString(NOTIFICATION_TYPE, null)
            val code = extras.getString(NOTIFICATION_ID, null)

            if (type != null && code != null) {
                val params = JSONObject()
                try {
                    params.put(TYPE_PARAM, type)
                    params.put(CODE_PARAM, code)
                    sendAsync(TRACK_CLICKED, params)

                    onResult(type, code)
                } catch (jsonException: JSONException) {
                    ParsingJsonError(
                        tag = this@NotificationClickHandler.javaClass.name,
                        functionName = "handleNotificationClick",
                        message = jsonException.message.orEmpty()
                    ).logError()
                }
            }
        }
    }
}
