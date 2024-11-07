package com.personalization.features.notification.domain.helpers

import android.os.Bundle
import com.personalization.features.notification.core.ErrorHandler
import com.personalization.sdk.domain.usecases.notification.UpdateNotificationSourceUseCase
import javax.inject.Inject
import org.json.JSONObject

class NotificationClickHandler @Inject constructor(
    private val updateSourceUseCase: UpdateNotificationSourceUseCase
) {

    fun handleNotificationClick(
        extras: Bundle?,
        sendAsync: (String, JSONObject) -> Unit
    ) {
        extras?.let {
            val type = it.getString("NOTIFICATION_TYPE", null)
            val code = it.getString("NOTIFICATION_ID", null)

            if (type != null && code != null) {
                val params = JSONObject().apply {
                    put("type", type)
                    put("code", code)
                }
                sendAsync("track/clicked", params)
                updateSourceUseCase(type, code)
            } else {
                ErrorHandler.logError("Missing notification data: type=$type, code=$code")
            }
        }
    }
}
