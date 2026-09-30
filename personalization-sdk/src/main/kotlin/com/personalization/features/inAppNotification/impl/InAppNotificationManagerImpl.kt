package com.personalization.features.inAppNotification.impl

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

    override fun shopPopUp(popupDto: PopupDto) {
        // Popups ride on network responses and arrive on a worker thread. Showing one right there
        // breaks the UI-thread rule and lets a failure escape into the network callback, which turns
        // it into an error for the request that carried the popup — an init that never persists its
        // did, a track that reports failure after it was sent.
        handler.post { present(popupDto) }
    }

    private fun present(popupDto: PopupDto) {
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
                    SDK.error("Popup ${popupDto.id} was not shown: the presentation listener failed", exception)
                    return
                }
                if (activity == null) {
                    SDK.debug("Popup ${popupDto.id} was kept back by the presentation listener")
                    return
                }
                activity.supportFragmentManager.takeIf { it.canShowDialog() }
            }

            presentation.autoPresentation -> defaultTarget()

            else -> {
                SDK.debug("Popup ${popupDto.id} was not shown: automatic presentation is off")
                return
            }
        }
        if (target == null) {
            SDK.warn("Popup ${popupDto.id} was not shown: no activity that can take a dialog")
            return
        }
        try {
            showDialog(target, extractDialogData(popupDto))
        } catch (exception: Exception) {
            SDK.error("Popup ${popupDto.id} was not shown: ${exception.message}", exception)
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
            SDK.warn("Dialog was not shown: no activity on screen to show it in")
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
            exception.printStackTrace()
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

    companion object {
        private const val TAG = "InAppNotificationManagerImpl"
        private const val FUNC_OPENING_BROWSER = "openUrlInBrowser"
    }
}
