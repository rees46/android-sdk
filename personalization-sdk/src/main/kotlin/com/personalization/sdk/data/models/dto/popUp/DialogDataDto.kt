package com.personalization.sdk.data.models.dto.popUp

data class DialogDataDto(
    val title: String,
    val message: String,
    val imageUrl: String,
    val buttonConfirmColor: Int,
    val buttonDeclineColor: Int,
    val buttonConfirmText: String,
    val buttonDeclineText: String,
    val onConfirmClick: () -> Unit,
    val position: Position
)
