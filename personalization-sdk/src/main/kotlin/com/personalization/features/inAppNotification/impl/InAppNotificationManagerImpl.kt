package com.personalization.features.inAppNotification.impl

import androidx.fragment.app.FragmentManager
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.inAppNotification.view.BottomSheetDialog
import com.personalization.inAppNotification.view.DefaultAlertDialog
import com.personalization.inAppNotification.view.FullScreenDialog
import javax.inject.Inject

class InAppNotificationManagerImpl @Inject constructor() : InAppNotificationManager {

    override fun showAlertDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        buttonText: String
    ) {
        DefaultAlertDialog.newInstance(
            title = title,
            message = message,
            buttonText = buttonText
        ).show(
            /* manager = */ fragmentManager,
            /* tag = */ DefaultAlertDialog.TAG
        )
    }

    override fun showFullScreenDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String,
        buttonNegativeText: String,
    ) {
        FullScreenDialog.newInstance(
            title = title,
            message = message,
            imageUrl = imageUrl,
            buttonPositiveText = buttonPositiveText,
            buttonNegativeText = buttonNegativeText,
        ).show(
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
        buttonNegativeText: String
    ) {
        BottomSheetDialog.newInstance(
            title = title,
            message = message,
            buttonPositiveText = buttonPositiveText,
            buttonNegativeText = buttonNegativeText,
        ).show(
            /* manager = */ fragmentManager,
            /* tag = */ BottomSheetDialog.TAG
        )
    }
}
