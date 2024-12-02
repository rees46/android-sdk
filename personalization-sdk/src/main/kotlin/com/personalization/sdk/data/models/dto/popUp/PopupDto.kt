package com.personalization.sdk.data.models.dto.popUp

data class PopupDto(
    val id: Int,
    val channels: List<String>,
    val position: Position,
    val delay: Int,
    val html: String,
    val components: Components?,
    val webPushSystem: Boolean,
    val popupActions: PopupActions?
)
