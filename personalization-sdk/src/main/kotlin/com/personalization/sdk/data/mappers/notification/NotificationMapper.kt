package com.personalization.sdk.data.mappers.notification

import com.personalization.sdk.data.models.NotificationSourceDto
import com.personalization.sdk.domain.models.NotificationSource

class NotificationMapper {

    fun toNotificationSource(notificationSourceDto: NotificationSourceDto): NotificationSource {
        return NotificationSource(
            type = notificationSourceDto.type,
            id = notificationSourceDto.id,
            time = notificationSourceDto.time
        )
    }
}
