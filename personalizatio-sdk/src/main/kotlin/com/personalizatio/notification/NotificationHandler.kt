package com.personalizatio.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.firebase.messaging.RemoteMessage
import com.personalizatio.R
import com.personalizatio.domain.usecases.notification.UpdateNotificationSourceUseCase
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class NotificationHandler @Inject constructor(
    private val updateSourceUseCase: UpdateNotificationSourceUseCase
) {

    private lateinit var context: Context

    internal fun initialize(context: Context) {
        this.context = context

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = context.getString(R.string.notification_channel_id)
            val channelName = context.getString(R.string.notification_channel_name)
            val notificationManager = context.getSystemService(
                NotificationManager::class.java
            )
            notificationManager?.createNotificationChannel(
                /* channel = */ NotificationChannel(
                    /* id = */ channelId,
                    /* name = */ channelName,
                    /* importance = */ NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    fun notificationClicked(
        extras: Bundle?,
        sendAsync: (String, JSONObject) -> Unit
    ) {
        if (extras == null) {
            return
        } else {
            val type = extras.getString(NOTIFICATION_TYPE, null)
            val code = extras.getString(NOTIFICATION_ID, null)

            if (type != null && code != null) {
                val params = JSONObject()
                try {
                    params.put(TYPE_PARAM, type)
                    params.put(CODE_PARAM, code)
                    sendAsync(TRACK_CLICKED, params)

                    updateSourceUseCase(
                        type = type,
                        id = code
                    )
                } catch (e: JSONException) {
                    Log.e(TAG, e.message, e)
                }
            }
        }
    }

    fun prepareData(remoteMessage: RemoteMessage): MutableMap<String, String> {
        val data: MutableMap<String, String> = HashMap(remoteMessage.data)
        remoteMessage.notification?.let { notification ->
            addNotificationData(notification = notification, data = data)
        }
        data[IMAGES_FIELD]?.takeIf { it.isNotEmpty() }?.let { data[IMAGES_FIELD] = it }
        data[ANALYTICS_LABEL_FIELD]?.takeIf { it.isNotEmpty() }?.let { data[ANALYTICS_LABEL_FIELD] = it }
        return data
    }

    private fun addNotificationData(
        notification: RemoteMessage.Notification,
        data: MutableMap<String, String>
    ) {
        notification.title?.takeIf { it.isNotEmpty() }?.let { data[TITLE_FIELD] = it }
        notification.body?.takeIf { it.isNotEmpty() }?.let { data[BODY_FIELD] = it }
        notification.imageUrl?.let { data[IMAGE_FIELD] = it.toString() }
    }

    private fun error(message: String?, exception: Exception? = null) {
        Log.e(TAG, message, exception)
    }

    companion object {
        private const val NOTIFICATION_TYPE = "NOTIFICATION_TYPE"
        private const val NOTIFICATION_ID = "NOTIFICATION_ID"
        private const val TRACK_CLICKED = "track/clicked"
        private const val TAG = "NotificationHandler"
        private const val IMAGES_FIELD = "images"
        private const val TITLE_FIELD = "title"
        private const val IMAGE_FIELD = "image"
        private const val ANALYTICS_LABEL_FIELD = "analytics_label"
        private const val BODY_FIELD = "body"
        private const val TYPE_PARAM = "type"
        private const val CODE_PARAM = "code"
    }
}
