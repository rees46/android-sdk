package com.personalization.domain.repositories

import com.personalization.domain.models.RecommendedBy

interface RecommendationRepository {

    fun getRecommendedBy() : RecommendedBy?
    fun setRecommendedBy(recommendedBy: RecommendedBy?)
}
