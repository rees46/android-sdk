package com.personalization

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BadParcelableException
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.messaging.RemoteMessage
import com.personalization.core.PushCallbacks
import com.personalization.core.PushPayload
import com.personalization.core.PushTargetType
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.PushHub
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.features.notification.actions.model.NotificationAction
import com.personalization.features.notification.event.model.NotificationEvent
import com.personalization.features.notification.presentation.helpers.NotificationHelper
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.push.PushDismissReceiver
import com.personalization.push.PushExtras
import com.personalization.sdk.data.models.dto.notification.NotificationData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * A notification the SDK draws for a push it did not see come in (`contract/notifications.md`): on the new
 * core the push is rebuilt from what the notification shows, for the shop of the helper's instance, and
 * reported whole as opened, its dismissal intent carrying the same push. A notification without a code or
 * a type, or one drawn before the process chose its core, reports nothing. And the ways a dismissal or a
 * message reaches the SDK broken: none of them throws at the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationHelperUnseenPushTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val seen = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val defaultWork = PushDismissReceiver.work

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
        PushHub.reset()
        PushHub.main = Executor { it.run() }
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.handler = SdkLogHandler { logged += it }
        context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        listOf(PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8())
                else JSONObject().apply { url.queryParameterNames.forEach { put(it, url.queryParameter(it)) } }
                seen += path to body
                if (path == "init") return MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                return MockResponse().setBody("{}")
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        SdkRegistry.all().forEach { it.release() }
        server.shutdown()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
        QueueStore.reset()
        PushHub.reset()
        PushDismissReceiver.work = defaultWork
    }

    private fun sdk(): SDK {
        val sdk = Rees46.initialize(
            context,
            Rees46Config(
                shopId = SHOP,
                apiDomain = server.url("/").toString().trimEnd('/'),
                autoSendPushToken = false,
                logLevel = LogLevel.DEBUG,
            ),
            coreV2 = true,
        )
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    private fun flush(sdk: SDK) = runBlocking { withTimeout(5_000) { sdk.flush() } }

    private fun notifications() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    private fun data(id: String?, type: String?, title: String = "Sale") = NotificationData(
        id = id,
        title = title,
        body = "Today only",
        icon = null,
        type = type,
        actions = listOf(NotificationAction(action = "buy", title = "Buy")),
        actionUrls = listOf("https://example.com/buy"),
        image = null,
        event = NotificationEvent(type = "web", uri = "https://example.com/p", payload = mapOf("k" to 1)),
    )

    @Test
    fun `a notification of a push the SDK did not see is reported as opened for the helper's shop, whole, and its dismissal carries the same push`() {
        val sdk = sdk()
        val opened = CopyOnWriteArrayList<PushPayload>()
        Rees46.setPushCallbacks(PushCallbacks(onOpened = { opened += it }))
        // A new process: the push came through the app's own service, not the SDK's.
        PushHub.forgetPushes()

        sdk.notificationHelper.createNotification(context, data(id = "m", type = "bulk"), images = null)
        flush(sdk)

        val shown = seen.single { it.first == "track/opened" }.second
        assertEquals("m", shown.getString("code"))
        assertEquals("bulk", shown.getString("type"))
        assertEquals(SHOP, shown.getString("shop_id"))
        assertEquals("D1", shown.getString("did"))
        val payload = opened.single()
        assertEquals(SHOP, payload.shopId)
        assertEquals("Sale", payload.title)
        assertEquals("Today only", payload.body)
        assertEquals("buy", payload.actions.single().id)
        assertEquals("Buy", payload.actions.single().title)
        assertEquals("https://example.com/buy", payload.actions.single().url)
        assertEquals(PushTargetType.WEB, payload.target?.type)
        assertEquals("https://example.com/p", payload.target?.uri)
        assertEquals(1, payload.target?.payload?.get("k"))
        val dismissal = notifications().single().deleteIntent ?: throw AssertionError("no dismissal intent")
        assertEquals(payload.data, PushExtras.read(shadowOf(dismissal).savedIntent))
    }

    @Test
    fun `a notification without a code or a type is not a push of the SDK and reports nothing`() {
        val sdk = sdk()
        PushHub.forgetPushes()

        sdk.notificationHelper.createNotification(context, data(id = null, type = "bulk", title = "No code"), images = null)
        sdk.notificationHelper.createNotification(context, data(id = "m", type = " ", title = "No type"), images = null)
        flush(sdk)

        assertEquals(2, notifications().size)
        assertTrue(notifications().all { it.deleteIntent == null })
        assertFalse(seen.any { it.first.startsWith("track/") })
    }

    @Test
    fun `a notification drawn before the process chose its core reports nothing and sets no dismissal`() {
        assertNull(CoreSelection.decided())
        val manager = context.getSystemService(NotificationManager::class.java)

        NotificationHelper(manager).createNotification(context, data(id = "m", type = "bulk"), images = null)

        assertNull(notifications().single().deleteIntent)
        assertFalse(logged.any { it.code == LogCodes.PUSH_RECEIVED || it.code == LogCodes.PUSH_DROPPED })
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `a dismissal the background cannot take is logged and the broadcast ends`() {
        PushDismissReceiver.work = Executor { throw RejectedExecutionException("no thread left") }
        val intent = Intent(context, PushDismissReceiver::class.java)
        PushExtras.put(intent, mapOf("id" to "m", "type" to "bulk", "shop_id" to SHOP))

        PushDismissReceiver().onReceive(context, intent)

        val failed = logged.single { it.code == LogCodes.PUSH_FAILED }
        assertEquals(LogLevel.ERROR, failed.level)
        assertEquals(SHOP, failed.shopId)
    }

    @Test
    fun `a broadcast without a push of the SDK is left alone`() {
        val handed = CopyOnWriteArrayList<Runnable>()
        PushDismissReceiver.work = Executor { handed += it }

        PushDismissReceiver().onReceive(context, Intent(context, PushDismissReceiver::class.java))
        PushDismissReceiver().onReceive(context, Intent().putExtra(PushExtras.EXTRA, "not a bundle"))

        assertTrue(handed.isEmpty())
        assertTrue(logged.none { it.level == LogLevel.ERROR })
    }

    @Test
    fun `an intent carries the push only as a bundle of text, and one that does not unparcel carries none`() {
        assertNull(PushExtras.read(null))
        assertNull(PushExtras.read(Intent()))
        assertNull("an empty push", PushExtras.read(Intent().putExtra(PushExtras.EXTRA, Bundle())))
        val mixed = Bundle().apply {
            putString("id", "m")
            putInt("count", 2)
        }
        assertEquals("only the text", mapOf("id" to "m"), PushExtras.read(Intent().putExtra(PushExtras.EXTRA, mixed)))
        val broken = mockk<Intent> { every { getBundleExtra(PushExtras.EXTRA) } throws BadParcelableException("ClassNotFoundException when unmarshalling") }
        assertNull(PushExtras.read(broken))
    }

    @Test
    fun `an FCM message that fails to be read is logged, never thrown at FCM's thread`() {
        val message = mockk<RemoteMessage> { every { data } throws IllegalStateException("a broken message") }

        MessagingService().onMessageReceived(message)

        assertEquals(LogLevel.ERROR, logged.single { it.code == LogCodes.PUSH_FAILED }.level)
    }

    private companion object {
        const val SHOP = "unseen-push-shop"
    }
}
