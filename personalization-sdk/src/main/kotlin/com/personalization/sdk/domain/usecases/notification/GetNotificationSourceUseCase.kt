package com.personalization.sdk.domain.usecases.notification

import com.personalization.sdk.domain.repositories.NotificationRepository
import javax.inject.Inject

class GetNotificationSourceUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {

    fun invoke(
        timeDuration: Long
    ) = notificationRepository.getNotificationSource(
        timeDuration = timeDuration
    )
}
