package com.personalization.features.notification.data.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.personalization.R

object NotificationChannelHelper {

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = context.getString(R.string.notification_channel_id)
            val channelName = context.getString(R.string.notification_channel_name)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(
                /* channel = */ NotificationChannel(
                    /* id = */ channelId,
                    /* name = */ channelName,
                    // HIGH so notification-mode pushes (attach_notification=true) that FCM/HMS
                    // auto-display in the background produce a heads-up pop-up with sound, instead
                    // of landing silently in the shade. Mirrors the React Native SDK reference
                    // (AndroidImportance.HIGH). Same channel id as before.
                    /* importance = */ NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }
}
