package com.personalization.sdk.domain.repositories

import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
import com.personalization.sdk.domain.models.NotificationSource
import org.json.JSONObject

interface NotificationRepository {

    fun getNotificationSource(timeDuration: Long): NotificationSource?

    fun updateNotificationSource(type: String, id: String)

    fun getAllNotificationsParams(
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String,
        type: String,
        channel: String,
        page: Int?,
        limit: Int?
    ): JSONObject

    fun getAllNotificationListener(
        onGetAllNotifications: (GetAllNotificationsResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    ): OnApiCallbackListener
}
