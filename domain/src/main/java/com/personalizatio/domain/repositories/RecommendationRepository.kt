package com.personalizatio.domain.repositories

import com.personalizatio.domain.models.RecommendedBy

interface RecommendationRepository {

    fun getRecommendedBy() : RecommendedBy?
    fun setRecommendedBy(recommendedBy: RecommendedBy?)
}
