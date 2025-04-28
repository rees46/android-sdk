@file:Suppress("NewApi")

package com.personalization.inAppNotification.view.component.dialog

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.personalization.databinding.BottomSheetDialogBinding
import com.personalization.ui.animation.button.addPressEffectDeclarative
import com.personalization.ui.click.NotificationClickListener
import com.personalization.utils.BundleUtils.getOptionalInt

class BottomSheetDialog : BottomSheetDialogFragment() {

    private var listener: NotificationClickListener? = null

    fun setListener(listener: NotificationClickListener) {
        this.listener = listener
    }

    private var _binding: BottomSheetDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDialogBinding.inflate(inflater, container, false)
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

        (view.parent as View).apply {
            backgroundTintMode = PorterDuff.Mode.CLEAR
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setBackgroundColor(Color.TRANSPARENT)
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

    private fun initDeclineButton() {
        val buttonText = arguments?.getString(AlertDialog.Companion.BUTTON_NEGATIVE_TEXT_KEY).orEmpty()
        val negativeButtonColor = arguments?.getOptionalInt(AlertDialog.Companion.BUTTON_NEGATIVE_COLOR_KEY)
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
        val buttonText = arguments?.getString(AlertDialog.Companion.BUTTON_POSITIVE_TEXT_KEY).orEmpty()
        val positiveButtonColor = arguments?.getOptionalInt(AlertDialog.Companion.BUTTON_POSITIVE_COLOR_KEY)
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
        const val TAG = "BottomSheetDialog"
        const val TITLE_KEY = "TITLE_KEY"
        const val IMAGE_URL_KEY = "IMAGE_URL_KEY"
        const val MESSAGE_KEY = "MESSAGE_KEY"
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
        ): BottomSheetDialog {
            val dialog = BottomSheetDialog()
            val args = Bundle().apply {
                putString(AlertDialog.Companion.TITLE_KEY, title)
                putString(AlertDialog.Companion.MESSAGE_KEY, message)
                putString(AlertDialog.Companion.IMAGE_URL_KEY, imageUrl)
                putString(AlertDialog.Companion.BUTTON_POSITIVE_TEXT_KEY, buttonPositiveText)
                putString(AlertDialog.Companion.BUTTON_NEGATIVE_TEXT_KEY, buttonNegativeText)
                buttonPositiveColor?.let { putInt(AlertDialog.Companion.BUTTON_POSITIVE_COLOR_KEY, it) }
                buttonNegativeColor?.let { putInt(AlertDialog.Companion.BUTTON_NEGATIVE_COLOR_KEY, it) }
            }
            dialog.arguments = args
            return dialog
        }
    }
}
