package com.personalization.domain.usecases.notification

import com.personalization.domain.repositories.NotificationRepository
import javax.inject.Inject

class UpdateNotificationSourceUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {

    operator fun invoke(
        type: String,
        id: String
    ) = notificationRepository.updateNotificationSource(
            type = type,
            id = id
        )
}
