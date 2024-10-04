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

    private var listener: AlertDialogListener? = null

    fun setListener(listener: AlertDialogListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        with(binding) {
            title.text = arguments?.getString(TITLE_KEY).orEmpty()
            message.text = arguments?.getString(MESSAGE_KEY).orEmpty()
            buttonAccept.text = arguments?.getString(BUTTON_POSITIVE_TEXT_KEY).orEmpty()
            buttonDecline.text = arguments?.getString(BUTTON_NEGATIVE_TEXT_KEY).orEmpty()
            val positiveButtonColor = arguments?.getInt(BUTTON_POSITIVE_COLOR_KEY)
            val negativeButtonColor = arguments?.getInt(BUTTON_NEGATIVE_COLOR_KEY)
            if (positiveButtonColor!=null) {
                buttonAccept.setBackgroundColor(positiveButtonColor)
            }
            if (negativeButtonColor!=null) {
                buttonDecline.setBackgroundColor(negativeButtonColor)
            }
            buttonAccept.setOnClickListener {
                listener?.onPositiveButtonClick()
                dismiss()
            }
            buttonDecline.setOnClickListener {
                listener?.onNegativeButtonClick()
                dismiss()
            }
        }
    }

    interface AlertDialogListener {
        fun onPositiveButtonClick()
        fun onNegativeButtonClick()
    }

    companion object {
        const val TAG = "AlertDialog"
        const val TITLE_KEY = "TITLE_KEY"
        const val MESSAGE_KEY = "MESSAGE_KEY"
        const val BUTTON_POSITIVE_COLOR_KEY = "BUTTON_POSITIVE_COLOR_KEY"
        const val BUTTON_NEGATIVE_COLOR_KEY = "BUTTON_NEGATIVE_COLOR_KEY"
        const val BUTTON_POSITIVE_TEXT_KEY = "BUTTON_POSITIVE_TEXT_KEY"
        const val BUTTON_NEGATIVE_TEXT_KEY = "BUTTON_NEGATIVE_TEXT_KEY"

        fun newInstance(
            title: String,
            message: String,
            buttonPositiveText: String,
            buttonNegativeText: String,
            buttonPositiveColor: Int,
            buttonNegativeColor: Int,
        ): DefaultAlertDialog {
            val dialog = DefaultAlertDialog()
            val args = Bundle().apply {
                putString(TITLE_KEY, title)
                putString(MESSAGE_KEY, message)
                putInt(BUTTON_POSITIVE_COLOR_KEY, buttonPositiveColor)
                putInt(BUTTON_NEGATIVE_COLOR_KEY, buttonNegativeColor)
                putString(BUTTON_POSITIVE_TEXT_KEY, buttonPositiveText)
                putString(BUTTON_NEGATIVE_TEXT_KEY, buttonNegativeText)
            }
            dialog.arguments = args
            return dialog
        }
    }
}
