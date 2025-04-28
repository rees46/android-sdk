package com.personalization.inAppNotification.view.component.snackbar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import com.personalization.databinding.CustomSnackbarBinding

class Snackbar(private val rootView: View) {

    fun show(
        message: String,
        buttonConfirmText: String,
        buttonDeclineText: String,
        onConfirmClick: () -> Unit,
        onDeclineClick: () -> Unit
    ) {
        val snackbar = Snackbar.make(
            /* view = */ rootView,
            /* text = */ message,
            /* duration = */ Snackbar.LENGTH_INDEFINITE
        )

        val binding = CustomSnackbarBinding.inflate(LayoutInflater.from(rootView.context))

        with(binding) {
            title.text = message
            confirmButton.text = buttonConfirmText
            declineButton.text = buttonDeclineText

            confirmButton.setOnClickListener {
                onConfirmClick()
                snackbar.dismiss()
            }

            declineButton.setOnClickListener {
                onDeclineClick()
                snackbar.dismiss()
            }
        }

        val snackbarView = snackbar.view
        val layoutParams = snackbarView.layoutParams as ViewGroup.MarginLayoutParams

        layoutParams.setMargins(0, 0, 0, 0)
        snackbarView.layoutParams = layoutParams
        snackbarView.setBackgroundColor(Color.TRANSPARENT)

        (snackbarView as ViewGroup).apply {
            removeAllViews()
            addView(
                binding.root,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        snackbar.show()
    }
}
