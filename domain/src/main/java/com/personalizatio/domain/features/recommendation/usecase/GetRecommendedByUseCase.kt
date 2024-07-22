package com.personalizatio.domain.features.recommendation.usecase

import com.personalizatio.domain.models.RecommendedBy
import com.personalizatio.domain.repositories.RecommendationRepository
import javax.inject.Inject

class GetRecommendedByUseCase @Inject constructor(
    private val recommendationRepository: RecommendationRepository
) {

    operator fun invoke() : RecommendedBy? {
        return recommendationRepository.getRecommendedBy()
    }
}
