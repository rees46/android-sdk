package com.personalization.sdk.data.models.dto.popUp

data class WebPushSettings(
    val publicKey: String,
    val safariEnabled: Boolean,
    val safariId: String,
    val serviceWorker: String
)
