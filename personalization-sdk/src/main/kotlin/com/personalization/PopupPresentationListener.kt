package com.personalization

import androidx.fragment.app.FragmentActivity
import com.personalization.sdk.data.models.dto.popUp.PopupDto

/**
 * Decides where a server popup is shown, or keeps it from being shown.
 *
 * Popups arrive with the responses to init and tracking calls. Without a listener the SDK shows them
 * in the activity on screen (see [SDK.enableAutoPopupPresentation]). Once a listener is set it has the
 * final word, and [SDK.enableAutoPopupPresentation] is no longer consulted.
 *
 * Same contract as `PopupPresentationDelegate` on iOS.
 */
fun interface PopupPresentationListener {

    /**
     * Called on the main thread for each popup, after the SDK has dropped a repeat of one shown
     * within the last minute.
     *
     * @return the activity to show [popup] in, or null to keep the SDK from showing it — to hold it
     * back on a screen where it would be in the way, or to draw it yourself. A popup kept back is not
     * reported as shown and is not remembered, so the server may send it again. One you draw yourself
     * is yours to report: `sdk.tracking.popupShown(popup.id)`.
     */
    fun shouldPresentPopup(sdk: SDK, popup: PopupDto): FragmentActivity?
}
