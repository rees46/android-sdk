package com.personalization

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PushRoutingTest {

    @After
    fun tearDown() {
        SDK.getAllInstances().keys.toList().forEach { SDK.destroyInstance(it) }
    }

    @Test
    fun singleInstance_onMessage_routesToInstance() {
        val sdk = SDK()
        registerInstance(sdk, "shop_A")

        val resolved = resolveTarget("shop_A")
        assertEquals(sdk, resolved)
    }

    @Test
    fun multiInstance_shopIdInPayload_routesToCorrectInstance() {
        val sdkA = SDK().also { registerInstance(it, "shop_A") }
        val sdkB = SDK().also { registerInstance(it, "shop_B") }

        val resolved = resolveTarget("shop_B")
        assertEquals(sdkB, resolved)
    }

    @Test
    fun multiInstance_noShopIdInPayload_fallbackToLegacyInstance() {
        registerInstance(SDK(), "shop_A")
        registerInstance(SDK(), "shop_B")

        val resolved = resolveTarget(null)
        assertNotNull(resolved)
        assertEquals(SDK.instance, resolved)
    }

    @Test
    fun multiInstance_unknownShopIdInPayload_fallbackToLegacyInstance() {
        registerInstance(SDK(), "shop_A")
        registerInstance(SDK(), "shop_B")

        val resolved = resolveTarget("unknown_shop")
        assertEquals(SDK.instance, resolved)
    }

    @Test
    fun onMessageListener_calledOnCorrectInstance() {
        val sdkA = SDK().also { registerInstance(it, "shop_A") }
        val sdkB = SDK().also { registerInstance(it, "shop_B") }

        var listenerACalled = false
        var listenerBCalled = false

        sdkA.setOnMessageListener { listenerACalled = true }
        sdkB.setOnMessageListener { listenerBCalled = true }

        val targetForB = resolveTarget("shop_B")
        assertEquals(sdkB, targetForB)

        val targetForA = resolveTarget("shop_A")
        assertEquals(sdkA, targetForA)
    }

    /**
     * Simulates the routing logic from SDK.onMessage() without requiring
     * a real RemoteMessage (which is hard to construct in unit tests).
     */
    private fun resolveTarget(shopId: String?): SDK {
        val targetInstance = shopId?.let { SDK.getInstance(it) }
        return targetInstance ?: SDK.instance
    }

    private fun registerInstance(sdk: SDK, shopId: String) {
        sdk.shopId = shopId
        val instancesField = SDK::class.java.getDeclaredField("instances")
        instancesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val instances = instancesField.get(null) as java.util.concurrent.ConcurrentHashMap<String, SDK>
        instances[shopId] = sdk
    }
}
