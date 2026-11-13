package com.personalization.sdk.data.repositories.notification

import com.google.gson.Gson
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
import com.personalization.sdk.data.models.params.GetAllNotificationsParams
import com.personalization.sdk.data.utils.QueryParamsUtils.formNewJSONWithMultipleParams
import com.personalization.sdk.domain.repositories.NotificationRepository
import org.json.JSONObject
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor() : NotificationRepository {

    override fun getAllNotificationsParams(
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String,
        type: String,
        channel: String,
        page: Int?,
        limit: Int?
    ): JSONObject = formNewJSONWithMultipleParams(
        mapOf(
            GetAllNotificationsParams.EMAIL to email,
            GetAllNotificationsParams.PHONE to phone,
            GetAllNotificationsParams.EXTERNAL_ID to externalId,
            GetAllNotificationsParams.LOYALTY_ID to loyaltyId,
            GetAllNotificationsParams.DATE_FROM to dateFrom,
            GetAllNotificationsParams.TYPE to type,
            GetAllNotificationsParams.CHANNEL to channel,
            GetAllNotificationsParams.PAGE to page,
            GetAllNotificationsParams.LIMIT to limit,
        )
    )

    override fun getAllNotificationListener(
        onGetAllNotifications: (GetAllNotificationsResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ): OnApiCallbackListener =
        object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val getAllNotificationsResponse =
                        Gson().fromJson(it.toString(), GetAllNotificationsResponse::class.java)
                    onGetAllNotifications(getAllNotificationsResponse)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        }
}
