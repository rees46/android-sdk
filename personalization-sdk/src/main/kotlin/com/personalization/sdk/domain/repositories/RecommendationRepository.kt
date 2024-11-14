package com.personalization.sdk.domain.repositories

import com.personalization.sdk.domain.models.RecommendedBy

interface RecommendationRepository {

    fun getRecommendedBy(): RecommendedBy?
    fun setRecommendedBy(recommendedBy: RecommendedBy?)
}
