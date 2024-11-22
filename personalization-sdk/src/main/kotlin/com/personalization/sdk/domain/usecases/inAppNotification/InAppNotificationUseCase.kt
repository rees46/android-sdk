package com.personalization.sdk.domain.usecases.inAppNotification

import com.personalization.api.managers.InAppNotificationManager
import com.personalization.api.responses.initialization.Popup
import javax.inject.Inject

class InAppNotificationUseCase @Inject constructor(
    private val inAppNotificationManager: InAppNotificationManager
) {
    fun execute(popup: Popup) {
        inAppNotificationManager.shopPopUp(popup = popup)
    }
}
