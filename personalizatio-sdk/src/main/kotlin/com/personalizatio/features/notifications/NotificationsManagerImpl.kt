package com.personalizatio.features.notifications

import com.google.gson.Gson
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.managers.NetworkManager
import com.personalizatio.api.managers.NotificationsManager
import com.personalizatio.api.responses.notifications.GetAllNotificationsResponse
import org.json.JSONObject

internal class NotificationsManagerImpl(private val networkManager: NetworkManager) : NotificationsManager {

    override fun getAllNotifications(
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String,
        type: String,
        channel: String,
        page: Int?,
        limit: Int?,
        onGetAllNotifications: (GetAllNotificationsResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getAllNotifications(email, phone, loyaltyId, externalId, dateFrom, type, channel, page, limit, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val getAllNotificationsResponse = Gson().fromJson(it.toString(), GetAllNotificationsResponse::class.java)
                    onGetAllNotifications(getAllNotificationsResponse)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    override fun getAllNotifications(
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String,
        type: String,
        channel: String,
        page: Int?,
        limit: Int?,
        listener: OnApiCallbackListener?
    ) {
        val params = com.personalizatio.Params()
        if (email != null) {
            params.put(GetAllNotificationsParameter.EMAIL, email)
        }
        if (phone != null) {
            params.put(GetAllNotificationsParameter.PHONE, phone)
        }
        if (externalId != null) {
            params.put(GetAllNotificationsParameter.EXTERNAL_ID, externalId)
        }
        if (loyaltyId != null) {
            params.put(GetAllNotificationsParameter.LOYALTY_ID, loyaltyId)
        }
        params.put(GetAllNotificationsParameter.DATE_FROM, dateFrom)
        params.put(GetAllNotificationsParameter.TYPE, type)
        params.put(GetAllNotificationsParameter.CHANNEL, channel)
        if (page != null) {
            params.put(GetAllNotificationsParameter.PAGE, page)
        }
        if (limit != null) {
            params.put(GetAllNotificationsParameter.LIMIT, limit)
        }

        networkManager.getSecretAsync(GET_ALL_NOTIFICATIONS_REQUEST, params.build(), listener)
    }

    companion object {
        const val GET_ALL_NOTIFICATIONS_REQUEST = "notifications"
    }
}
