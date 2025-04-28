package com.personalization.inAppNotification.view.component.dialog.topSheet.container

import com.personalization.databinding.TopSheetDialogBinding
import com.personalization.inAppNotification.view.component.container.InAppViewContainer

class TopSheetDialogViewContainer(binding: TopSheetDialogBinding) : InAppViewContainer {
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