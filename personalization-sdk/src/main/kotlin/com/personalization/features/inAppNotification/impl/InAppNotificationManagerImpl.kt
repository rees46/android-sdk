package com.personalization.features.inAppNotification.impl

import androidx.fragment.app.FragmentManager
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.inAppNotification.view.DefaultAlertDialog
import com.personalization.inAppNotification.view.FullScreenDialog
import javax.inject.Inject

class InAppNotificationManagerImpl @Inject constructor() : InAppNotificationManager {

    override fun showAlertDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String
    ) {
        DefaultAlertDialog.newInstance(
            title = title,
            message = message
        ).show(
            /* manager = */ fragmentManager,
            /* tag = */ DefaultAlertDialog.TAG
        )
    }

    override fun showFullScreenAlertDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        imageUrl: String?
    ) {
        FullScreenDialog.newInstance(
            title = title,
            message = message,
            imageUrl = imageUrl
        ).show(
            /* manager = */ fragmentManager,
            /* tag = */ FullScreenDialog.TAG
        )
    }
}
