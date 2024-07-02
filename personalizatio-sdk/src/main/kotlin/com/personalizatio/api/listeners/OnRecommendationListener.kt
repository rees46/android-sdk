package com.personalizatio.api.listeners

import com.personalizatio.entities.recommendation.RecommendationEntity
import com.personalizatio.entities.recommendation.ExtendedRecommendationEntity

interface OnRecommendationListener {

    fun onGetRecommendation(recommendationEntity: RecommendationEntity) {}

    fun onGetExtendedRecommendation(extendedRecommendationEntity: ExtendedRecommendationEntity) {}
}
