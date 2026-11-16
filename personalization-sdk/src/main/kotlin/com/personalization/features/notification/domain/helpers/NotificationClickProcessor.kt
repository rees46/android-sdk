package com.personalization.features.notification.domain.helpers

import android.os.Bundle
import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.features.notification.domain.data.NotificationDataExtractor
import com.personalization.features.notification.domain.data.NotificationDataSender
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject

class NotificationClickProcessor @Inject constructor(
    private val dataExtractor: NotificationDataExtractor,
    private val dataSender: NotificationDataSender
) {

    fun processClick(
        extras: Bundle?,
        sendAsync: (String, JSONObject) -> Unit,
        onResult: (type: String, code: String) -> Unit
    ) {
        val (type, code) = dataExtractor.extractData(extras = extras)

        if (type == null || code == null) {
            handleError(errorMessage = "Invalid notification data", extras = extras)
            return
        }

        try {
            dataSender.sendAsyncData(
                type = type,
                code = code,
                sendAsync = sendAsync
            )
            onResult(type, code)
        } catch (e: JSONException) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "The click of a push could not be sent", throwable = e)
        }
    }

    private fun handleError(errorMessage: String, extras: Bundle?) {
        // Every SDK push has a type and a code: a click without them is another sender's notification,
        // which an app that hands every click to the SDK passes on as well. Not the extras themselves:
        // they carry the push's content.
        SdkLogs.log(
            LogLevel.DEBUG,
            LogCodes.PUSH_DROPPED,
            "$errorMessage: the clicked push has no type or code",
            context = mapOf("reason" to LogReasons.NOT_SDK_PUSH, "keys" to extras?.keySet()?.sorted())
        )
    }
}
