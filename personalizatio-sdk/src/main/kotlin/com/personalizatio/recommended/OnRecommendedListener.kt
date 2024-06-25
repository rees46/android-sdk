package com.personalizatio.recommended

import com.personalizatio.entities.recommended.RecommendedEntity

interface OnRecommendedListener {
    fun onGetRecommended(recommendedEntity: RecommendedEntity) {}
}
