package com.personalization.inAppNotification.view.component.container

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.personalization.inAppNotification.view.component.image.ImageView

interface InAppViewContainer {
    val backgroundImageView: ImageView
    val imageContainer: ViewGroup
    val titleTextView: TextView
    val messageTextView: TextView
    val buttonAcceptContainer: ViewGroup
    val buttonAccept: TextView
    val buttonDeclineContainer: ViewGroup
    val buttonDecline: TextView
    val closeButton: View
}
