package com.personalization.sdk.domain.usecases.inAppNotification

import com.personalization.api.managers.InAppNotificationManager
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import javax.inject.Inject

class InAppNotificationUseCase @Inject constructor(
    private val inAppNotificationManager: InAppNotificationManager
) {
    fun execute(popupDto: PopupDto) {
        inAppNotificationManager.shopPopUp(popupDto = popupDto)
    }
}
