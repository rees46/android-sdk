package com.personalization.api.managers

import androidx.fragment.app.FragmentManager

interface InAppNotificationManager {


    fun showAlertDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String
    )

    fun showFullScreenAlertDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        imageUrl: String?
    )
}
