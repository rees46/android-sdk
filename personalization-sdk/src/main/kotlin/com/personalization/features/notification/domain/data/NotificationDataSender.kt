package com.personalization.features.notification.domain.data

import com.personalization.features.notification.domain.model.NotificationConstants.CODE_PARAM
import com.personalization.features.notification.domain.model.NotificationConstants.TRACK_CLICKED
import com.personalization.features.notification.domain.model.NotificationConstants.TYPE_PARAM
import javax.inject.Inject
import org.json.JSONObject

class NotificationDataSender @Inject constructor() {

  fun sendAsyncData(type: String, code: String, sendAsync: (String, JSONObject) -> Unit) {
    val params = JSONObject().apply {
      put(TYPE_PARAM, type)
      put(CODE_PARAM, code)
    }
    sendAsync(TRACK_CLICKED, params)
  }

}
