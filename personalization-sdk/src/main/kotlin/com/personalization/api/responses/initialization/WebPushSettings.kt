package com.personalization.api.responses.initialization

data class WebPushSettings(
    val publicKey: String,
    val safariEnabled: Boolean,
    val safariId: String,
    val serviceWorker: String
)
