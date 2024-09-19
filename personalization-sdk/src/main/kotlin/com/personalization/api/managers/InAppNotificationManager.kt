package com.personalization.api.managers

import androidx.fragment.app.FragmentManager

interface InAppNotificationManager {


    fun showAlertDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        buttonText: String
    )

    fun showFullScreenDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText:String,
        buttonNegativeText:String,
    )

    fun showBottomSheetDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText:String,
        buttonNegativeText:String,
    )
}
