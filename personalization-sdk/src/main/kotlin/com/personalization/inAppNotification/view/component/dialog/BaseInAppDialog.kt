package com.personalization.inAppNotification.view.component.dialog

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.personalization.inAppNotification.view.component.container.InAppViewContainer
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_DECLINE_COLOR_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_DECLINE_TEXT_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_CONFIRM_COLOR_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_CONFIRM_TEXT_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.IMAGE_URL_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.MESSAGE_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.TITLE_KEY
import com.personalization.ui.animation.button.addPressEffectDeclarative
import com.personalization.ui.click.NotificationClickListener
import com.personalization.utils.BundleUtils.getOptionalInt

abstract class BaseInAppDialog : DialogFragment() {

    protected abstract val container: InAppViewContainer
    private var _listener: NotificationClickListener? = null

    var listener: NotificationClickListener?
        get() = _listener
        set(value) {
            _listener = value
        }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDialog()
    }

    private fun setupDialog() {
        initImage()
        initTextBlock()
        initConfirmButton()
        initDeclineButton()
        handleCloseButton()
    }

    private fun initImage() {
        val imageUrl = arguments?.getString(IMAGE_URL_KEY).orEmpty()
        if (imageUrl.isNotBlank()) {
            container.backgroundImageView.loadImage(imageUrl)
        } else {
            container.imageContainer.visibility = View.GONE
        }
    }

    private fun initTextBlock() {
        container.titleTextView.text = arguments?.getString(TITLE_KEY).orEmpty()
        container.messageTextView.text = arguments?.getString(MESSAGE_KEY).orEmpty()
    }

    private fun initConfirmButton() {
        val buttonText = arguments?.getString(BUTTON_CONFIRM_TEXT_KEY).orEmpty()
        val confirmColor = arguments?.getOptionalInt(BUTTON_CONFIRM_COLOR_KEY)

        if (buttonText.isEmpty()) {
            container.buttonConfirmContainer.isVisible = false
            return
        }

        container.buttonConfirmContainer.addPressEffectDeclarative()
        container.buttonConfirm.text = buttonText
        confirmColor?.let { container.buttonConfirm.setBackgroundColor(it) }
        container.buttonConfirmContainer.setOnClickListener {
            onButtonClick(true)
        }
    }

    private fun initDeclineButton() {
        val buttonText = arguments?.getString(BUTTON_DECLINE_TEXT_KEY).orEmpty()
        val declineColor = arguments?.getOptionalInt(BUTTON_DECLINE_COLOR_KEY)

        if (buttonText.isEmpty()) {
            container.buttonDeclineContainer.isVisible = false
            return
        }

        container.buttonDeclineContainer.addPressEffectDeclarative()
        container.buttonDecline.text = buttonText
        declineColor?.let { container.buttonDecline.setBackgroundColor(it) }
        container.buttonDeclineContainer.setOnClickListener {
            onButtonClick(false)
        }
    }

    private fun handleCloseButton() {
        container.closeButton.addPressEffectDeclarative()
        container.closeButton.setOnClickListener { dismiss() }
    }

    private fun onButtonClick(isConfirmClick: Boolean) {
        when (isConfirmClick) {
            true -> listener?.onConfirmClick()
            false -> listener?.onDeclineClick()
        }
        dismiss()
    }
}
