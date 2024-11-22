package com.personalization.api.responses.initialization

data class Popup(
    val id: Int,
    val channels: List<String>,
    val position: Position,
    val delay: Int,
    val html: String,
    val components: Components?,
    val webPushSystem: Boolean,
    val popupActions: PopupActions?
)
