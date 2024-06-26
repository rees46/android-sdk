package com.personalizatio.recommended

import com.personalizatio.entities.recommended.RecommendedEntity
import com.personalizatio.entities.recommended.RecommendedFullEntity

interface OnRecommendedListener {
    fun onGetRecommended(recommendedEntity: RecommendedEntity) {}

    fun onGetRecommended(recommendedFullEntity: RecommendedFullEntity) {}
}
