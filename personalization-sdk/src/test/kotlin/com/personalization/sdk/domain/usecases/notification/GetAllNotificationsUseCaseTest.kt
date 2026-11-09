package com.personalization.sdk.domain.usecases.notification

import com.personalization.api.params.NotificationChannel
import com.personalization.api.params.NotificationChannels
import com.personalization.api.params.NotificationType
import com.personalization.api.params.NotificationTypes
import com.personalization.sdk.domain.repositories.NetworkRepository
import com.personalization.sdk.domain.repositories.NotificationRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `GET /notifications` authenticates the shop by its secret: it has to travel with the request. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GetAllNotificationsUseCaseTest {

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var networkRepository: NetworkRepository
    private lateinit var useCase: GetAllNotificationsUseCase
    private val methodSlot = slot<String>()
    private val paramsSlot = slot<JSONObject>()

    @Before
    fun setUp() {
        notificationRepository = mockk(relaxed = true)
        networkRepository = mockk(relaxed = true)
        every {
            notificationRepository.getAllNotificationsParams(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers { JSONObject().put("date_from", arg<String>(4)) }
        every { networkRepository.getSecretAsync(capture(methodSlot), capture(paramsSlot), any()) } just Runs
        useCase = GetAllNotificationsUseCase(notificationRepository, networkRepository)
    }

    @Test
    fun `the request carries the shop secret`() {
        useCase.invoke(
            email = "user@example.com",
            dateFrom = "2026-01-01",
            types = types(),
            channels = channels(),
            shopSecret = "secret",
            onGetAllNotifications = {}
        )

        assertEquals("notifications", methodSlot.captured)
        assertEquals("secret", paramsSlot.captured.getString("shop_secret"))
        assertEquals("2026-01-01", paramsSlot.captured.getString("date_from"))
    }

    @Test
    fun `the flow variant carries the shop secret`() {
        useCase.invoke(
            dateFrom = "2026-01-01",
            types = types(),
            channels = channels(),
            shopSecret = "secret"
        )

        assertEquals("secret", paramsSlot.captured.getString("shop_secret"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun `the variant without a secret still sends what it sent`() {
        useCase.invoke(
            dateFrom = "2026-01-01",
            types = types(),
            channels = channels(),
            onGetAllNotifications = {}
        )

        assertFalse(paramsSlot.captured.has("shop_secret"))
        assertEquals("2026-01-01", paramsSlot.captured.getString("date_from"))
    }

    private fun types(): NotificationTypes = NotificationTypes.of(NotificationType.BULK)

    private fun channels(): NotificationChannels = NotificationChannels.of(NotificationChannel.EMAIL)
}
