package com.personalization.inAppNotification.view.component.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.personalization.databinding.AlertDialogBinding
import com.personalization.inAppNotification.view.component.container.InAppViewContainer

class AlertDialog : BaseInAppDialog() {

    private var _binding: AlertDialogBinding? = null
    private val binding get() = _binding!!

    class AlertDialogViewContainer(binding: AlertDialogBinding) : InAppViewContainer {
        override val backgroundImageView = binding.backgroundImageView
        override val imageContainer = binding.imageContainer
        override val titleTextView = binding.textContainer.title
        override val messageTextView = binding.textContainer.message
        override val buttonAcceptContainer = binding.buttonContainer.buttonAcceptContainer
        override val buttonAccept = binding.buttonContainer.buttonAccept
        override val buttonDeclineContainer = binding.buttonContainer.buttonDeclineContainer
        override val buttonDecline = binding.buttonContainer.buttonDecline
        override val closeButton = binding.closeButton
    }

    override val container: InAppViewContainer by lazy {
        AlertDialogViewContainer(binding)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setGravity(Gravity.CENTER)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.5f)
        }
    }

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
            imageUrl: String?,
            buttonPositiveText: String?,
            buttonNegativeText: String?,
            buttonPositiveColor: Int?,
            buttonNegativeColor: Int?,
        ): AlertDialog {
            val dialog = AlertDialog()
            val args = Bundle().apply {
                putString(TITLE_KEY, title)
                putString(MESSAGE_KEY, message)
                putString(IMAGE_URL_KEY, imageUrl)
                putString(BUTTON_POSITIVE_TEXT_KEY, buttonPositiveText)
                putString(BUTTON_NEGATIVE_TEXT_KEY, buttonNegativeText)
                buttonPositiveColor?.let { putInt(BUTTON_POSITIVE_COLOR_KEY, it) }
                buttonNegativeColor?.let { putInt(BUTTON_NEGATIVE_COLOR_KEY, it) }
            }
            dialog.arguments = args
            return dialog
        }
    }
}
