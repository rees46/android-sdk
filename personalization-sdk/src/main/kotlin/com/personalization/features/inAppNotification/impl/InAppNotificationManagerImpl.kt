package com.personalization.features.inAppNotification.impl

import androidx.fragment.app.FragmentManager
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.inAppNotification.view.AlertDialog
import javax.inject.Inject

class InAppNotificationManagerImpl @Inject constructor(
) : InAppNotificationManager {

    private lateinit var supportFragmentManager: FragmentManager

    override fun initialize(fragmentManager: FragmentManager) {
        supportFragmentManager = fragmentManager
        showAlertDialog("FFFF","FFFF")
    }

    override fun showAlertDialog(title: String, message: String) {
        AlertDialog.newInstance(
            title = title,
            message = message
        ).show(
            /* manager = */ supportFragmentManager,
            /* tag = */ AlertDialog.TAG
        )
    }
}
