package com.personalization.sdk.data.repositories.notification

import com.google.gson.Gson
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
import com.personalization.sdk.data.mappers.notification.NotificationMapper
import com.personalization.sdk.data.models.params.GetAllNotificationsParams
import com.personalization.sdk.data.utils.QueryParamsUtils.formNewJSONWithMultipleParams
import com.personalization.sdk.domain.models.NotificationSource
import com.personalization.sdk.domain.repositories.NotificationRepository
import org.json.JSONObject
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val notificationDataSource: NotificationDataSource,
    private val notificationMapper: NotificationMapper
) : NotificationRepository {

    override fun getNotificationSource(timeDuration: Long): NotificationSource? {
        val notificationSourceDto = notificationDataSource.getNotificationSourceDto()

        if (!isTimeCorrect(notificationSourceDto.time, timeDuration)) {
            return null
        }

        return notificationMapper.toNotificationSource(notificationSourceDto)
    }

    override fun updateNotificationSource(type: String, id: String) {
        notificationDataSource.saveType(type)
        notificationDataSource.saveId(id)
        notificationDataSource.saveTime(System.currentTimeMillis())
    }

    override fun getAllNotificationsParams(
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String,
        type: String,
        channel: String,
        page: Int?,
        limit: Int?
    ): JSONObject = formNewJSONWithMultipleParams(
        mapOf(
            GetAllNotificationsParams.EMAIL to email,
            GetAllNotificationsParams.PHONE to phone,
            GetAllNotificationsParams.EXTERNAL_ID to externalId,
            GetAllNotificationsParams.LOYALTY_ID to loyaltyId,
            GetAllNotificationsParams.DATE_FROM to dateFrom,
            GetAllNotificationsParams.TYPE to type,
            GetAllNotificationsParams.CHANNEL to channel,
            GetAllNotificationsParams.PAGE to page,
            GetAllNotificationsParams.LIMIT to limit,
        )
    )

    override fun getAllNotificationListener(
        onGetAllNotifications: (GetAllNotificationsResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ): OnApiCallbackListener =
        object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val getAllNotificationsResponse =
                        Gson().fromJson(it.toString(), GetAllNotificationsResponse::class.java)
                    onGetAllNotifications(getAllNotificationsResponse)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        }

    private fun isTimeCorrect(time: Long, timeDuration: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        return time > 0 && (time + timeDuration) > currentTime
    }
}
