package com.personalization.sdk.domain.usecases.notification

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.repositories.NetworkRepository
import com.personalization.sdk.domain.repositories.NotificationRepository
import javax.inject.Inject

class GetAllNotificationsUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val networkRepository: NetworkRepository
) {

    fun invoke(
        email: String? = null,
        phone: String? = null,
        loyaltyId: String? = null,
        externalId: String? = null,
        dateFrom: String,
        type: String,
        channel: String,
        page: Int?,
        limit: Int?,
        listener: OnApiCallbackListener?
    ) {
        val params = notificationRepository.getAllNotificationsParams(
            email = email,
            phone = phone,
            loyaltyId = loyaltyId,
            externalId = externalId,
            dateFrom = dateFrom,
            type = type,
            channel = channel,
            page = page,
            limit = limit
        )

        networkRepository.getSecretAsync(
            method = GET_ALL_NOTIFICATIONS_REQUEST,
            params = params.build(),
            listener = listener
        )
    }

    companion object {
        private const val GET_ALL_NOTIFICATIONS_REQUEST = "notifications"
    }
}
