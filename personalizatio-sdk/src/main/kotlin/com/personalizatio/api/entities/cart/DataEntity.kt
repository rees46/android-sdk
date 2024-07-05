package com.personalizatio.api.entities.cart

import com.google.gson.annotations.SerializedName

data class DataEntity(
    @SerializedName("items")
    val items: List<ItemEntity>
)
