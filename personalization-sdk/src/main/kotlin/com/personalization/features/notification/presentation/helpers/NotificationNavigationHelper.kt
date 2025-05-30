package com.personalization.features.notification.presentation.helpers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.personalization.features.notification.domain.model.NotificationConstants.CURRENT_IMAGE_INDEX
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_IMAGE
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.features.notification.service.NotificationService
import com.personalization.sdk.data.models.dto.notification.NotificationData
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

object NotificationNavigationHelper {

    private val requestCodeCounter = AtomicInteger(0)

    fun createNavigationPendingIntent(
        context: Context,
        data: NotificationData,
        newIndex: Int,
        action: String
    ): PendingIntent {
        val intent = Intent(context, NotificationService::class.java).apply {
            this.action = action
            putExtra(CURRENT_IMAGE_INDEX, newIndex)
            putExtra(NOTIFICATION_TITLE, data.title)
            putExtra(NOTIFICATION_BODY, data.body)
            putExtra(NOTIFICATION_IMAGE, data.image)
        }

        return PendingIntent.getService(
            /* context = */ context,
            /* requestCode = */ generateRequestCode(action, newIndex),
            /* intent = */ intent,
            /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun createRetryPendingIntent(
        context: Context,
        data: NotificationData
    ): PendingIntent {
        val retryIntent = Intent(context, NotificationService::class.java).apply {
            putExtra(CURRENT_IMAGE_INDEX, 0)
            putExtra(NOTIFICATION_IMAGE, data.image)
            putExtra(NOTIFICATION_TITLE, data.title)
            putExtra(NOTIFICATION_BODY, data.body)
        }
        return PendingIntent.getService(
            context,
            0,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun generateRequestCode(action: String, currentIndex: Int): Int {
        val baseCode = "${action}_${currentIndex}".hashCode()
        val uniqueCode = if (baseCode != Int.MIN_VALUE) abs(baseCode) else 0

        return requestCodeCounter.incrementAndGet() + uniqueCode
    }
}
