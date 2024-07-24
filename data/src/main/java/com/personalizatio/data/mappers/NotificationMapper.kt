package com.personalizatio.data.mappers

import com.personalizatio.data.models.NotificationSourceDto
import com.personalizatio.domain.models.NotificationSource

class NotificationMapper {

    fun toNotificationSource(notificationSourceDto: NotificationSourceDto): NotificationSource {
        return NotificationSource(
            type = notificationSourceDto.type,
            id = notificationSourceDto.id,
            time = notificationSourceDto.time
        )
    }
}
