package com.personalization.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalization.LogLevel
import com.personalization.Rees46
import com.personalization.core.PushEvent
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

/**
 * The `deleteIntent` of a notification the SDK drew: dismissed without a tap, it is
 * `Rees46.handlePush(push, PushEvent.CLOSED)` — `track/closed` and the `onClosed` callback.
 */
internal class PushDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val push = intent.extras?.let { extras ->
                extras.keySet().mapNotNull { key -> extras.getString(key)?.let { key to it } }.toMap()
            } ?: return
            Rees46.handlePush(push, PushEvent.CLOSED)
        } catch (e: Exception) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "A dismissed notification could not be reported", throwable = e)
        }
    }

    internal companion object {
        /** The intent that reports the dismissal of the notification [notificationId] of [push]. */
        fun intent(context: Context, notificationId: Int, push: Map<String, String>): PendingIntent {
            val intent = Intent(context, PushDismissReceiver::class.java)
            push.forEach { (key, value) -> intent.putExtra(key, value) }
            return PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
