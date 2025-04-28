package com.personalization.inAppNotification.view.component.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.personalization.databinding.AlertDialogBinding
import com.personalization.ui.animation.button.addPressEffectDeclarative
import com.personalization.ui.click.NotificationClickListener
import com.personalization.utils.BundleUtils.getOptionalInt

class AlertDialog : DialogFragment() {

    private var listener: NotificationClickListener? = null

    fun setListener(listener: NotificationClickListener) {
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
        } else {
            binding.imageContainer.isVisible = false
        }
    }

    private fun initDeclineButton() {
        val buttonText = arguments?.getString(BUTTON_NEGATIVE_TEXT_KEY).orEmpty()
        val negativeButtonColor = arguments?.getOptionalInt(BUTTON_NEGATIVE_COLOR_KEY)
        with(binding) {
            buttonContainer.apply {
                if (buttonText.isEmpty()) {
                    buttonDeclineContainer.isVisible = false
                    return
                }
                buttonDeclineContainer.addPressEffectDeclarative()
                buttonDecline.text = buttonText
                negativeButtonColor?.let { buttonDecline.setBackgroundColor(it) }
                buttonDeclineContainer.setOnClickListener {
                    onButtonClick(isPositiveClick = false)
                }
            }
        }
    }

    private fun initAcceptButton() {
        val buttonText = arguments?.getString(BUTTON_POSITIVE_TEXT_KEY).orEmpty()
        val positiveButtonColor = arguments?.getOptionalInt(BUTTON_POSITIVE_COLOR_KEY)
        with(binding) {
            buttonContainer.apply {
                if (buttonText.isEmpty()) {
                    buttonAcceptContainer.isVisible = false
                    return
                }
                buttonAcceptContainer.addPressEffectDeclarative()
                buttonAccept.text = buttonText
                positiveButtonColor?.let { buttonAccept.setBackgroundColor(it) }
                buttonAcceptContainer.setOnClickListener {
                    onButtonClick(isPositiveClick = true)
                }
            }
        }
    }

    private fun handleClosingButton() {
        with(binding) {
            closeButton.addPressEffectDeclarative()
            closeButton.setOnClickListener { dismiss() }
        }
    }

    private fun onButtonClick(isPositiveClick: Boolean) {
        when (isPositiveClick) {
            true -> listener?.onPositiveClick()
            else -> listener?.onNegativeClick()
        }
        dismiss()
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
