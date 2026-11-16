package com.personalization.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalization.LogLevel
import com.personalization.Rees46
import com.personalization.SdkRegistry
import com.personalization.core.PushEvent
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.Executor

/**
 * The `deleteIntent` of a notification the SDK drew on the new core: dismissed without a tap, it is
 * `Rees46.handlePush(push, PushEvent.CLOSED)` — `track/closed` and the `onClosed` callback, for the shop the
 * push names, with the whole push ([PushExtras]).
 *
 * The work runs off the main thread ([goAsync]): the broadcast ends once the event is on the disk of the
 * shop's queue, or the SDK gave it up, so a process the dismissal started is not stopped before.
 */
internal class PushDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val push = PushExtras.read(intent) ?: return
        // Null when onReceive is called outside a broadcast.
        val pending: PendingResult? = goAsync()
        try {
            work.execute {
                try {
                    report(push)
                } finally {
                    pending?.finish()
                }
            }
        } catch (e: Exception) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "A dismissed notification could not be reported", push["shop_id"], throwable = e)
            pending?.finish()
        }
    }

    internal companion object {
        /** How long the broadcast waits for the event to reach the disk: well inside the system's 10 s. */
        const val WRITE_WAIT_MS = 5_000L

        /** Where the dismissal is handled; a test runs it in place. */
        @Volatile
        internal var work: Executor = Dispatchers.IO.asExecutor()

        /** Hands the dismissal of [push] to its shop and returns once the event is written or given up. */
        internal fun report(push: Map<String, String>) {
            try {
                val shopId = Rees46.handlePush(push, PushEvent.CLOSED) ?: return
                SdkRegistry.byShopId(shopId)?.awaitEventsWritten(WRITE_WAIT_MS)
            } catch (e: Exception) {
                SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "A dismissed notification could not be reported", push["shop_id"], throwable = e)
            }
        }

        /** The intent that reports the dismissal of the notification [notificationId] of [push]. */
        fun intent(context: Context, notificationId: Int, push: Map<String, String>): PendingIntent {
            val intent = Intent(context, PushDismissReceiver::class.java)
            PushExtras.put(intent, push)
            return PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
