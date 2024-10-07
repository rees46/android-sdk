package com.personalization.inAppNotification.view

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.personalization.databinding.AlertDialogBinding
import com.personalization.inAppNotification.utils.button.addPressEffect

class DefaultAlertDialog : DialogFragment() {

    private var listener: AlertDialogListener? = null

    fun setListener(listener: AlertDialogListener) {
        this.listener = listener
    }

    private var _binding: AlertDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AlertDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        initImage()
        initTextBlock()
        initAcceptButton()
        initDeclineButton()
        handleClosingButton()
    }

    private fun initTextBlock() {
        with(binding) {
            textContainer.apply {
                title.text = arguments?.getString(TITLE_KEY).orEmpty()
                message.text = arguments?.getString(MESSAGE_KEY).orEmpty()
            }
        }
    }

    private fun initImage() {
        val imageUrl = arguments?.getString(IMAGE_URL_KEY).orEmpty()
        if (imageUrl.isNotBlank()) {
            binding.backgroundImageView.loadImage(imageUrl)
        }
    }

    private fun initDeclineButton() {
        val negativeButtonColor = arguments?.getInt(BUTTON_NEGATIVE_COLOR_KEY)
        with(binding) {
            buttonContainer.apply {
                buttonDeclineContainer.addPressEffect()
                buttonDecline.text = arguments?.getString(BUTTON_NEGATIVE_TEXT_KEY).orEmpty()
                if (negativeButtonColor != null) {
                    buttonDecline.setBackgroundColor(negativeButtonColor)
                }
                buttonDeclineContainer.setOnClickListener {
                    listener?.onNegativeButtonClick()
                    dismiss()
                }
            }
        }
    }

    private fun initAcceptButton() {
        val positiveButtonColor = arguments?.getInt(BUTTON_POSITIVE_COLOR_KEY)
        with(binding) {
            buttonContainer.apply {
                buttonAcceptContainer.addPressEffect()
                buttonAccept.text = arguments?.getString(BUTTON_POSITIVE_TEXT_KEY).orEmpty()
                if (positiveButtonColor != null) {
                    buttonAccept.setBackgroundColor(positiveButtonColor)
                }
                buttonAcceptContainer.setOnClickListener {
                    listener?.onPositiveButtonClick()
                    dismiss()
                }
            }
        }
    }

    private fun handleClosingButton() {
        with(binding) {
            closeButton.addPressEffect()
            closeButton.setOnClickListener { dismiss() }
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
        const val IMAGE_URL_KEY = "IMAGE_URL_KEY"
        const val BUTTON_POSITIVE_COLOR_KEY = "BUTTON_POSITIVE_COLOR_KEY"
        const val BUTTON_NEGATIVE_COLOR_KEY = "BUTTON_NEGATIVE_COLOR_KEY"
        const val BUTTON_POSITIVE_TEXT_KEY = "BUTTON_POSITIVE_TEXT_KEY"
        const val BUTTON_NEGATIVE_TEXT_KEY = "BUTTON_NEGATIVE_TEXT_KEY"

        fun newInstance(
            title: String,
            message: String,
            imageUrl: String,
            buttonPositiveText: String,
            buttonNegativeText: String,
            buttonPositiveColor: Int,
            buttonNegativeColor: Int,
        ): DefaultAlertDialog {
            val dialog = DefaultAlertDialog()
            val args = Bundle().apply {
                putString(TITLE_KEY, title)
                putString(MESSAGE_KEY, message)
                putString(IMAGE_URL_KEY, imageUrl)
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
