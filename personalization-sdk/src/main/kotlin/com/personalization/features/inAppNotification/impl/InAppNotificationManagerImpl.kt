package com.personalization.features.inAppNotification.impl

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.personalization.R
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.errors.EmptyFieldError
import com.personalization.inAppNotification.view.component.dialog.ALERT_DIALOG_TAG
import com.personalization.inAppNotification.view.component.dialog.AlertDialog
import com.personalization.inAppNotification.view.component.dialog.BOTTOM_SHEET_TAG
import com.personalization.inAppNotification.view.component.dialog.BottomSheetDialog
import com.personalization.inAppNotification.view.component.dialog.FullScreenDialog
import com.personalization.inAppNotification.view.component.dialog.TOP_SHEET_DIALOG
import com.personalization.inAppNotification.view.component.dialog.TopSheetDialog
import com.personalization.inAppNotification.view.component.snackbar.Snackbar
import com.personalization.sdk.data.models.dto.popUp.DialogDataDto
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position
import com.personalization.ui.click.NotificationClickListener
import javax.inject.Inject

class InAppNotificationManagerImpl @Inject constructor(
    private val context: Context
) : InAppNotificationManager {

    private lateinit var fragmentManager: FragmentManager

    override fun initFragmentManager(fragmentManager: FragmentManager) {
        this.fragmentManager = fragmentManager
    }

    override fun shopPopUp(popupDto: PopupDto) {
        val dialogData = extractDialogData(popupDto)
        showDialog(dialogData)
    }

    private fun extractDialogData(popupDto: PopupDto): DialogDataDto {
        val deepLink =
            popupDto.popupActions?.link?.linkAndroid ?: popupDto.popupActions?.link?.linkWeb
        val buttonPositiveColor = ContextCompat.getColor(context, R.color.buttonAcceptColor)
        val buttonNegativeColor = ContextCompat.getColor(context, R.color.colorGray)
        val buttonSubscription = popupDto.popupActions?.pushSubscribe?.buttonText
        val buttonNegativeText = popupDto.popupActions?.close?.buttonText
        val buttonPositiveText = buttonSubscription ?: popupDto.popupActions?.link?.buttonText
        val imageUrl: String? = popupDto.components?.image
        val title: String? = popupDto.components?.header
        val message: String? = popupDto.components?.text
        val position: Position = popupDto.position

        val onPositiveClick = if (buttonSubscription != null) {
            { requestPushNotifications() }
        } else {
            { openUrlInBrowser(url = deepLink) }
        }

        return DialogDataDto(
            title = title.orEmpty(),
            message = message.orEmpty(),
            imageUrl = imageUrl.orEmpty(),
            buttonPositiveColor = buttonPositiveColor,
            buttonNegativeColor = buttonNegativeColor,
            buttonPositiveText = buttonPositiveText.orEmpty(),
            buttonNegativeText = buttonNegativeText.orEmpty(),
            onPositiveClick = onPositiveClick,
            position = position
        )
    }

    private fun showDialog(dialogData: DialogDataDto) {
        when (dialogData.position) {
            Position.CENTERED -> showAlertDialog(
                title = dialogData.title,
                message = dialogData.message,
                imageUrl = dialogData.imageUrl,
                buttonPositiveColor = dialogData.buttonPositiveColor,
                buttonNegativeColor = dialogData.buttonNegativeColor,
                buttonPositiveText = dialogData.buttonPositiveText,
                buttonNegativeText = dialogData.buttonNegativeText,
                onPositiveClick = dialogData.onPositiveClick
            )

            Position.BOTTOM -> showBottomSheetDialog(
                title = dialogData.title,
                message = dialogData.message,
                imageUrl = dialogData.imageUrl,
                buttonPositiveColor = dialogData.buttonPositiveColor,
                buttonNegativeColor = dialogData.buttonNegativeColor,
                buttonPositiveText = dialogData.buttonPositiveText,
                buttonNegativeText = dialogData.buttonNegativeText,
                onPositiveClick = dialogData.onPositiveClick
            )

            Position.TOP -> showTopSheetDialog(
                title = dialogData.title,
                message = dialogData.message,
                imageUrl = dialogData.imageUrl,
                buttonPositiveColor = dialogData.buttonPositiveColor,
                buttonNegativeColor = dialogData.buttonNegativeColor,
                buttonPositiveText = dialogData.buttonPositiveText,
                buttonNegativeText = dialogData.buttonNegativeText,
                onPositiveClick = dialogData.onPositiveClick
            )

            else -> showFullScreenDialog(
                title = dialogData.title,
                message = dialogData.message,
                imageUrl = dialogData.imageUrl,
                buttonPositiveColor = dialogData.buttonPositiveColor,
                buttonNegativeColor = dialogData.buttonNegativeColor,
                buttonPositiveText = dialogData.buttonPositiveText,
                buttonNegativeText = dialogData.buttonNegativeText,
                onPositiveClick = dialogData.onPositiveClick
            )
        }
    }

    override fun showAlertDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String?,
        buttonNegativeText: String?,
        buttonPositiveColor: Int?,
        buttonNegativeColor: Int?,
        onPositiveClick: (() -> Unit)?
    ) {
        val dialog = AlertDialog.newInstance(
            title = title,
            message = message,
            imageUrl = imageUrl,
            buttonPositiveColor = buttonPositiveColor,
            buttonNegativeColor = buttonNegativeColor,
            buttonPositiveText = buttonPositiveText,
            buttonNegativeText = buttonNegativeText
        )

        dialog.listener = (
            object : NotificationClickListener {
                override fun onPositiveClick() {
                    onPositiveClick?.invoke()
                }
                override fun onNegativeClick() {
                    dialog.dismiss()
                }
            }
        )

        dialog.show(
            /* manager = */ fragmentManager,
            /* tag = */ ALERT_DIALOG_TAG
        )
    }

    override fun showFullScreenDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String?,
        buttonNegativeText: String?,
        buttonPositiveColor: Int?,
        buttonNegativeColor: Int?,
        onPositiveClick: (() -> Unit)?
    ) {
        val dialog = FullScreenDialog.newInstance(
            title = title,
            message = message,
            imageUrl = imageUrl,
            buttonPositiveColor = buttonPositiveColor,
            buttonNegativeColor = buttonNegativeColor,
            buttonPositiveText = buttonPositiveText,
            buttonNegativeText = buttonNegativeText,
        )

        dialog.listener = (
            object : NotificationClickListener {
                override fun onPositiveClick() {
                    onPositiveClick?.invoke()
                }
                override fun onNegativeClick() {
                    dialog.dismiss()
                }
            }
        )

        dialog.show(
            /* manager = */ fragmentManager,
            /* tag = */ FULL_SCREEN_DIALOG_TAG
        )
    }

    override fun showBottomSheetDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String?,
        buttonNegativeText: String?,
        buttonPositiveColor: Int?,
        buttonNegativeColor: Int?,
        onPositiveClick: (() -> Unit)?
    ) {
        val dialog = BottomSheetDialog.newInstance(
            title = title,
            message = message,
            imageUrl = imageUrl,
            buttonPositiveColor = buttonPositiveColor,
            buttonNegativeColor = buttonNegativeColor,
            buttonPositiveText = buttonPositiveText,
            buttonNegativeText = buttonNegativeText,
        )

        dialog.listener = (
            object : NotificationClickListener {
                override fun onPositiveClick() {
                    onPositiveClick?.invoke()
                }
                override fun onNegativeClick() {
                    dialog.dismiss()
                }
            }
        )

        dialog.show(
            /* manager = */ fragmentManager,
            /* tag = */ BOTTOM_SHEET_TAG
        )
    }

    override fun showTopSheetDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String?,
        buttonNegativeText: String?,
        buttonPositiveColor: Int?,
        buttonNegativeColor: Int?,
        onPositiveClick: (() -> Unit)?
    ) {
        val dialog = TopSheetDialog.newInstance(
            title = title,
            message = message,
            imageUrl = imageUrl,
            buttonPositiveColor = buttonPositiveColor,
            buttonNegativeColor = buttonNegativeColor,
            buttonPositiveText = buttonPositiveText,
            buttonNegativeText = buttonNegativeText,
        )

        dialog.listener = (
            object : NotificationClickListener {
                override fun onPositiveClick() {
                    onPositiveClick?.invoke()
                }
                override fun onNegativeClick() {
                    dialog.dismiss()
                }
            }
        )

        dialog.show(
            /* manager = */ fragmentManager,
            /* tag = */ TOP_SHEET_DIALOG
        )
    }

    override fun showSnackBar(
        view: View,
        message: String,
        buttonPositiveText: String,
        buttonNegativeText: String,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    ) {
        Snackbar(view).show(
            message = message,
            buttonPositiveText = buttonPositiveText,
            buttonNegativeText = buttonNegativeText,
            onPositiveClick = onPositiveClick,
            onNegativeClick = onNegativeClick
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
