package com.personalization.api.managers

import androidx.fragment.app.FragmentManager

interface InAppNotificationManager {

    fun initialize(fragmentManager: FragmentManager)

    fun showAlertDialog(
        title: String,
        message: String
    )
}
