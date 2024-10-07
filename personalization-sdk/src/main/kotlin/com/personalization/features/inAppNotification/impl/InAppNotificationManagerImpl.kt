package com.personalization.features.inAppNotification.impl

import android.view.View
import androidx.fragment.app.FragmentManager
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.inAppNotification.view.BottomSheetDialog
import com.personalization.inAppNotification.view.DefaultAlertDialog
import com.personalization.inAppNotification.view.FullScreenDialog
import com.personalization.inAppNotification.view.SdkSnackbar
import javax.inject.Inject

class InAppNotificationManagerImpl @Inject constructor() : InAppNotificationManager {

    override fun showAlertDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        imageUrl: String,
        buttonPositiveText: String,
        buttonNegativeText: String,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    ) {
        val dialog = DefaultAlertDialog.newInstance(
            title = title,
            message = message,
            imageUrl = imageUrl,
            buttonPositiveColor = buttonPositiveColor,
            buttonNegativeColor = buttonNegativeColor,
            buttonPositiveText = buttonPositiveText,
            buttonNegativeText = buttonNegativeText,
        )

        dialog.setListener(
            object : DefaultAlertDialog.AlertDialogListener {
                override fun onPositiveButtonClick() = onPositiveClick()
                override fun onNegativeButtonClick() = onNegativeClick()
            }
        )

        dialog.show(
            /* manager = */ fragmentManager,
            /* tag = */ DefaultAlertDialog.TAG
        )
    }

    override fun showFullScreenDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
        buttonPositiveText: String,
        buttonNegativeText: String,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
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
            object : FullScreenDialog.FullScreenDialogListener {
                override fun onPositiveButtonClick() = onPositiveClick()
                override fun onNegativeButtonClick() = onNegativeClick()
            }
        )

        dialog.show(
            /* manager = */ fragmentManager,
            /* tag = */ FullScreenDialog.TAG
        )
    }

    override fun showBottomSheetDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String,
        buttonNegativeText: String?,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
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
            object : BottomSheetDialog.BottomSheetDialogListener {
                override fun onPositiveButtonClick() = onPositiveClick()
                override fun onNegativeButtonClick() = onNegativeClick()
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
        SdkSnackbar(view).show(
            message = message,
            buttonPositiveText = buttonPositiveText,
            buttonNegativeText = buttonNegativeText,
            onPositiveClick = onPositiveClick,
            onNegativeClick = onNegativeClick
        )
    }
}
