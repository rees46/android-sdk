package com.personalization.inAppNotification.view.component.dialog.alert.container

import com.personalization.databinding.AlertDialogBinding
import com.personalization.inAppNotification.view.component.container.InAppViewContainer

class AlertDialogViewContainer(binding: AlertDialogBinding) : InAppViewContainer {
    override val backgroundImageView = binding.backgroundImageView
    override val imageContainer = binding.imageContainer
    override val titleTextView = binding.textContainer.title
    override val messageTextView = binding.textContainer.message
    override val buttonConfirmContainer = binding.buttonContainer.buttonConfirmContainer
    override val buttonConfirm = binding.buttonContainer.buttonConfirm
    override val buttonDeclineContainer = binding.buttonContainer.buttonDeclineContainer
    override val buttonDecline = binding.buttonContainer.buttonDecline
    override val closeButton = binding.closeButton
}
