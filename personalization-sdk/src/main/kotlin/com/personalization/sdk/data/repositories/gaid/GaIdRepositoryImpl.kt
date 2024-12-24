package com.personalization.sdk.data.repositories.gaid

import android.content.Context
import com.personalization.sdk.domain.repositories.GaIdRepository
import javax.inject.Inject

class GaIdRepositoryImpl @Inject constructor(
    private val gaIdDataSource: GaIdDataSource
) : GaIdRepository {
    override suspend fun fetchAdId(context: Context): String =
        gaIdDataSource.fetchGaId(context)
}