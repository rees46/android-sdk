package com.personalization.demo

import android.Manifest
import android.os.Build
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personalization.AmbiguousShopException
import com.personalization.PushEventType
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.UnknownShopIdException
import com.personalization.demo.httplogger.HttpLogEntry
import com.personalization.demo.httplogger.HttpLogStore
import com.personalization.sdk.data.models.dto.notification.NotificationData
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device E2E for the multi-instance feature against the live REES46 API. [DemoApplication]
 * initializes shop A (default) eagerly and registers shop B lazily; this test resolves both, so two
 * shops are live in one process and their isolation / routing / fail-fast contracts run against real
 * backend sessions. Mirrors the other `*IntegrationTest` classes (real init on the emulator).
 *
 * State is process-global (Rees46 / SdkRegistry singletons persist across tests in one run), so every
 * assertion here is order-independent: each test makes both shops live itself and asserts a property
 * that holds regardless of what ran before.
 */
@RunWith(AndroidJUnit4::class)
class MultiInstanceE2ETest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val shopA = BuildConfig.SHOP_ID
    private val shopB = BuildConfig.SHOP_ID_2

    companion object {
        /**
         * Grant POST_NOTIFICATIONS before the first activity launch so the system permission dialog
         * never pauses the activity (which would make Espresso's [multiInstanceTab_opensWithoutCrashing]
         * fail with NoActivityResumedException). Runs once, before any ActivityScenarioRule launch.
         */
        @JvmStatic
        @BeforeClass
        fun grantNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                    "com.personalization.demo",
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    @Test
    fun bothShops_resolveToDistinctInstances() {
        val a = Rees46.getInstance(shopA)
        val b = Rees46.getInstance(shopB) // materializes the lazily-registered shop B
        assertNotNull(a)
        assertNotNull(b)
        assertNotSame("Each shop must have its own SDK instance", a, b)
    }

    @Test
    fun twoShops_haveIsolatedServerSessions() {
        val (didA, sidA, didB, sidB) = awaitSessions()
        assertTrue("shop A session not ready (did/sid empty)", didA.isNotEmpty() && sidA.isNotEmpty())
        assertTrue("shop B session not ready (did/sid empty)", didB.isNotEmpty() && sidB.isNotEmpty())
        assertNotEquals("did must be assigned per shop", didA, didB)
        assertNotEquals("sid must be assigned per shop", sidA, sidB)
    }

    @Test
    fun defaultGetInstance_isAmbiguousWithTwoShopsLive() {
        Rees46.getInstance(shopB) // make both live
        assertThrowsOf<AmbiguousShopException> { Rees46.getInstance() }
    }

    @Test
    fun getInstance_forUnknownShop_throwsUnknownShopId() {
        assertThrowsOf<UnknownShopIdException> { Rees46.getInstance("nope-not-a-shop") }
    }

    @Test
    fun handlePush_routesToTheNamedShopOnly() {
        val a = Rees46.getInstance(shopA)
        val b = Rees46.getInstance(shopB)
        val toA = mutableListOf<NotificationData>()
        val toB = mutableListOf<NotificationData>()
        a.setOnMessageListener { toA.add(it) }
        b.setOnMessageListener { toB.add(it) }

        Rees46.handlePush(
            mapOf("shop_id" to shopB, "type" to "bulk", "id" to "e2e", "title" to "B"),
            PushEventType.RECEIVED
        )

        assertTrue("push for shop B must reach shop B", toB.size == 1)
        assertTrue("push for shop B must NOT reach shop A", toA.isEmpty())
    }

    @Test
    fun handlePush_withUnknownShop_isDropped() {
        val a = Rees46.getInstance(shopA)
        val b = Rees46.getInstance(shopB)
        val toA = mutableListOf<NotificationData>()
        val toB = mutableListOf<NotificationData>()
        a.setOnMessageListener { toA.add(it) }
        b.setOnMessageListener { toB.add(it) }

        Rees46.handlePush(
            mapOf("shop_id" to "unknown-shop", "type" to "bulk", "id" to "e2e"),
            PushEventType.RECEIVED
        )

        assertTrue("a push for an unknown shop must be dropped", toA.isEmpty() && toB.isEmpty())
    }

    @Test
    fun handlePush_clicked_tracksTheClickOnTheNamedShop() {
        Rees46.getInstance(shopB) // ensure B is live and its session is ready
        HttpLogStore.clear()

        // CLICKED goes through notificationClicked, whose extractor reads NOTIFICATION_TYPE/
        // NOTIFICATION_ID (the keys the host puts on the tap intent), while shop_id drives routing.
        Rees46.handlePush(
            mapOf(
                "shop_id" to shopB,
                "NOTIFICATION_TYPE" to "bulk",
                "NOTIFICATION_ID" to "e2e-click"
            ),
            PushEventType.CLICKED
        )

        // track/clicked is POSTed on shop B's network stack (shop_id carried in the body); poll the
        // captured traffic for it.
        val tracked = awaitLog { entry ->
            entry.url.contains("track/clicked") &&
                (entry.url + entry.requestBody.orEmpty()).contains(shopB)
        }
        assertTrue("expected a track/clicked carrying shop B in the HTTP log", tracked)
    }

    @Test
    fun handlePush_materializesALazilyRegisteredShop() {
        // The cold-process case that broke the second shop: FCM/HMS start the app just to deliver a
        // data-only push, so only eagerly-initialized shops are live and a lazily-registered shop is
        // still pending. A push for it must materialize it and be delivered, not dropped. This shopId is
        // used by no other test, so on a fresh process it is genuinely pending here.
        val lazyShop = "mi-materialize-on-push"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Rees46.registerShops(
            context = context,
            configs = listOf(Rees46Config(shopId = lazyShop, autoSendPushToken = false)),
            eagerInit = false
        )

        // R1's process-global listener is the only one that can catch a push for a shop that does not
        // exist yet at subscribe time — exactly what materialize-on-push produces.
        val received = mutableListOf<Pair<String, NotificationData>>()
        Rees46.setOnMessageListener { shopId, data -> received.add(shopId to data) }

        Rees46.handlePush(
            mapOf("shop_id" to lazyShop, "type" to "bulk", "id" to "e2e", "title" to "lazy"),
            PushEventType.RECEIVED
        )

        assertTrue("the push must initialize the pending shop", Rees46.isInitialized(lazyShop))
        assertTrue(
            "the materialized shop's push must reach the global listener",
            received.any { it.first == lazyShop }
        )
    }

    @Test
    fun multiInstanceTab_opensWithoutCrashing() {
        onView(withId(R.id.tabMultiInstance)).perform(click())
        Thread.sleep(3000)
    }

    // --- helpers ---

    private data class Sessions(val didA: String, val sidA: String, val didB: String, val sidB: String)

    /** Both shops live, then poll until the server has assigned each a did/sid (async /init). */
    private fun awaitSessions(timeoutMs: Long = 10_000): Sessions {
        val a = Rees46.getInstance(shopA)
        val b = Rees46.getInstance(shopB)
        val deadline = System.currentTimeMillis() + timeoutMs
        var s = read(a, b)
        while (System.currentTimeMillis() < deadline &&
            (s.didA.isEmpty() || s.didB.isEmpty() || s.sidA.isEmpty() || s.sidB.isEmpty())
        ) {
            Thread.sleep(300)
            s = read(a, b)
        }
        return s
    }

    private fun read(a: SDK, b: SDK) =
        Sessions(a.getDid().orEmpty(), a.getSid(), b.getDid().orEmpty(), b.getSid())

    /** Polls the captured SDK HTTP traffic until [predicate] matches an entry, or the timeout. */
    private fun awaitLog(timeoutMs: Long = 8000, predicate: (HttpLogEntry) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (HttpLogStore.snapshot().any(predicate)) return true
            Thread.sleep(300)
        }
        return false
    }

    private inline fun <reified T : Throwable> assertThrowsOf(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName} but nothing was thrown")
        } catch (throwable: Throwable) {
            assertTrue(
                "Expected ${T::class.simpleName} but got ${throwable::class.simpleName}: ${throwable.message}",
                throwable is T
            )
        }
    }
}
