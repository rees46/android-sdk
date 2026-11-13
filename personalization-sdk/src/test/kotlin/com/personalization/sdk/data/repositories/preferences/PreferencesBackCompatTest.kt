package com.personalization.sdk.data.repositories.preferences

import android.content.Context
import com.personalization.sdk.domain.repositories.PreferencesRepository
import com.personalization.sdk.domain.usecases.preferences.InitPreferencesUseCase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The forms with `legacyPreferencesKey` that earlier releases had stay, deprecated: code built against
 * them keeps compiling and running, and they open the partition as the current forms do.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PreferencesBackCompatTest {

    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `the data source and the repository take the old form and open the partition`() {
        val source = mockk<PreferencesDataSource>(relaxed = true)
        val repository: PreferencesRepository = PreferencesRepositoryImpl(source)

        repository.initialize(context, "partition", "old_key", "shop")
        repository.initialize(context = context, preferencesKey = "partition", legacyPreferencesKey = null)

        verify(exactly = 1) { source.initialize(context, "partition", "shop") }
        verify(exactly = 1) { source.initialize(context, "partition", null) }
    }

    @Test
    fun `the use case takes the old form and opens the partition`() {
        val repository = mockk<PreferencesRepository>(relaxed = true)

        InitPreferencesUseCase(repository).invoke(context, "partition", "old_key", "shop")

        verify(exactly = 1) { repository.initialize(context, "partition", "shop") }
    }

    @Test
    fun `the data source's old form opens the partition`() {
        val source = PreferencesDataSourceImpl()
        val real = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()

        source.initialize(real, "back_compat_partition", "old_key", "shop")
        source.saveValue("probe", "value")

        org.junit.Assert.assertEquals(
            "value",
            real.getSharedPreferences("back_compat_partition", Context.MODE_PRIVATE).getString("probe", null),
        )
    }
}
