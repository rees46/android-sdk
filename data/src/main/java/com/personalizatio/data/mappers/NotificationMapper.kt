package com.personalizatio.data.mappers

import com.personalizatio.data.models.NotificationSourceDto
import com.personalizatio.domain.models.NotificationSource
import org.json.JSONObject

class NotificationMapper {

    fun toNotificationSource(notificationSourceDto: NotificationSourceDto): NotificationSource {
        return NotificationSource(
            jsonObject = JSONObject()
                .put("from", notificationSourceDto.type)
                .put("code", notificationSourceDto.id)
        )
    }
}
