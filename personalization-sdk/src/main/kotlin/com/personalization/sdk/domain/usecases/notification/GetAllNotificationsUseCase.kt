package com.personalization.sdk.domain.usecases.notification

import com.google.gson.Gson
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.params.NotificationChannels
import com.personalization.api.params.NotificationTypes
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
import com.personalization.sdk.domain.repositories.NetworkRepository
import com.personalization.sdk.domain.repositories.NotificationRepository
import org.json.JSONObject
import java.util.EnumSet
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
        types: NotificationTypes,
        channels: NotificationChannels,
        page: Int = DEFAULT_PAGE,
        limit: Int = DEFAULT_LIMIT,
        onGetAllNotifications: (GetAllNotificationsResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    ) {
        val type = getEnumsString(types)
        val channel = getEnumsString(channels)

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

    private inline fun <reified T : Enum<T>> getEnumsString(enums: EnumSet<T>): String {
        val string = StringBuilder()

        for (enum in enumValues<T>()) {
            if(enums.contains(enum)) {
                if(string.isNotEmpty()) {
                    string.append(',')
                }
                string.append(enum.toString())
            }
        }

        return string.toString()
    }

    companion object {
        private const val GET_ALL_NOTIFICATIONS_REQUEST = "notifications"

        private const val DEFAULT_PAGE = 1
        private const val DEFAULT_LIMIT = 20
    }
}
