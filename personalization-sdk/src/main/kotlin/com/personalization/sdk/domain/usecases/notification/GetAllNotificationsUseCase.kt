package com.personalization.sdk.domain.usecases.notification

import com.personalization.api.params.NotificationChannels
import com.personalization.api.params.NotificationTypes
import com.personalization.api.responses.ResponseResult
import com.personalization.api.responses.SDKErrorResponse
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
import com.personalization.sdk.domain.repositories.NetworkRepository
import com.personalization.sdk.domain.repositories.NotificationRepository
import com.personalization.utils.EnumUtils
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class GetAllNotificationsUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val networkRepository: NetworkRepository
) {

    /**
     * Notification history of a user (`GET /notifications`).
     *
     * The endpoint authenticates the shop by its secret key; without [shopSecret] the API
     * answers 400 "Incorrect shop credentials". [shopSecret] comes after the paging
     * parameters so that a call written for the variant without it can never bind here.
     */
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
        shopSecret: String,
        onGetAllNotifications: (GetAllNotificationsResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    ) {
        request(
            email, phone, loyaltyId, externalId, dateFrom, types, channels, page, limit,
            shopSecret, onGetAllNotifications, onError
        )
    }

    /** [invoke] with the result as a flow. */
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
        shopSecret: String
    ): StateFlow<ResponseResult<GetAllNotificationsResponse>> =
        requestFlow(email, phone, loyaltyId, externalId, dateFrom, types, channels, page, limit, shopSecret)

    @Deprecated(
        message = "The API rejects the request without the shop secret. Use the variant with shopSecret.",
        replaceWith = ReplaceWith(
            "invoke(email, phone, loyaltyId, externalId, dateFrom, types, channels, page, limit, " +
                "shopSecret, onGetAllNotifications, onError)"
        )
    )
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
        request(
            email, phone, loyaltyId, externalId, dateFrom, types, channels, page, limit,
            null, onGetAllNotifications, onError
        )
    }

    @Deprecated(
        message = "The API rejects the request without the shop secret. Use the variant with shopSecret.",
        replaceWith = ReplaceWith(
            "invoke(email, phone, loyaltyId, externalId, dateFrom, types, channels, page, limit, shopSecret)"
        )
    )
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
    ): StateFlow<ResponseResult<GetAllNotificationsResponse>> =
        requestFlow(email, phone, loyaltyId, externalId, dateFrom, types, channels, page, limit, null)

    private fun request(
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String,
        types: NotificationTypes,
        channels: NotificationChannels,
        page: Int,
        limit: Int,
        shopSecret: String?,
        onGetAllNotifications: (GetAllNotificationsResponse) -> Unit,
        onError: (Int, String?) -> Unit
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
        shopSecret?.let { params.put(SHOP_SECRET_PARAM, it) }

        val listener = notificationRepository.getAllNotificationListener(
            onGetAllNotifications = onGetAllNotifications,
            onError = onError
        )

        networkRepository.getSecretAsync(
            method = GET_ALL_NOTIFICATIONS_REQUEST,
            params = params,
            listener = listener
        )
    }

    private fun requestFlow(
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String,
        types: NotificationTypes,
        channels: NotificationChannels,
        page: Int,
        limit: Int,
        shopSecret: String?
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
                error = SDKErrorResponse(code, message)
            )

            resultFlow.update {
                result
            }
        }

        request(
            email, phone, loyaltyId, externalId, dateFrom, types, channels, page, limit,
            shopSecret, onGetAllNotifications, onError
        )

        return resultFlow
    }

    companion object {
        private const val GET_ALL_NOTIFICATIONS_REQUEST = "notifications"
        private const val SHOP_SECRET_PARAM = "shop_secret"

        private const val DEFAULT_PAGE = 1
        private const val DEFAULT_LIMIT = 20
    }
}
