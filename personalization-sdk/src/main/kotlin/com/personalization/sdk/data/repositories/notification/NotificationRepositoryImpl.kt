package com.personalization.sdk.data.repositories.notification

import com.google.gson.Gson
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
import com.personalization.sdk.data.mappers.NotificationMapper
import com.personalization.sdk.data.models.params.GetAllNotificationsParams
import com.personalization.sdk.data.utils.ParamsEnumUtils.addOptionalParam
import com.personalization.sdk.domain.models.NotificationSource
import com.personalization.sdk.domain.repositories.NotificationRepository
import org.json.JSONObject
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val notificationDataSource: NotificationDataSource,
    private val notificationMapper: NotificationMapper
) : NotificationRepository {

    override fun getNotificationSource(timeDuration: Int): NotificationSource? {
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
    ): JSONObject {
        val params = JSONObject()

        addOptionalParam(params, GetAllNotificationsParams.EMAIL, email)
        addOptionalParam(params, GetAllNotificationsParams.PHONE, phone)
        addOptionalParam(params, GetAllNotificationsParams.EXTERNAL_ID, externalId)
        addOptionalParam(params, GetAllNotificationsParams.LOYALTY_ID, loyaltyId)
        params.put(GetAllNotificationsParams.DATE_FROM, dateFrom)
        params.put(GetAllNotificationsParams.TYPE, type)
        params.put(GetAllNotificationsParams.CHANNEL, channel)
        addOptionalParam(params, GetAllNotificationsParams.PAGE, page)
        addOptionalParam(params, GetAllNotificationsParams.LIMIT, limit)

        return params
    }

    override fun getAllNotificationListener(
        onGetAllNotifications: (GetAllNotificationsResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) : OnApiCallbackListener =
        object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val getAllNotificationsResponse = Gson().fromJson(it.toString(), GetAllNotificationsResponse::class.java)
                    onGetAllNotifications(getAllNotificationsResponse)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        }

    private fun isTimeCorrect(time: Long, timeDuration: Int): Boolean {
        val currentTime = System.currentTimeMillis()
        return time > 0 && (time + timeDuration) > currentTime
    }
}
