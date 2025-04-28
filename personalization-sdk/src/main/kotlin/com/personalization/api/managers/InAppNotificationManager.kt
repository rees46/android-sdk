package com.personalization.api.managers

import android.view.View
import androidx.fragment.app.FragmentManager
import com.personalization.sdk.data.models.dto.popUp.PopupDto

interface InAppNotificationManager {

    fun initFragmentManager(fragmentManager: FragmentManager)

    fun shopPopUp(popupDto: PopupDto)

    fun showAlertDialog(
        title: String,
        message: String,
        imageUrl: String? = null,
        buttonPositiveText: String? = null,
        buttonNegativeText: String? = null,
        buttonPositiveColor: Int? = null,
        buttonNegativeColor: Int? = null,
        onPositiveClick: (() -> Unit)? = null
    )

    fun showFullScreenDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
        buttonPositiveText: String,
        buttonNegativeText: String,
        onPositiveClick: () -> Unit
    )

    fun showBottomSheetDialog(
        title: String,
        message: String,
        imageUrl: String?,
        buttonPositiveText: String,
        buttonNegativeText: String?,
        buttonPositiveColor: Int,
        buttonNegativeColor: Int,
        onPositiveClick: () -> Unit
    )

    fun showTopSheetDialog(
        title: String,
        message: String,
        imageUrl: String? = null,
        buttonPositiveText: String? = null,
        buttonNegativeText: String? = null,
        buttonPositiveColor: Int? = null,
        buttonNegativeColor: Int? = null,
        onPositiveClick: (() -> Unit)? = null
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
