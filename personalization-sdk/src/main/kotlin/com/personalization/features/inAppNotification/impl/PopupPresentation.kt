package com.personalization.features.inAppNotification.impl

import androidx.fragment.app.FragmentActivity
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The host's say in where popups go, shared by one SDK instance and its popup manager. The SDK
 * writes it from its public settings, the manager reads it for every popup.
 */
@Singleton
internal class PopupPresentation @Inject constructor() {

    /** Returns the activity to show a popup in, or null to keep it from being shown. */
    @Volatile
    var listener: ((PopupDto) -> FragmentActivity?)? = null

    /** Whether popups go to the activity on screen when there is no [listener]. */
    @Volatile
    var autoPresentation: Boolean = true
}
