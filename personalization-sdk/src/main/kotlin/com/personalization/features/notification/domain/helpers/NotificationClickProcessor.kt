package com.personalization.features.notification.domain.helpers

import android.os.Bundle
import com.personalization.errors.ParsingJsonError
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
    val (type, code) = dataExtractor.extractData(extras)

    if (type == null || code == null) {
      handleError("Invalid notification data", extras)
      return
    }

    try {
      dataSender.sendAsyncData(type, code, sendAsync)
      onResult(type, code)
    } catch (e: JSONException) {
      ParsingJsonError(
        tag = this@NotificationClickProcessor.javaClass.name,
        functionName = "processClick",
        message = e.message.orEmpty()
      ).logError()
    }
  }

  private fun handleError(message: String, extras: Bundle?) {
    //TODO Add error logging
  }
}
