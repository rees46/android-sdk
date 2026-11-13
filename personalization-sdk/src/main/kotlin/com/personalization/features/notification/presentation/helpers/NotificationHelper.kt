package com.personalization.features.notification.presentation.helpers

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.personalization.Rees46
import com.personalization.core.PushEvent
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_CHANNEL
import com.personalization.push.PushDismissReceiver
import com.personalization.resources.NotificationResources
import com.personalization.sdk.data.models.dto.notification.NotificationData
import javax.inject.Inject

class NotificationHelper @Inject constructor(
    private val notificationManager: NotificationManager
) {

    /** The shop of the instance this helper belongs to: the notifications it draws are that shop's. */
    @Volatile
    internal var shopId: String? = null

    /**
     * Draws [data] as a notification. When it is a push of the SDK — it has an `id` and a `type` — the SDK
     * reports it as shown (`Rees46.handlePush(push, PushEvent.OPENED)`: `track/opened` and the `onOpened`
     * callback), and a dismissal without a tap as closed (`track/closed`, `onClosed`), for the shop of the
     * instance whose helper this is.
     */
    fun createNotification(
        context: Context,
        data: NotificationData,
        images: List<Bitmap>?,
        currentImageIndex: Int = 0,
        hasError: Boolean = false
    ) = show(context, data, images, currentImageIndex, hasError, shopId ?: shopOf(data.id), reportShown = true)

    /** The same push drawn again, with another picture of its carousel: not shown once more. */
    internal fun redraw(
        context: Context,
        data: NotificationData,
        images: List<Bitmap>?,
        currentImageIndex: Int,
        hasError: Boolean,
    ) = show(context, data, images, currentImageIndex, hasError, shopOf(data.id), reportShown = false)

    private fun show(
        context: Context,
        data: NotificationData,
        images: List<Bitmap>?,
        currentImageIndex: Int,
        hasError: Boolean,
        shopId: String?,
        reportShown: Boolean,
    ) {
        val view = RemoteViews(
            /* packageName = */ context.packageName,
            /* layoutId = */ NotificationResources.NOTIFICATION_LAYOUT
        )

        NotificationTextHelper.setNotificationText(
            customView = view,
            data = data
        )
        NotificationImageHelper.displayImages(
            customView = view,
            images = images,
            currentIndex = currentImageIndex
        )
        NotificationViewHelper.setNavigationActions(
            customView = view,
            context = context,
            data = data,
            currentIndex = currentImageIndex,
            imageCount = images?.size ?: 0,
            hasError = hasError
        )

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(resolveSmallIcon(context))
            .setCustomContentView(view)
            .setCustomBigContentView(view)
            .setAutoCancel(true)
            // From Android 8 the channel decides, and NOTIFICATION_CHANNEL is IMPORTANCE_HIGH. Below
            // that there is no channel, and a heads-up pop-up requires BOTH a high priority and a
            // sound or vibration — priority alone leaves the push silent in the shade. minSdk is 19,
            // so these still matter. Mirrors the Flutter SDK's notifier and the React Native one,
            // which sets AndroidImportance.HIGH per notification.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notificationId = (data.title + data.body).hashCode()
        val push = pushOf(data, shopId)
        push?.let { notificationBuilder.setDeleteIntent(PushDismissReceiver.intent(context, notificationId, it)) }

        notificationManager.notify(
            /* id = */ notificationId,
            /* notification = */ notificationBuilder.build()
        )
        if (push == null) return
        if (shopId != null) remember(push.getValue(FIELD_ID), shopId)
        if (reportShown) Rees46.handlePush(push, PushEvent.OPENED)
    }

    /** What reports the push of [data]: its code, type and shop; null when it is not a push of the SDK. */
    private fun pushOf(data: NotificationData, shopId: String?): Map<String, String>? {
        val code = data.id?.takeIf { it.isNotBlank() } ?: return null
        val type = data.type?.takeIf { it.isNotBlank() } ?: return null
        return buildMap {
            put(FIELD_ID, code)
            put(FIELD_TYPE, type)
            shopId?.let { put(FIELD_SHOP_ID, it) }
        }
    }

    /**
     * The SDK never imposes its own brand icon. Use the host app's dedicated notification icon
     * if it declared one via the standard `default_notification_icon` meta-data; otherwise fall
     * back to the host application's own launcher icon.
     */
    private fun resolveSmallIcon(context: Context): Int {
        val hostSpecialIcon = readHostNotificationIcon(context)
        return if (hostSpecialIcon != 0) hostSpecialIcon else context.applicationInfo.icon
    }

    private fun readHostNotificationIcon(context: Context): Int = try {
        context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData
            ?.getInt(META_DATA_NOTIFICATION_ICON, 0) ?: 0
    } catch (e: PackageManager.NameNotFoundException) {
        0
    }

    private companion object {
        const val FIELD_ID = "id"
        const val FIELD_TYPE = "type"
        const val FIELD_SHOP_ID = "shop_id"
        private const val SHOPS_LIMIT = 100

        /**
         * The shop of each push drawn in this process, by its code: the carousel's service draws a push
         * again without an instance, and its dismissal still belongs to the shop.
         */
        private val shops = object : LinkedHashMap<String, String>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > SHOPS_LIMIT
        }

        fun remember(code: String, shopId: String) = synchronized(shops) { shops[code] = shopId }

        fun shopOf(code: String?): String? = code?.let { synchronized(shops) { shops[it] } }

        // De-facto standard meta-data hosts use to declare a dedicated push notification icon.
        const val META_DATA_NOTIFICATION_ICON =
            "com.google.firebase.messaging.default_notification_icon"
    }
}
