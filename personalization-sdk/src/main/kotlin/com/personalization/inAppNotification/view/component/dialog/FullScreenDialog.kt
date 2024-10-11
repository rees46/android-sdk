package com.personalization.inAppNotification.view.component.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.personalization.databinding.FullScreenDialogBinding
import com.personalization.ui.animation.button.addPressEffectDeclarative

class FullScreenDialog : DialogFragment() {

    private val binding: FullScreenDialogBinding by lazy {
        FullScreenDialogBinding.inflate(layoutInflater)
    }

    private var listener: FullScreenDialogListener? = null

    fun setListener(listener: FullScreenDialogListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }

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
                buttonDeclineContainer.addPressEffectDeclarative()
                buttonDecline.text = arguments?.getString(BUTTON_NEGATIVE_TEXT_KEY).orEmpty()
                if (negativeButtonColor != null) {
                    buttonDecline.setBackgroundColor(negativeButtonColor)
                }
                buttonDeclineContainer.setOnClickListener {
                    onButtonClick(isPositiveClick = false)
                }
            }
        }
    }

    private fun initAcceptButton() {
        val positiveButtonColor = arguments?.getInt(BUTTON_POSITIVE_COLOR_KEY)
        with(binding) {
            buttonContainer.apply {
                buttonAcceptContainer.addPressEffectDeclarative()
                buttonAccept.text = arguments?.getString(BUTTON_POSITIVE_TEXT_KEY).orEmpty()
                if (positiveButtonColor != null) {
                    buttonAccept.setBackgroundColor(positiveButtonColor)
                }
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
            true -> listener?.onPositiveButtonClick()
            else -> listener?.onNegativeButtonClick()
        }
        dismiss()
    }


    interface FullScreenDialogListener {
        fun onPositiveButtonClick()
        fun onNegativeButtonClick()
    }

    companion object {
        const val TAG = "FullScreenDialog"
        const val TITLE_KEY = "TITLE_KEY"
        const val MESSAGE_KEY = "MESSAGE_KEY"
        const val IMAGE_URL_KEY = "IMAGE_URL_KEY"
        const val BUTTON_POSITIVE_TEXT_KEY = "BUTTON_POSITIVE_TEXT_KEY"
        const val BUTTON_NEGATIVE_TEXT_KEY = "BUTTON_NEGATIVE_TEXT_KEY"
        const val BUTTON_POSITIVE_COLOR_KEY = "BUTTON_POSITIVE_COLOR_KEY"
        const val BUTTON_NEGATIVE_COLOR_KEY = "BUTTON_NEGATIVE_COLOR_KEY"

        fun newInstance(
            title: String,
            message: String,
            imageUrl: String?,
            buttonPositiveText: String,
            buttonNegativeText: String,
            buttonPositiveColor: Int,
            buttonNegativeColor: Int,
        ): FullScreenDialog {
            val dialog = FullScreenDialog()
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
