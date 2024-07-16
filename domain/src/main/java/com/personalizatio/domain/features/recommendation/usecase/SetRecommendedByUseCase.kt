package com.personalizatio.domain.features.recommendation.usecase

import com.personalizatio.data.model.RecommendedBy
import com.personalizatio.data.repository.recommendation.RecommendationRepository
import javax.inject.Inject

class SetRecommendedByUseCase @Inject constructor(
    private val recommendationRepository: RecommendationRepository
) {

    operator fun invoke(recommendedBy: RecommendedBy?) {
        recommendationRepository.recommendedBy = recommendedBy
    }
}
