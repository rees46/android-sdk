package com.personalization.features.inAppNotification.impl

import com.personalization.logs.SdkLogs
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.LogLevel
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.personalization.R
import com.personalization.SDK
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.api.managers.TrackEventManager
import com.personalization.errors.EmptyFieldError
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import dagger.Lazy
import com.personalization.inAppNotification.view.component.dialog.fullScreen.FULL_SCREEN_DIALOG_TAG
import com.personalization.inAppNotification.view.component.dialog.alert.ALERT_DIALOG_TAG
import com.personalization.inAppNotification.view.component.dialog.alert.AlertDialog
import com.personalization.inAppNotification.view.component.dialog.bottom.BOTTOM_DIALOG_TAG
import com.personalization.inAppNotification.view.component.dialog.bottom.BottomDialog
import com.personalization.inAppNotification.view.component.dialog.fullScreen.FullScreenDialog
import com.personalization.inAppNotification.view.component.dialog.top.TOP_DIALOG_TAG
import com.personalization.inAppNotification.view.component.dialog.top.TopDialog
import com.personalization.inAppNotification.view.component.snackbar.Snackbar
import com.personalization.sdk.data.models.dto.popUp.DialogDataDto
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position
import com.personalization.ui.click.NotificationClickListener
import com.personalization.ui.popups.PopupV3Slots
import java.lang.ref.WeakReference
import javax.inject.Inject

