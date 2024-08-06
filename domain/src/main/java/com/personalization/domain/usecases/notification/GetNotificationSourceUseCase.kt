package com.personalization.domain.usecases.notification

import com.personalization.domain.repositories.NotificationRepository
import javax.inject.Inject

class GetNotificationSourceUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {

    fun invoke(
        timeDuration: Int
    ) = notificationRepository.getNotificationSource(
        timeDuration = timeDuration
    )
}
