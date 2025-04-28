package com.personalization.inAppNotification.view.component.dialog.fullScreen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.personalization.databinding.FullScreenDialogBinding
import com.personalization.inAppNotification.view.component.container.InAppViewContainer
import com.personalization.inAppNotification.view.component.dialog.BaseInAppDialog
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_NEGATIVE_COLOR_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_NEGATIVE_TEXT_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_POSITIVE_COLOR_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_POSITIVE_TEXT_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.IMAGE_URL_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.MESSAGE_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.TITLE_KEY

const val FULL_SCREEN_DIALOG_TAG = "FullScreenDialog"

class FullScreenDialog : BaseInAppDialog() {

    private var _binding: FullScreenDialogBinding? = null
    private val binding get() = _binding!!

    class FullScreenDialogViewContainer(binding: FullScreenDialogBinding) : InAppViewContainer {
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
        FullScreenDialogViewContainer(binding)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FullScreenDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            title: String,
            message: String,
            imageUrl: String?,
            buttonPositiveText: String?,
            buttonNegativeText: String?,
            buttonPositiveColor: Int?,
            buttonNegativeColor: Int?
        ): FullScreenDialog {
            val dialog = FullScreenDialog()
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
