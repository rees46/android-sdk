package com.personalization.logs

import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.messaging.RemoteMessage
import com.personalization.LogLevel
import com.personalization.MessagingService
import com.personalization.PushEventType
import com.personalization.PushProvider
import com.personalization.Rees46
import com.personalization.RegisterManager
import com.personalization.SDK
import com.personalization.SdkLogMessage
import com.personalization.SdkRegistry
import com.personalization.api.OnApiCallbackListener
import com.personalization.features.inAppNotification.impl.ForegroundActivity
import com.personalization.features.inAppNotification.impl.InAppNotificationManagerImpl
import com.personalization.features.notification.domain.data.NotificationDataExtractor
import com.personalization.features.notification.domain.data.NotificationDataSender
import com.personalization.features.notification.domain.helpers.NotificationClickProcessor
import com.personalization.push.PushTokenManager
import com.personalization.sdk.data.models.dto.popUp.CloseAction
import com.personalization.sdk.data.models.dto.popUp.Components
import com.personalization.sdk.data.models.dto.popUp.PopupActions
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position
import dagger.Lazy
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/** The SDK writes only the codes of the logging spec's table, each at its level there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LogCodesTest {

    private val received = CopyOnWriteArrayList<SdkLogMessage>()
    private lateinit var previousExecutor: Executor

    @Before
    fun setUp() {
        SdkLogs.reset()
        SdkRegistry.reset()
        Rees46.reset()
        previousExecutor = SdkLogs.handlerExecutor
        SdkLogs.handlerExecutor = Executor { it.run() }
        Rees46.setLogHandler { received += it }
        SdkLogs.register("shop", LogLevel.DEBUG, "SDK")
    }

    @After
    fun tearDown() {
        SdkLogs.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.handlerExecutor = previousExecutor
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.System.putString(context.contentResolver, "firebase.test.lab", null)
    }

    private fun codes(): List<Pair<String, LogLevel>> = received.map { it.code to it.level }

    @Test
    fun `every code is in the table of the spec`() {
        val codes = LogCodes::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }
        assertTrue(codes.isNotEmpty())
        assertEquals(emptyList<String>(), codes - SPEC_CODES)
    }

    @Test
    fun `a failure is an error on a 4xx and a warning otherwise`() {
        assertEquals(LogLevel.ERROR, LogCodes.failureLevel(403))
        assertEquals(LogLevel.WARNING, LogCodes.failureLevel(503))
        assertEquals(LogLevel.WARNING, LogCodes.failureLevel(-1))
        assertEquals(LogLevel.WARNING, LogCodes.failureLevel(null))
    }

    @Test
    fun `a 4xx to init is a failed init, not a refused shop`() {
        val init = startInit()

        init.onError(403, """{"message":"blocked"}""")

        assertEquals(listOf(LogCodes.INIT_FAILED to LogLevel.ERROR), received.map { it.code to it.level })
        assertEquals(403, received.single().context["status"])
    }

    @Test
    fun `an answered init is logged as succeeded`() {
        val init = startInit()

        init.onSuccess(JSONObject("""{"did":"D","seance":"S"}"""))

        assertEquals(listOf(LogCodes.INIT_SUCCEEDED to LogLevel.INFO), received.map { it.code to it.level })
    }

    @Test
    fun `init is skipped at info on a Google Play pre-launch report device`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.System.putString(context.contentResolver, "firebase.test.lab", "true")
        val network = mockk<com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase>(relaxed = true)
        val settings = mockk<com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase>(relaxed = true)
        every { settings.getDid() } returns ""
        every { settings.getShopId() } returns "shop"
        val manager = RegisterManager(mockk(relaxed = true), settings, network, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

        manager.initialize(context, context.contentResolver, autoSendPushToken = false)

        assertEquals(listOf(LogCodes.INIT_SKIPPED to LogLevel.INFO), codes())
        io.mockk.verify(exactly = 0) { network.get(eq("init"), any(), any()) }
    }

    @Test
    fun `a notification-only FCM message is dropped at debug as not the SDK's`() {
        val message = mockk<RemoteMessage>(relaxed = true)
        every { message.data } returns emptyMap()
        every { message.notification } returns mockk(relaxed = true)

        MessagingService().onMessageReceived(message)

        assertEquals(listOf(LogCodes.PUSH_DROPPED to LogLevel.DEBUG), codes())
        assertEquals(LogReasons.NOT_SDK_PUSH, received.single().context["reason"])
    }

    @Test
    fun `a clicked notification without type or code is dropped at debug as not the SDK's`() {
        NotificationClickProcessor(NotificationDataExtractor(), NotificationDataSender())
            .processClick(Bundle().apply { putString("title", "another sender") }, { _, _ -> }, { _, _ -> })

        assertEquals(listOf(LogCodes.PUSH_DROPPED to LogLevel.DEBUG), codes())
        assertEquals(LogReasons.NOT_SDK_PUSH, received.single().context["reason"])
    }

    @Test
    fun `a push no shop takes is a warning that says why`() {
        Rees46.handlePush(mapOf("shop_id" to "nobody", "type" to "bulk"), PushEventType.RECEIVED)
        SdkRegistry.register("shop-a", SDK())
        SdkRegistry.register("shop-b", SDK())
        Rees46.handlePush(mapOf("type" to "bulk"), PushEventType.RECEIVED)
        SDK.onMessage(mapOf("shop_id" to "nobody"))
        SDK.onMessage(mapOf("type" to "bulk"))

        assertEquals(List(4) { LogCodes.PUSH_DROPPED to LogLevel.WARNING }, codes())
        assertEquals(
            listOf(LogReasons.UNKNOWN_SHOP, LogReasons.AMBIGUOUS_SHOP, LogReasons.UNKNOWN_SHOP, LogReasons.AMBIGUOUS_SHOP),
            received.map { it.context["reason"] }
        )
    }

    @Test
    fun `a data message is a push step until its shop takes it`() {
        SdkRegistry.register("shop", SDK())
        val message = mockk<RemoteMessage>(relaxed = true)
        every { message.data } returns mapOf("shop_id" to "shop", "type" to "bulk", "id" to "1")
        every { message.notification } returns null

        MessagingService().onMessageReceived(message)

        // The bare instance has not been initialized, so its delivery is not tracked: one more push step.
        assertEquals(
            listOf(LogCodes.PUSH to LogLevel.DEBUG, LogCodes.PUSH_RECEIVED to LogLevel.DEBUG, LogCodes.PUSH to LogLevel.WARNING),
            codes()
        )
        assertEquals("shop", received[1].shopId)
    }

    @Test
    fun `a push token from a provider is logged at debug without the token`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tokens = PushTokenManager(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        tokens.logShopId = "shop"
        tokens.initialize(context, autoSendPushToken = false)
        received.clear()

        tokens.onTokenReceived("secret-token", PushProvider.FCM)

        assertEquals(listOf(LogCodes.PUSH_TOKEN_RECEIVED to LogLevel.DEBUG), codes())
        assertEquals("fcm", received.single().context["provider"])
        assertTrue(received.none { "secret-token" in it.message || "secret-token" in it.context.toString() })
    }

    @Test
    fun `a popup the app keeps back is debug, one that cannot be shown a warning`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ForegroundActivity.install(context)
        val settings = mockk<com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase>(relaxed = true)
        every { settings.getShopId() } returns "shop"
        val manager = InAppNotificationManagerImpl(context, settings, Lazy { mockk(relaxed = true) })

        // A popup goes on the main thread and reads the settings there.
        manager.presentation.listener = { null }
        manager.shopPopUp(popup(1))
        shadowOf(Looper.getMainLooper()).idle()
        manager.presentation.listener = null
        manager.presentation.autoPresentation = false
        manager.shopPopUp(popup(2))
        shadowOf(Looper.getMainLooper()).idle()
        manager.presentation.autoPresentation = true
        manager.shopPopUp(popup(3)) // no activity on screen
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            listOf(
                LogCodes.POPUP_NOT_SHOWN to LogLevel.DEBUG,
                LogCodes.POPUP_NOT_SHOWN to LogLevel.DEBUG,
                LogCodes.POPUP_NOT_SHOWN to LogLevel.WARNING,
            ),
            codes()
        )
        assertEquals(
            listOf(LogReasons.APP_DECLINED, LogReasons.AUTO_PRESENTATION_OFF, "no_activity"),
            received.map { it.context["reason"] }
        )
    }

    private fun popup(id: Int) = PopupDto(
        id = id,
        channels = emptyList(),
        position = Position.CENTERED,
        delay = 0,
        html = "",
        components = Components(
            text = "Body",
            image = "",
            button = "",
            header = "Title",
            textEnabled = "true",
            imageEnabled = "false",
            headerEnabled = "true"
        ),
        webPushSystem = false,
        popupActions = PopupActions(link = null, close = CloseAction("Close"), pushSubscribe = null)
    )

    private fun startInit(): OnApiCallbackListener {
        val network = mockk<com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase>(relaxed = true)
        val settings = mockk<com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase>(relaxed = true)
        every { settings.getDid() } returns ""
        every { settings.getShopId() } returns "shop"
        val listener = slot<OnApiCallbackListener>()
        every { network.get(eq("init"), any(), capture(listener)) } just Runs
        val manager = RegisterManager(mockk(relaxed = true), settings, network, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)
        received.clear()
        return listener.captured
    }

    private companion object {
        /** The table "Коды" of the logging spec, with the area codes of the older code. */
        val SPEC_CODES = setOf(
            "request", "request_failed", "request_build_failed", "invalid_argument", "invalid_config",
            "decoding_failed", "callback_failed", "init_succeeded", "init_saved_answer", "init_retry",
            "init_failed", "init_refused", "device_id_reset", "storage_failed", "queue_dropped",
            "push_received", "push_dropped", "push_failed", "push_permission", "push_permission_failed",
            "push_token_sent", "push_token_skipped", "push_token_failed", "advertising_id_sent",
            "advertising_id_failed", "popup_not_shown", "core_flag_mismatch", "not_implemented",
            "request_cancelled", "init_skipped", "push_token_received", "device_id_kept",
            "sdk_log", "ui", "stories", "stories_v3", "popups_v3", "push",
        )
    }
}
