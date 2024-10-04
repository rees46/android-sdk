package com.personalization.api.managers

import android.view.View
import androidx.fragment.app.FragmentManager

interface InAppNotificationManager {

    fun showAlertDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        buttonPositiveText: String,
        buttonNegativeText: String,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    )

    fun showFullScreenDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String,
        buttonNegativeText: String,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    )

    fun showBottomSheetDialog(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String,
        buttonNegativeText: String,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    )

    fun showSnackBar(
        view: View,
        message: String,
        buttonPositiveText: String,
        buttonNegativeText: String,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    )
}
