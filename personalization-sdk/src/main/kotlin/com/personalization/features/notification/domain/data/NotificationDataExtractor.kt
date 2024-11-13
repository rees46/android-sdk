package com.personalization.features.notification.domain.data

import android.os.Bundle
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ID
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TYPE
import javax.inject.Inject

class NotificationDataExtractor @Inject constructor() {

  fun extractData(extras: Bundle?): Pair<String?, String?> {
    val type = extras?.getString(NOTIFICATION_TYPE)
    val code = extras?.getString(NOTIFICATION_ID)
    return Pair(type, code)
  }

}
