package com.personalization.features.notification.presentation.helpers

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.personalization.Rees46
import com.personalization.core.PushEvent
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.PushHub
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ACTION
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ACTIONS
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ACTION_URLS
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_CHANNEL
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_EVENT
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ICON
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_IMAGE
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_PAYLOAD
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_URI
import com.personalization.features.notification.domain.model.NotificationConstants.TYPE_PARAM
import com.personalization.features.notification.event.model.NotificationEvent
import com.personalization.push.PushDismissReceiver
import com.personalization.resources.NotificationResources
import com.personalization.sdk.data.models.dto.notification.NotificationData
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class NotificationHelper @Inject constructor(
    private val notificationManager: NotificationManager
) {

    /** The shop of the instance this helper belongs to: the shop of a push the SDK did not see come in. */
    @Volatile
    internal var shopId: String? = null

    /**
     * Draws [data] as a notification.
     *
     * On the new core ([com.personalization.Rees46.initializeBridge]), a push of the SDK — one with an `id` and
     * a `type` — is reported as shown (`Rees46.handlePush(push, PushEvent.OPENED)`: `track/opened` and the
     * `onOpened` callback) and its dismissal without a tap as closed (`track/closed`, `onClosed`), for the
     * shop the push names in its `shop_id`. The callbacks get the whole push as the SDK received it. A push
     * the SDK did not see come in is rebuilt from [data], for the shop of this helper's instance.
     *
     * The legacy core reports neither and sets no dismissal intent, as before.
     */
    fun createNotification(
        context: Context,
        data: NotificationData,
        images: List<Bitmap>?,
        currentImageIndex: Int = 0,
        hasError: Boolean = false
    ) = show(context, data, images, currentImageIndex, hasError, push = if (CoreSelection.decided() == true) pushOf(data) else null, reportShown = true)

    /**
     * The same push drawn again, with another picture of its carousel: not reported as shown once more.
     * [push] is what the carousel's button carried ([com.personalization.push.PushExtras]): the dismissal of the notification drawn
     * again is still reported for the push's shop, in a process the button started. Null on the legacy core.
     */
    internal fun redraw(
        context: Context,
        data: NotificationData,
        images: List<Bitmap>?,
        currentImageIndex: Int,
        hasError: Boolean,
        push: Map<String, String>?,
    ) = show(context, data, images, currentImageIndex, hasError, push, reportShown = false)

    private fun show(
        context: Context,
        data: NotificationData,
        images: List<Bitmap>?,
        currentImageIndex: Int,
        hasError: Boolean,
        push: Map<String, String>?,
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
            hasError = hasError,
            push = push,
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
        push?.let { notificationBuilder.setDeleteIntent(PushDismissReceiver.intent(context, notificationId, it)) }

        notificationManager.notify(
            /* id = */ notificationId,
            /* notification = */ notificationBuilder.build()
        )
        if (reportShown && push != null) Rees46.handlePush(push, PushEvent.OPENED)
    }

    /**
     * The push behind [data], for its events: as the SDK received it, with its own `shop_id` and every
     * field ([PushHub.received]); or, for a push the SDK did not see come in, rebuilt from [data] for the
     * shop of this helper's instance. Null when it is not a push of the SDK.
     */
    private fun pushOf(data: NotificationData): Map<String, String>? {
        val code = data.id?.takeIf { it.isNotBlank() } ?: return null
        val type = data.type?.takeIf { it.isNotBlank() } ?: return null
        PushHub.received(code, type)?.let { return it }
        return buildMap {
            put(FIELD_ID, code)
            put(FIELD_TYPE, type)
            shopId?.let { put(FIELD_SHOP_ID, it) }
            data.title?.let { put(NOTIFICATION_TITLE, it) }
            data.body?.let { put(NOTIFICATION_BODY, it) }
            data.icon?.takeIf { it.isNotEmpty() }?.let { put(NOTIFICATION_ICON, it) }
            data.image?.takeIf { it.isNotEmpty() }?.let { put(NOTIFICATION_IMAGE, it) }
            data.actions?.takeIf { it.isNotEmpty() }?.let { actions ->
                val list = JSONArray()
                actions.forEach { list.put(JSONObject().put(NOTIFICATION_ACTION, it.action).put(NOTIFICATION_TITLE, it.title)) }
                put(NOTIFICATION_ACTIONS, list.toString())
            }
            data.actionUrls?.takeIf { it.isNotEmpty() }?.let { urls -> put(NOTIFICATION_ACTION_URLS, JSONArray(urls).toString()) }
            data.event?.let(::eventOf)?.let { put(NOTIFICATION_EVENT, it) }
        }
    }

    /** The `event` of a push as JSON text; null for the empty event the mapper gives a push without one. */
    private fun eventOf(event: NotificationEvent): String? {
        val payload = event.payload.orEmpty()
        if (event.type.isNullOrEmpty() && event.uri.isNullOrEmpty() && payload.isEmpty()) return null
        return try {
            JSONObject()
                .put(TYPE_PARAM, event.type.orEmpty())
                .put(NOTIFICATION_URI, event.uri.orEmpty())
                .apply { if (payload.isNotEmpty()) put(NOTIFICATION_PAYLOAD, JSONObject(payload)) }
                .toString()
        } catch (e: JSONException) {
            null
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

        // De-facto standard meta-data hosts use to declare a dedicated push notification icon.
        const val META_DATA_NOTIFICATION_ICON =
            "com.google.firebase.messaging.default_notification_icon"
    }
}
