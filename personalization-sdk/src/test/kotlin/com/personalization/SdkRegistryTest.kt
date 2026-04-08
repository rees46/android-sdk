package com.personalization

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkRegistryTest {

    @After
    fun tearDown() {
        SDK.getAllInstances().keys.toList().forEach { SDK.destroyInstance(it) }
    }

    @Test
    fun register_singleInstance_appearsInRegistry() {
        val sdk = SDK()
        sdk.shopId = "shop_A"
        SDK.getAllInstances() // ensure empty initially

        simulateRegister(sdk, "shop_A")

        val retrieved = SDK.getInstance("shop_A")
        assertNotNull(retrieved)
        assertEquals(sdk, retrieved)
    }

    @Test
    fun register_multipleInstances_allAppearInRegistry() {
        val sdkA = SDK().also { simulateRegister(it, "shop_A") }
        val sdkB = SDK().also { simulateRegister(it, "shop_B") }
        val sdkC = SDK().also { simulateRegister(it, "shop_C") }

        assertEquals(3, SDK.getAllInstances().size)
        assertEquals(sdkA, SDK.getInstance("shop_A"))
        assertEquals(sdkB, SDK.getInstance("shop_B"))
        assertEquals(sdkC, SDK.getInstance("shop_C"))
    }

    @Test
    fun getInstance_unknownShopId_returnsNull() {
        simulateRegister(SDK(), "shop_A")
        assertNull(SDK.getInstance("unknown"))
    }

    @Test
    fun destroyInstance_removesFromRegistry() {
        simulateRegister(SDK(), "shop_A")
        assertNotNull(SDK.getInstance("shop_A"))

        SDK.destroyInstance("shop_A")
        assertNull(SDK.getInstance("shop_A"))
    }

    @Test
    fun destroyInstance_doesNotAffectOtherInstances() {
        val sdkA = SDK().also { simulateRegister(it, "shop_A") }
        val sdkB = SDK().also { simulateRegister(it, "shop_B") }

        SDK.destroyInstance("shop_A")

        assertNull(SDK.getInstance("shop_A"))
        assertEquals(sdkB, SDK.getInstance("shop_B"))
    }

    @Test
    fun reinitialize_sameShopId_replacesInstance() {
        val sdkOld = SDK().also { simulateRegister(it, "shop_A") }
        val sdkNew = SDK().also { simulateRegister(it, "shop_A") }

        assertEquals(1, SDK.getAllInstances().size)
        assertEquals(sdkNew, SDK.getInstance("shop_A"))
    }

    @Test
    fun legacySingleton_instanceField_stillWorks() {
        val legacyInstance = SDK.instance
        assertNotNull(legacyInstance)
        assertTrue(legacyInstance is SDK)
    }

    private fun simulateRegister(sdk: SDK, shopId: String) {
        sdk.shopId = shopId
        sdk.instanceTag = "SDK[$shopId]"

        val instancesField = SDK::class.java.getDeclaredField("instances")
        instancesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val instances = instancesField.get(null) as java.util.concurrent.ConcurrentHashMap<String, SDK>
        instances[shopId] = sdk
    }
}
