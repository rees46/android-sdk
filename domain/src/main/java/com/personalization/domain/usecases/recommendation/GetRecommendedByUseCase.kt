package com.personalization.domain.usecases.recommendation

import com.personalization.domain.models.RecommendedBy
import com.personalization.domain.repositories.RecommendationRepository
import javax.inject.Inject

class GetRecommendedByUseCase @Inject constructor(
    private val recommendationRepository: RecommendationRepository
) {

    operator fun invoke() : RecommendedBy? {
        return recommendationRepository.getRecommendedBy()
    }
}
