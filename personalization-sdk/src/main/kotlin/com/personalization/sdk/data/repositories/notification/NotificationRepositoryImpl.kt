package com.personalization.sdk.data.repositories.notification

import com.personalization.Params
import com.personalization.sdk.data.mappers.NotificationMapper
import com.personalization.sdk.domain.models.NotificationSource
import com.personalization.sdk.domain.repositories.NotificationRepository
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
    ): Params {
        val params = Params()
        if (email != null) {
            params.put(GET_ALL_NOTIFICATIONS_EMAIL_PARAMS, email)
        }
        if (phone != null) {
            params.put(GET_ALL_NOTIFICATIONS_PHONE_PARAMS, phone)
        }
        if (externalId != null) {
            params.put(GET_ALL_NOTIFICATIONS_EXTERNAL_ID_PARAMS, externalId)
        }
        if (loyaltyId != null) {
            params.put(GET_ALL_NOTIFICATIONS_LOYALTY_ID_PARAMS, loyaltyId)
        }
        params.put(GET_ALL_NOTIFICATIONS_DATE_FROM_PARAMS, dateFrom)
        params.put(GET_ALL_NOTIFICATIONS_TYPE_PARAMS, type)
        params.put(GET_ALL_NOTIFICATIONS_CHANNEL_PARAMS, channel)
        if (page != null) {
            params.put(GET_ALL_NOTIFICATIONS_PAGE_PARAMS, page)
        }
        if (limit != null) {
            params.put(GET_ALL_NOTIFICATIONS_LIMIT_PARAMS, limit)
        }

        return params
    }

    private fun isTimeCorrect(time: Long, timeDuration: Int): Boolean {
        val currentTime = System.currentTimeMillis()
        return time > 0 && (time + timeDuration) > currentTime
    }

    companion object {

        private const val GET_ALL_NOTIFICATIONS_EMAIL_PARAMS = "email"
        private const val GET_ALL_NOTIFICATIONS_PHONE_PARAMS = "phone"
        private const val GET_ALL_NOTIFICATIONS_EXTERNAL_ID_PARAMS = "external_id"
        private const val GET_ALL_NOTIFICATIONS_LOYALTY_ID_PARAMS = "loyalty_id"
        private const val GET_ALL_NOTIFICATIONS_DATE_FROM_PARAMS = "date_from"
        private const val GET_ALL_NOTIFICATIONS_TYPE_PARAMS = "type"
        private const val GET_ALL_NOTIFICATIONS_CHANNEL_PARAMS = "channel"
        private const val GET_ALL_NOTIFICATIONS_PAGE_PARAMS = "page"
        private const val GET_ALL_NOTIFICATIONS_LIMIT_PARAMS = "limit"
    }
}
