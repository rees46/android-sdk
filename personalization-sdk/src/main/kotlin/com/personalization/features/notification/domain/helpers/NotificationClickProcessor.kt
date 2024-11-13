package com.personalization.features.notification.domain.helpers

import android.os.Bundle
import com.personalization.errors.EmptyFieldError
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
      ParsingJsonError(
        tag = this@NotificationClickProcessor.javaClass.name,
        functionName = "processClick",
        message = e.message.orEmpty()
      ).logError()
    }
  }

  private fun handleError(errorMessage: String, extras: Bundle?) {
    EmptyFieldError(
      tag = TAG,
      functionName = FUNCTION_NAME,
      message = errorMessage + extras
    ).logError()
  }

  companion object{
    private const val TAG = "NotificationClickProcessor"
    private const val FUNCTION_NAME = "processClick"
  }
}
