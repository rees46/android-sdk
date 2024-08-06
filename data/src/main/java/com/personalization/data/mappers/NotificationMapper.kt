package com.personalization.data.mappers

import com.personalization.data.models.NotificationSourceDto
import com.personalization.domain.models.NotificationSource

class NotificationMapper {

    fun toNotificationSource(notificationSourceDto: NotificationSourceDto): NotificationSource {
        return NotificationSource(
            type = notificationSourceDto.type,
            id = notificationSourceDto.id,
            time = notificationSourceDto.time
        )
    }
}
