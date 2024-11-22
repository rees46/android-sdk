package com.personalization.api.managers

import android.view.View
import androidx.fragment.app.FragmentManager
import com.personalization.api.responses.initialization.Popup

interface InAppNotificationManager {

    fun initFragmentManager(fragmentManager: FragmentManager)

    fun shopPopUp(popup: Popup)

    fun showAlertDialog(
        title: String,
        message: String,
        imageUrl: String,
        buttonPositiveText: String,
        buttonNegativeText: String,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    )

    fun showFullScreenDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
        buttonPositiveText: String,
        buttonNegativeText: String,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    )

    fun showBottomSheetDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String,
        buttonNegativeText: String?,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
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
