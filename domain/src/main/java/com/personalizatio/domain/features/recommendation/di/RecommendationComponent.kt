package com.personalizatio.domain.features.recommendation.di

import dagger.Subcomponent

@Subcomponent
interface RecommendationComponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(): RecommendationComponent
    }
}
