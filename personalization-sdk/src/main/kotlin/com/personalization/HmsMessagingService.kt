package com.personalization

import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage

class HmsMessagingService : HmsMessageService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isNotEmpty()) {
            SDK.onHmsNewToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (BuildConfig.DEBUG && message.dataOfMap.isNotEmpty()) {
            SDK.debug("HMS message data: ${message.dataOfMap}")
        }
    }
}
