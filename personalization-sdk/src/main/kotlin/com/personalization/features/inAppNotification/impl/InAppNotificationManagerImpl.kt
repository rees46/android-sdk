package com.personalization.features.inAppNotification.impl

import android.view.View
import androidx.fragment.app.FragmentManager
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.inAppNotification.view.component.dialog.AlertDialog
import com.personalization.inAppNotification.view.component.dialog.BottomSheetDialog
import com.personalization.inAppNotification.view.component.dialog.FullScreenDialog
import com.personalization.inAppNotification.view.component.snackbar.Snackbar
import com.personalization.ui.click.NotificationClickListener
import javax.inject.Inject

class InAppNotificationManagerImpl @Inject constructor() : InAppNotificationManager {

    private lateinit var fragmentManager: FragmentManager

    override fun initFragmentManager(fragmentManager: FragmentManager) {
        this.fragmentManager = fragmentManager
    }

    override fun showAlertDialog(
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
                override fun onNegativeClick() = onNegativeClick()
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
            object : NotificationClickListener {
                override fun onPositiveClick() = onPositiveClick()
                override fun onNegativeClick() = onNegativeClick()
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
            object : NotificationClickListener {
                override fun onPositiveClick() = onPositiveClick()
                override fun onNegativeClick() = onNegativeClick()
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
}
