package com.personalization.inAppNotification.view

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.personalization.databinding.AlertDialogBinding

class DefaultAlertDialog : DialogFragment() {

    private val binding: AlertDialogBinding by lazy {
        AlertDialogBinding.inflate(layoutInflater)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        with(binding) {
            title.text = arguments?.getString(TITLE_KEY).orEmpty()
            message.text = arguments?.getString(MESSAGE_KEY).orEmpty()
            button.text = arguments?.getString(BUTTON_TEXT_KEY).orEmpty()
            button.setOnClickListener { dismiss() }
        }
    }

    companion object {
        const val TAG = "AlertDialog"
        const val TITLE_KEY = "TITLE_KEY"
        const val MESSAGE_KEY = "MESSAGE_KEY"
        const val BUTTON_TEXT_KEY = "BUTTON_TEXT_KEY"

        fun newInstance(
            title: String,
            message: String,
            buttonText: String
        ): DefaultAlertDialog {
            val dialog = DefaultAlertDialog()
            val args = Bundle().apply {
                putString(TITLE_KEY, title)
                putString(MESSAGE_KEY, message)
                putString(BUTTON_TEXT_KEY, buttonText)
            }
            dialog.arguments = args
            return dialog
        }
    }
}