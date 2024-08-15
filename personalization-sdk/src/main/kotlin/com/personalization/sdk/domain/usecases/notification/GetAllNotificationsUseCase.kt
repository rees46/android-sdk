package com.personalization.sdk.domain.usecases.notification

import com.google.gson.Gson
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.params.NotificationChannels
import com.personalization.api.params.NotificationTypes
import com.personalization.api.responses.ResponseResult
import com.personalization.api.responses.SDKError
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
import com.personalization.sdk.domain.repositories.NetworkRepository
import com.personalization.sdk.domain.repositories.NotificationRepository
import com.personalization.utils.EnumUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
        types: NotificationTypes,
        channels: NotificationChannels,
        page: Int = DEFAULT_PAGE,
        limit: Int = DEFAULT_LIMIT,
        onGetAllNotifications: (GetAllNotificationsResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    ) {
        val type = EnumUtils.getEnumsString(types)
        val channel = EnumUtils.getEnumsString(channels)

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

    fun invoke(
        email: String? = null,
        phone: String? = null,
        loyaltyId: String? = null,
        externalId: String? = null,
        dateFrom: String,
        types: NotificationTypes,
        channels: NotificationChannels,
        page: Int = DEFAULT_PAGE,
        limit: Int = DEFAULT_LIMIT
    ): StateFlow<ResponseResult<GetAllNotificationsResponse>> {

        val resultFlow = MutableStateFlow(ResponseResult<GetAllNotificationsResponse>())

        val onGetAllNotifications = { response: GetAllNotificationsResponse ->
            val result = ResponseResult(
                response = response
            )

            resultFlow.update {
                result
            }
        }

        val onError = { code: Int, message: String? ->
            val result = ResponseResult<GetAllNotificationsResponse>(
                error = SDKError(code, message)
            )

            resultFlow.update {
                result
            }
        }

        invoke(
            email = email,
            phone = phone,
            loyaltyId = loyaltyId,
            externalId = externalId,
            dateFrom = dateFrom,
            types = types,
            channels = channels,
            page = page,
            limit = limit,
            onGetAllNotifications = onGetAllNotifications,
            onError = onError
        )

        return resultFlow
    }

    companion object {
        private const val GET_ALL_NOTIFICATIONS_REQUEST = "notifications"

        private const val DEFAULT_PAGE = 1
        private const val DEFAULT_LIMIT = 20
    }
}
