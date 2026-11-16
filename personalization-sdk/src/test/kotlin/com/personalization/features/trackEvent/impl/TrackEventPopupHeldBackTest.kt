package com.personalization.features.trackEvent.impl

import com.personalization.LogLevel
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.core.internal.EventAnswerListener
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.recommendation.GetRecommendedByUseCase
import com.personalization.sdk.domain.usecases.trackingSource.GetTrackingSourceUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * Decision 2 at the tracking methods: a popup in an event's answer is shown only when the queue let it (the
 * event went within 10 s of being made, in the foreground). One the queue held back is not shown and is
 * logged with why, and the app's listener hears the whole answer either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrackEventPopupHeldBackTest {

    private val shown = CopyOnWriteArrayList<PopupDto>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val handed = slot<OnApiCallbackListener>()
    private lateinit var impl: TrackEventManagerImpl

    @Before
    fun setUp() {
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register(SHOP, LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
        val network = mockk<SendNetworkMethodUseCase>(relaxed = true)
        every { network.postAsync(any(), any(), capture(handed)) } just Runs
        val popups = mockk<InAppNotificationManager>(relaxed = true)
        every { popups.shopPopUp(any()) } answers { shown += firstArg<PopupDto>() }
        val settings = mockk<GetUserSettingsValueUseCase>(relaxed = true)
        every { settings.getShopId() } returns SHOP
        val recommendedBy = mockk<GetRecommendedByUseCase>(relaxed = true)
        every { recommendedBy.invoke() } returns null
        impl = TrackEventManagerImpl(
            recommendedBy,
            mockk(relaxed = true),
            network,
            popups,
            settings,
            mockk<GetTrackingSourceUseCase>(relaxed = true).also { every { it.invoke() } returns null },
        )
    }

    @After
    fun tearDown() {
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    /** Tracks a custom event and answers it as the queue does: [heldBack] set on its listener first. */
    private fun answered(heldBack: String?): List<String> {
        val heard = CopyOnWriteArrayList<String>()
        impl.trackEvent("e", null, null, null, null, null, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                heard += response.toString()
            }
        })
        val listener = handed.captured as EventAnswerListener
        listener.popupHeldBack = heldBack
        listener.onSuccess(JSONObject(ANSWER))
        return heard
    }

    @Test
    fun `a popup the queue held back is not shown and is logged with why, and the app hears the whole answer`() {
        listOf(LogReasons.LATE_EVENT, LogReasons.APP_IN_BACKGROUND).forEach { reason ->
            assertEquals(listOf(JSONObject(ANSWER).toString()), answered(reason))
        }
        assertTrue("held back: not shown", shown.isEmpty())
        val notShown = logged.filter { it.code == LogCodes.POPUP_NOT_SHOWN }
        assertEquals(listOf(LogReasons.LATE_EVENT, LogReasons.APP_IN_BACKGROUND), notShown.map { it.context["reason"] })
        assertTrue(notShown.all { it.level == LogLevel.DEBUG })

        // In time and in the foreground: shown.
        assertEquals(listOf(JSONObject(ANSWER).toString()), answered(null))
        assertEquals(listOf(7), shown.map { it.id })
    }

    private companion object {
        const val SHOP = "popup-shop"

        // components and popup_actions arrive as JSON strings, not objects.
        val ANSWER = """
            {"status":"success","popup":{
              "id":7,"position":"centered",
              "components":"{\"header\":\"Title\",\"text\":\"Body\"}",
              "popup_actions":"{\"close\":{\"button_text\":\"Close\"}}"
            }}
        """.trimIndent()
    }
}