class InAppNotificationManagerImpl @Inject constructor(
    private val context: Context,
    private val getUserSettingsValueUseCase: GetUserSettingsValueUseCase,
    private val trackEventManager: Lazy<TrackEventManager>
) : InAppNotificationManager {

    // Set by the SDK's DI module; the default only serves managers built by hand, as in tests.
    internal var presentation: PopupPresentation = PopupPresentation()

    // Weak: the manager belongs to an activity, and holding it would outlive that activity.
    private var fragmentManager: WeakReference<FragmentManager>? = null
    private val popupShownFlags: MutableMap<Int, Long> = mutableMapOf()
    private val handler: Handler = Handler(Looper.getMainLooper())

    @Deprecated(
        "Not needed any more: popups are shown in the activity on screen. To pick the activity, " +
            "or keep a popup from being shown, set SDK.popupPresentationListener."
    )
    override fun initFragmentManager(fragmentManager: FragmentManager) {
        this.fragmentManager = WeakReference(fragmentManager)
    }

    /** The dialog the manager showed last, while it may still be on screen: what [dismissCurrent] closes. */
    private var current: WeakReference<DialogFragment>? = null

    /**
     * Shows a server popup [PopupDto.delay] seconds after it came, as the contract has it: the presentation
     * listener is asked then, not before.
     */
    override fun shopPopUp(popupDto: PopupDto) {
        // Popups ride on network responses and arrive on a worker thread. Showing one right there
        // breaks the UI-thread rule and lets a failure escape into the network callback, which turns
        // it into an error for the request that carried the popup — an init that never persists its
        // did, a track that reports failure after it was sent.
        val delayMs = popupDto.delay.coerceAtLeast(0) * 1_000L
        if (delayMs > 0) handler.postDelayed({ present(popupDto) }, delayMs) else handler.post { present(popupDto) }
    }

    /**
     * A popup the app hands back to the SDK (`SDK.presentPopup`): shown now, in [activity] while it can take
     * a dialog or else in the activity on screen — past the presentation listener, automatic presentation and
     * the rule of one showing a minute, and without its delay. Still one popup on screen at a time.
     */
    internal fun presentHandedBack(popupDto: PopupDto, activity: FragmentActivity?) {
        val held = activity?.let(::WeakReference)
        handler.post { presentNow(popupDto, held) }
    }

    private fun presentNow(popupDto: PopupDto, activity: WeakReference<FragmentActivity>?) {
        if (PopupV3Slots.isModalOpen) {
            PopupV3Slots.whenModalClosed { handler.post { presentNow(popupDto, activity) } }
            return
        }
        val target = activity?.get()?.supportFragmentManager?.takeIf { it.canShowDialog() } ?: defaultTarget()
        if (target == null) {
            log(LogLevel.WARNING, LogCodes.POPUP_NOT_SHOWN, "Popup was not shown: no activity that can take a dialog", context = mapOf("popup" to popupDto.id, "reason" to "no_activity"))
            return
        }
        show(popupDto, target)
    }

    /**
     * Closes the dialog of the popup the manager shows now (`SDK.dismissCurrentPopup`), as its close button
     * does. True when there was one on screen. On the main thread.
     */
    internal fun dismissCurrent(): Boolean {
        val dialog = current?.get()
        current = null
        if (dialog == null || !dialog.isAdded) return false
        dialog.dismissAllowingStateLoss()
        return true
    }

    private fun present(popupDto: PopupDto) {
        // One modal at a time, shared with popups v3: while one of theirs is open, this one waits.
        if (PopupV3Slots.isModalOpen) {
            PopupV3Slots.whenModalClosed { handler.post { present(popupDto) } }
            return
        }

        // Check if popup was shown in the last 60 seconds
        val shownTime = popupShownFlags[popupDto.id]
        if (shownTime != null) {
            val timeSinceShown = System.currentTimeMillis() - shownTime
            if (timeSinceShown < 60_000) { // 60 seconds in milliseconds
                return // Popup was already shown, skip
            }
        }

        // Whatever did not reach the screen is neither remembered nor reported as shown.
        val listener = presentation.listener
        val target = when {
            listener != null -> {
                // Host code, called from the SDK's own main-thread callback: a throw here would
                // take the whole app down instead of just this popup.
                val activity = try {
                    listener(popupDto)
                } catch (exception: Exception) {
                    log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "Popup was not shown: the presentation listener threw", context = mapOf("popup" to popupDto.id), throwable = exception)
                    return
                }
                if (activity == null) {
                    log(LogLevel.DEBUG, LogCodes.POPUP_NOT_SHOWN, "Popup kept back by the presentation listener", context = mapOf("popup" to popupDto.id, "reason" to LogReasons.APP_DECLINED))
                    return
                }
                activity.supportFragmentManager.takeIf { it.canShowDialog() }
            }

            presentation.autoPresentation -> defaultTarget()

            else -> {
                log(LogLevel.DEBUG, LogCodes.POPUP_NOT_SHOWN, "Popup was not shown: automatic presentation is off", context = mapOf("popup" to popupDto.id, "reason" to LogReasons.AUTO_PRESENTATION_OFF))
                return
            }
        }
        if (target == null) {
            log(LogLevel.WARNING, LogCodes.POPUP_NOT_SHOWN, "Popup was not shown: no activity that can take a dialog", context = mapOf("popup" to popupDto.id, "reason" to "no_activity"))
            return
        }
        show(popupDto, target)
    }

    /** Shows [popupDto] in [target]; once on screen, remembers it for a minute and reports it shown. */
    private fun show(popupDto: PopupDto, target: FragmentManager) {
        try {
            showDialog(target, extractDialogData(popupDto))
        } catch (exception: Exception) {
            log(LogLevel.WARNING, LogCodes.POPUP_NOT_SHOWN, "Popup was not shown", context = mapOf("popup" to popupDto.id, "reason" to "exception"), throwable = exception)
            return
        }

        // Store popup shown flag in memory for 60 seconds
        popupShownFlags[popupDto.id] = System.currentTimeMillis()

        // Remove flag after 60 seconds
        handler.postDelayed({
            popupShownFlags.remove(popupDto.id)
        }, 60_000)

        // Send popup shown event to server
        trackEventManager.get().trackPopupShown(popupId = popupDto.id, listener = null)
    }

    private fun extractDialogData(popupDto: PopupDto): DialogDataDto {
        val deepLink =
            popupDto.popupActions?.link?.linkAndroid ?: popupDto.popupActions?.link?.linkWeb
        val buttonConfirmColor = ContextCompat.getColor(context, R.color.buttonConfirmColor)
        val buttonDeclineColor = ContextCompat.getColor(context, R.color.colorGray)
        val buttonSubscription = popupDto.popupActions?.pushSubscribe?.buttonText
        val buttonDeclineText = popupDto.popupActions?.close?.buttonText
        val buttonConfirmText = buttonSubscription ?: popupDto.popupActions?.link?.buttonText
        val imageUrl: String? = popupDto.components?.image
        val title: String? = popupDto.components?.header
        val message: String? = popupDto.components?.text
        val position: Position = popupDto.position

        val onConfirmClick = if (buttonSubscription != null) {
            { requestPushNotifications() }
        } else {
            { openUrlInBrowser(url = deepLink) }
        }

        return DialogDataDto(
            title = title.orEmpty(),
            message = message.orEmpty(),
            imageUrl = imageUrl.orEmpty(),
            buttonConfirmColor = buttonConfirmColor,
            buttonDeclineColor = buttonDeclineColor,
            buttonConfirmText = buttonConfirmText.orEmpty(),
            buttonDeclineText = buttonDeclineText.orEmpty(),
            onConfirmClick = onConfirmClick,
            position = position
        )
    }

    /**
     * Where a dialog goes: the FragmentManager handed over through [initFragmentManager] while its
     * activity can still take one, else the activity on screen.
     */
    private fun defaultTarget(): FragmentManager? {
        fragmentManager?.get()?.takeIf { it.canShowDialog() }?.let { return it }
        val activity = ForegroundActivity.get() as? FragmentActivity ?: return null
        return activity.supportFragmentManager.takeIf { it.canShowDialog() }
    }

    private fun FragmentManager.canShowDialog() = !isDestroyed && !isStateSaved

    private fun showDialog(target: FragmentManager, dialogData: DialogDataDto) = showDialog(
        position = dialogData.position,
        title = dialogData.title,
        message = dialogData.message,
        imageUrl = dialogData.imageUrl,
        buttonConfirmText = dialogData.buttonConfirmText,
        buttonDeclineText = dialogData.buttonDeclineText,
        buttonConfirmColor = dialogData.buttonConfirmColor,
        buttonDeclineColor = dialogData.buttonDeclineColor,
        onConfirmClick = dialogData.onConfirmClick,
        target = target
    )

    override fun showAlertDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonConfirmText: String?,
        buttonDeclineText: String?,
        buttonConfirmColor: Int?,
        buttonDeclineColor: Int?,
        onConfirmClick: (() -> Unit)?
    ) = showDialog(
        Position.CENTERED, title, message, imageUrl, buttonConfirmText, buttonDeclineText,
        buttonConfirmColor, buttonDeclineColor, onConfirmClick, defaultTarget()
    )

    override fun showFullScreenDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonConfirmText: String?,
        buttonDeclineText: String?,
        buttonConfirmColor: Int?,
        buttonDeclineColor: Int?,
        onConfirmClick: (() -> Unit)?
    ) = showDialog(
        Position.UNKNOWN, title, message, imageUrl, buttonConfirmText, buttonDeclineText,
        buttonConfirmColor, buttonDeclineColor, onConfirmClick, defaultTarget()
    )

    override fun showBottomDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonConfirmText: String?,
        buttonDeclineText: String?,
        buttonConfirmColor: Int?,
        buttonDeclineColor: Int?,
        onConfirmClick: (() -> Unit)?
    ) = showDialog(
        Position.BOTTOM, title, message, imageUrl, buttonConfirmText, buttonDeclineText,
        buttonConfirmColor, buttonDeclineColor, onConfirmClick, defaultTarget()
    )

    override fun showTopDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonConfirmText: String?,
        buttonDeclineText: String?,
        buttonConfirmColor: Int?,
        buttonDeclineColor: Int?,
        onConfirmClick: (() -> Unit)?
    ) = showDialog(
        Position.TOP, title, message, imageUrl, buttonConfirmText, buttonDeclineText,
        buttonConfirmColor, buttonDeclineColor, onConfirmClick, defaultTarget()
    )

    private fun showDialog(
        position: Position,
        title: String,
        message: String,
        imageUrl: String?,
        buttonConfirmText: String?,
        buttonDeclineText: String?,
        buttonConfirmColor: Int?,
        buttonDeclineColor: Int?,
        onConfirmClick: (() -> Unit)?,
        target: FragmentManager?
    ) {
        if (target == null) {
            log(LogLevel.WARNING, LogCodes.POPUP_NOT_SHOWN, "Dialog was not shown: no activity on screen to show it in", context = mapOf("reason" to "no_activity"))
            return
        }

        val (dialog, tag) = when (position) {
            Position.CENTERED -> AlertDialog.newInstance(
                title, message, imageUrl, buttonConfirmText, buttonDeclineText,
                buttonConfirmColor, buttonDeclineColor
            ) to ALERT_DIALOG_TAG

            Position.BOTTOM -> BottomDialog.newInstance(
                title, message, imageUrl, buttonConfirmText, buttonDeclineText,
                buttonConfirmColor, buttonDeclineColor
            ) to BOTTOM_DIALOG_TAG

            Position.TOP -> TopDialog.newInstance(
                title, message, imageUrl, buttonConfirmText, buttonDeclineText,
                buttonConfirmColor, buttonDeclineColor
            ) to TOP_DIALOG_TAG

            // A popup with no position set comes out fullscreen.
            else -> FullScreenDialog.newInstance(
                title, message, imageUrl, buttonConfirmText, buttonDeclineText,
                buttonConfirmColor, buttonDeclineColor
            ) to FULL_SCREEN_DIALOG_TAG
        }

        dialog.listener = (
            object : NotificationClickListener {
                override fun onConfirmClick() {
                    onConfirmClick?.invoke()
                }
                override fun onDeclineClick() {
                    dialog.dismiss()
                }
            }
        )

        dialog.show(
            /* manager = */ target,
            /* tag = */ tag
        )
        current = WeakReference(dialog)
    }

    override fun showSnackBar(
        view: View,
        message: String,
        buttonConfirmText: String,
        buttonDeclineText: String,
        onConfirmClick: () -> Unit,
        onDeclineClick: () -> Unit
    ) {
        Snackbar(view).show(
            message = message,
            buttonConfirmText = buttonConfirmText,
            buttonDeclineText = buttonDeclineText,
            onConfirmClick = onConfirmClick,
            onDeclineClick = onDeclineClick
        )
    }

    private fun openUrlInBrowser(url: String?) {
        if (url.isNullOrEmpty()) {
            EmptyFieldError(
                tag = TAG,
                functionName = FUNC_OPENING_BROWSER,
            )
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (exception: Exception) {
            // The class only: the text names the link.
            log(LogLevel.WARNING, LogCodes.UI, "No app opened the popup's link", throwable = exception)
        }
    }

    private fun requestPushNotifications() {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }

            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            Toast.makeText(
                /* context = */ context,
                /* text = */ context.getText(R.string.has_notification_permission_message),
                /* duration = */ Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Writes to the log under the instance's shop. The level is checked before the shop is read from
     * the preferences, so a line below every shop's level costs nothing.
     */
    private fun log(
        level: LogLevel,
        code: String,
        message: String,
        context: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        if (!SdkLogs.isLoggable(level)) return
        val shopId = runCatching { getUserSettingsValueUseCase.getShopId() }.getOrNull()?.takeIf { it.isNotEmpty() }
        SdkLogs.log(level, code, message, shopId, context, throwable)
    }

    companion object {
        private const val TAG = "InAppNotificationManagerImpl"
        private const val FUNC_OPENING_BROWSER = "openUrlInBrowser"
    }
}
