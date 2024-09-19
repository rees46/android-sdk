package com.personalization.inAppNotification.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import com.personalization.databinding.CustomSnackbarBinding

class CustomSnackbar(private val rootView: View) {

    fun show(
        message: String,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    ) {
        val snackbar = Snackbar.make(
            /* view = */rootView,
            /* text = */message,
            /* duration = */Snackbar.LENGTH_INDEFINITE
        )

        CustomSnackbarBinding.inflate(LayoutInflater.from(rootView.context)).apply {

            snackbarMessage.text = message

            snackbarPositiveButton.setOnClickListener {
                onPositiveClick()
                snackbar.dismiss()
            }

            snackbarNegativeButton.setOnClickListener {
                onNegativeClick()
                snackbar.dismiss()
            }

            (snackbar.view as ViewGroup).apply {
                removeAllViews()
                addView(root)
            }
        }

        snackbar.show()
    }
}
