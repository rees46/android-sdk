package com.personalizatio.domain.features.recommendation.usecase

import com.personalizatio.domain.models.RecommendedBy
import com.personalizatio.domain.repositories.RecommendationRepository
import javax.inject.Inject

class SetRecommendedByUseCase @Inject constructor(
    private val recommendationRepository: RecommendationRepository
) {

    operator fun invoke(recommendedBy: RecommendedBy?) {
        recommendationRepository.setRecommendedBy(recommendedBy)
    }
}
