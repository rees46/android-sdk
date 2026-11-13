package com.personalization.sdk.data.repositories.preferences

import android.content.Context
import com.personalization.sdk.domain.repositories.PreferencesRepository
import com.personalization.sdk.domain.usecases.preferences.InitPreferencesUseCase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The parameters of `initialize` and `InitPreferencesUseCase.invoke` stay those of 2.49.0
 * (`context, preferencesKey, legacyPreferencesKey, shopId`): a call written for that release binds as it
 * did — its third argument is the legacy key, not the shop — and opens the partition, the legacy key
 * being ignored.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PreferencesBackCompatTest {

    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `the repository's three-argument call of 2_49_0 binds its third argument to the legacy key`() {
        val source = mockk<PreferencesDataSource>(relaxed = true)
        val repository: PreferencesRepository = PreferencesRepositoryImpl(source)

        repository.initialize(context, "partition", "old_key")
        repository.initialize(context, "partition", "old_key", "shop")
        repository.initialize(context = context, preferencesKey = "partition", shopId = "named")

        verify(exactly = 1) { source.initialize(context, "partition", null, null) }
        verify(exactly = 1) { source.initialize(context, "partition", null, "shop") }
        verify(exactly = 1) { source.initialize(context, "partition", null, "named") }
    }

    @Test
    fun `the use case takes the forms of 2_49_0`() {
        val repository = mockk<PreferencesRepository>(relaxed = true)
        val useCase = InitPreferencesUseCase(repository)

        useCase.invoke(context, "partition", "old_key")
        useCase.invoke(context, "partition", "old_key", "shop")

        verify(exactly = 1) { repository.initialize(context, "partition", null, null) }
        verify(exactly = 1) { repository.initialize(context, "partition", null, "shop") }
    }

    @Test
    fun `the data source's three-argument call opens the partition and not a shop named like the legacy key`() {
        val source = PreferencesDataSourceImpl()
        val real = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()

        source.initialize(real, "back_compat_partition", "old_key")
        source.saveValue("probe", "value")

        assertEquals("value", real.getSharedPreferences("back_compat_partition", Context.MODE_PRIVATE).getString("probe", null))
        assertEquals(null, real.getSharedPreferences("old_key", Context.MODE_PRIVATE).getString("probe", null))
    }
}
