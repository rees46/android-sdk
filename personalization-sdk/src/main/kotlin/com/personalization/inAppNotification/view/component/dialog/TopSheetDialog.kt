@file:Suppress("NewApi")

package com.personalization.inAppNotification.view.component.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.personalization.databinding.TopSheetDialogBinding
import com.personalization.ui.animation.button.addPressEffectDeclarative
import com.personalization.ui.click.NotificationClickListener

const val AMOUNT = 0.5f
const val TITLE_KEY = "TITLE_KEY"
const val IMAGE_URL_KEY = "IMAGE_URL_KEY"
const val MESSAGE_KEY = "MESSAGE_KEY"
const val BUTTON_POSITIVE_COLOR_KEY = "BUTTON_POSITIVE_COLOR_KEY"
const val BUTTON_NEGATIVE_COLOR_KEY = "BUTTON_NEGATIVE_COLOR_KEY"
const val BUTTON_POSITIVE_TEXT_KEY = "BUTTON_POSITIVE_TEXT_KEY"
const val BUTTON_NEGATIVE_TEXT_KEY = "BUTTON_NEGATIVE_TEXT_KEY"

class TopSheetDialog : DialogFragment() {

    private var listener: NotificationClickListener? = null

    fun setListener(listener: NotificationClickListener) {
        this.listener = listener
    }

    private var _binding: TopSheetDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TopSheetDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initImage()
        initTextBlock()
        initAcceptButton()
        initDeclineButton()
        handleClosingButton()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.TOP)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(AMOUNT)
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

    private fun initTextBlock() {
        with(binding) {
            textContainer.apply {
                title.text = arguments?.getString(TITLE_KEY).orEmpty()
                message.text = arguments?.getString(MESSAGE_KEY).orEmpty()
            }
        }
    }

    private fun initAcceptButton() {
        val buttonText = arguments?.getString(BUTTON_POSITIVE_TEXT_KEY) ?: ""
        val positiveButtonColor = arguments?.getInt(BUTTON_POSITIVE_COLOR_KEY)
        with(binding) {
            buttonContainer.apply {
                if (buttonText.isEmpty()) {
                    buttonAcceptContainer.isVisible = false
                    return
                }
                buttonAcceptContainer.addPressEffectDeclarative()
                buttonAccept.text = buttonText
                if (positiveButtonColor != null) {
                    buttonAccept.setBackgroundColor(positiveButtonColor)
                }
                buttonAcceptContainer.setOnClickListener {
                    onButtonClick(isPositiveClick = true)
                }
            }
        }
    }

    private fun initDeclineButton() {
        val buttonText = arguments?.getString(BUTTON_NEGATIVE_TEXT_KEY) ?: ""
        val negativeButtonColor = arguments?.getInt(BUTTON_NEGATIVE_COLOR_KEY)
        with(binding) {
            buttonContainer.apply {
                if (buttonText.isEmpty()) {
                    buttonDeclineContainer.isVisible = false
                    return
                }
                buttonDeclineContainer.addPressEffectDeclarative()
                buttonDecline.text = buttonText
                if (negativeButtonColor != null) {
                    buttonDecline.setBackgroundColor(negativeButtonColor)
                }
                buttonDeclineContainer.setOnClickListener {
                    onButtonClick(isPositiveClick = false)
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
        const val TAG = "TopSheetDialog"
        
        fun newInstance(
            title: String,
            message: String,
            imageUrl: String?,
            buttonPositiveText: String,
            buttonNegativeText: String?,
            buttonPositiveColor: Int,
            buttonNegativeColor: Int,
        ): TopSheetDialog {
            val dialog = TopSheetDialog()
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
