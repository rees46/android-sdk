package com.personalizatio.domain.usecases.notification

import com.personalizatio.domain.repositories.NotificationRepository
import javax.inject.Inject

class GetNotificationSourceUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {

    fun execute(
        timeDuration: Int
    ) = notificationRepository.getNotificationSource(
        timeDuration = timeDuration
    )
}
