package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.userSettings.UserSettingsRepositoryImpl
import com.personalization.sdk.domain.usecases.userSettings.InitUserSettingsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The A/B-testing segment is a sticky per-device bucket: assigned once, then stable for the device's
 * lifetime (and kept as-is when migrated from a previous install). It must NOT be re-rolled on every
 * init — doing so would flip the device between A and B each launch and discard a migrated segment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SegmentAbTestingTest {

    private lateinit var context: Context
    private val shopId = "shop-a"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric keeps SharedPreferences between tests in the same process — clear the partition.
        context.getSharedPreferences(PreferencesPartition.keyFor(shopId), Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun repository(): UserSettingsRepositoryImpl {
        val source = PreferencesDataSourceImpl().apply {
            initialize(context = context, preferencesKey = PreferencesPartition.keyFor(shopId))
        }
        return UserSettingsRepositoryImpl(source)
    }

    @Test
    fun `the segment is assigned once and stays stable across reads and instances`() {
        val repo = repository()

        val assigned = repo.getSegmentForABTesting()
        assertTrue("segment must be A or B, was $assigned", assigned == "A" || assigned == "B")
        assertEquals(assigned, repo.getSegmentForABTesting())
        // A fresh repository over the same partition reads the persisted bucket, not a fresh roll.
        assertEquals(assigned, repository().getSegmentForABTesting())
    }

    @Test
    fun `init does not re-roll the segment`() {
        val repo = repository()
        val assigned = repo.getSegmentForABTesting()

        // If init re-rolled the segment, 20 inits would almost certainly flip it at least once.
        repeat(20) { InitUserSettingsUseCase(repo).invoke(shopId = shopId, stream = "android") }

        assertEquals(assigned, repo.getSegmentForABTesting())
    }
}
