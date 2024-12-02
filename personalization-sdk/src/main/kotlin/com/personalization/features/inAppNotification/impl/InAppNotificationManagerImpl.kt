package com.personalization.features.inAppNotification.impl

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.personalization.R
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.errors.EmptyFieldError
import com.personalization.inAppNotification.view.component.dialog.AlertDialog
import com.personalization.inAppNotification.view.component.dialog.BottomSheetDialog
import com.personalization.inAppNotification.view.component.dialog.FullScreenDialog
import com.personalization.inAppNotification.view.component.snackbar.Snackbar
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

    private fun openUrlInBrowser(url: String?) {
        if (url.isNullOrEmpty()) {
            EmptyFieldError(
                tag = TAG,
                functionName = FUNC_OPENING_BROWSER,
                message = "Deep link is empty or null"
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

    override fun shopPopUp(popupDto: PopupDto) {
        val deepLink =
            popupDto.popupActions?.link?.linkAndroid ?: popupDto.popupActions?.link?.linkWeb
        val buttonPositiveColor = ContextCompat.getColor(context, R.color.buttonAcceptColor)
        val buttonNegativeColor = ContextCompat.getColor(context, R.color.colorGray)
        val buttonNegativeText = popupDto.popupActions?.close?.buttonText
        val buttonPositiveText = popupDto.popupActions?.link?.buttonText
        val imageUrl: String? = popupDto.components?.image
        val title: String? = popupDto.components?.header
        val message: String? = popupDto.components?.text
        val position: Position = popupDto.position

        when (position) {
            Position.CENTERED -> showAlertDialog(
                title = title.orEmpty(),
                message = message.orEmpty(),
                imageUrl = imageUrl.orEmpty(),
                buttonPositiveColor = buttonPositiveColor,
                buttonNegativeColor = buttonNegativeColor,
                buttonPositiveText = buttonPositiveText.orEmpty(),
                buttonNegativeText = buttonNegativeText.orEmpty(),
                onPositiveClick = {
                    openUrlInBrowser(url = deepLink)
                }
            )

            Position.BOTTOM -> showBottomSheetDialog(
                title = title.orEmpty(),
                message = message.orEmpty(),
                imageUrl = imageUrl.orEmpty(),
                buttonPositiveColor = buttonPositiveColor,
                buttonNegativeColor = buttonNegativeColor,
                buttonPositiveText = buttonPositiveText.orEmpty(),
                buttonNegativeText = buttonNegativeText.orEmpty(),
                onPositiveClick = {
                    openUrlInBrowser(url = deepLink)
                }
            )

            else -> showFullScreenDialog(
                title = title.orEmpty(),
                message = message.orEmpty(),
                imageUrl = imageUrl.orEmpty(),
                buttonPositiveColor = buttonPositiveColor,
                buttonNegativeColor = buttonNegativeColor,
                buttonPositiveText = buttonPositiveText.orEmpty(),
                buttonNegativeText = buttonNegativeText.orEmpty(),
                onPositiveClick = {
                    openUrlInBrowser(url = deepLink)
                }
            )
        }
    }

    override fun showAlertDialog(
        title: String,
        message: String,
        imageUrl: String,
        buttonPositiveText: String,
        buttonNegativeText: String,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
        onPositiveClick: () -> Unit
    ) {
        val dialog = AlertDialog.newInstance(
            title = title,
            message = message,
            imageUrl = imageUrl,
            buttonPositiveColor = buttonPositiveColor,
            buttonNegativeColor = buttonNegativeColor,
            buttonPositiveText = buttonPositiveText,
            buttonNegativeText = buttonNegativeText,
        )

        dialog.setListener(
            object : NotificationClickListener {
                override fun onPositiveClick() = onPositiveClick()
                override fun onNegativeClick() {
                    dialog.dismiss()
                }
            }
        )

        dialog.show(
            /* manager = */ fragmentManager,
            /* tag = */ AlertDialog.TAG
        )
    }

    override fun showFullScreenDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
        buttonPositiveText: String,
        buttonNegativeText: String,
        onPositiveClick: () -> Unit
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

        dialog.setListener(
            object : NotificationClickListener {
                override fun onPositiveClick() = onPositiveClick()
                override fun onNegativeClick() {
                    dialog.dismiss()
                }
            }
        )

        dialog.show(
            /* manager = */ fragmentManager,
            /* tag = */ FullScreenDialog.TAG
        )
    }

    override fun showBottomSheetDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String,
        buttonNegativeText: String?,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
        onPositiveClick: () -> Unit
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

        dialog.setListener(
            object : NotificationClickListener {
                override fun onPositiveClick() = onPositiveClick()
                override fun onNegativeClick() {
                    dialog.dismiss()
                }
            }
        )

        dialog.show(
            /* manager = */ fragmentManager,
            /* tag = */ BottomSheetDialog.TAG
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

    companion object {
        private const val TAG = "InAppNotificationManagerImpl"
        private const val FUNC_OPENING_BROWSER = "openUrlInBrowser"
    }
}
