package com.personalizatio.domain.usecases.notification

import com.personalizatio.domain.repositories.SourceRepository
import javax.inject.Inject

class GetSourceObjectUseCase @Inject constructor(
    private val sourceRepository: SourceRepository
) {

    operator fun invoke(
        timeDuration: Int
    ) = sourceRepository.getJsonObject(
        timeDuration = timeDuration
    )
}
