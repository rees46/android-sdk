package com.personalization.sdk.data.models.dto.popUp

data class DialogDataDto(
    val title: String,
    val message: String,
    val imageUrl: String,
    val buttonPositiveColor: Int,
    val buttonNegativeColor: Int,
    val buttonPositiveText: String,
    val buttonNegativeText: String,
    val onPositiveClick: () -> Unit,
    val position: Position
)
