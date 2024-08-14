package com.personalization.sdk.domain.usecases.notification

import com.google.gson.Gson
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
import com.personalization.sdk.domain.repositories.NetworkRepository
import com.personalization.sdk.domain.repositories.NotificationRepository
import org.json.JSONObject
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
        onGetAllNotifications: (GetAllNotificationsResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
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
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    response?.let {
                        val getAllNotificationsResponse = Gson().fromJson(it.toString(), GetAllNotificationsResponse::class.java)
                        onGetAllNotifications(getAllNotificationsResponse)
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    companion object {
        private const val GET_ALL_NOTIFICATIONS_REQUEST = "notifications"
    }
}
