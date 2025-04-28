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
        buttonConfirmText: String? = null,
        buttonDeclineText: String? = null,
        buttonConfirmColor: Int? = null,
        buttonDeclineColor: Int? = null,
        onConfirmClick: (() -> Unit)? = null
    )

    fun showFullScreenDialog(
        title: String,
        message: String,
        imageUrl: String? = null,
        buttonConfirmText: String? = null,
        buttonDeclineText: String? = null,
        buttonConfirmColor: Int? = null,
        buttonDeclineColor: Int? = null,
        onConfirmClick: (() -> Unit)? = null
    )

    fun showBottomDialog(
        title: String,
        message: String,
        imageUrl: String? = null,
        buttonConfirmText: String? = null,
        buttonDeclineText: String? = null,
        buttonConfirmColor: Int? = null,
        buttonDeclineColor: Int? = null,
        onConfirmClick: (() -> Unit)? = null
    )

    fun showTopDialog(
        title: String,
        message: String,
        imageUrl: String? = null,
        buttonConfirmText: String? = null,
        buttonDeclineText: String? = null,
        buttonConfirmColor: Int? = null,
        buttonDeclineColor: Int? = null,
        onConfirmClick: (() -> Unit)? = null
    )

    fun showSnackBar(
        view: View,
        message: String,
        buttonConfirmText: String,
        buttonDeclineText: String,
        onConfirmClick: () -> Unit,
        onDeclineClick: () -> Unit
    )

}
