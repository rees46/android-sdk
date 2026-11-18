package com.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Rees46Config.appDomain`, the host of API v3, and what apps built against 2.52, the release before it,
 * still find: its constructor in the form Kotlin compiles a call with defaults, its `copy` and its
 * components.
 */
class Rees46ConfigAppDomainTest {

    /** The parameters of the 2.52 constructor and `copy`. */
    private val parameters = listOf(
        String::class.java, String::class.java, String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType, String::class.java, Boolean::class.javaPrimitiveType, LogLevel::class.java,
        String::class.java, String::class.java, Boolean::class.javaPrimitiveType,
    )

    private val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")

    /** The mask of a call that passes only the parameters at [given]: every other one is left to its default. */
    private fun leaving(vararg given: Int): Int = given.fold((1 shl parameters.size) - 1) { mask, index -> mask and (1 shl index).inv() }

    @Test
    fun `the host of API v3 is app rees46 ru unless the config says otherwise`() {
        assertEquals("app.rees46.ru", Rees46Config("shop").appDomain)
        assertEquals("app.cluster.example", Rees46Config("shop", appDomain = "app.cluster.example").appDomain)
        assertFalse(Rees46Config("shop") == Rees46Config("shop", appDomain = "app.cluster.example"))
        assertEquals("app.cluster.example", Rees46Config("shop", appDomain = "app.cluster.example").copy(shopId = "other").appDomain)
    }

    @Test
    fun `the 2 52 constructor is still there in its plain form, and takes the default host`() {
        val plain = Rees46Config::class.java.getConstructor(*parameters.toTypedArray())
        assertFalse(plain.isSynthetic)

        val made = plain.newInstance("shop", "api.example", "flutter", false, true, false, "TAG", false, LogLevel.DEBUG, "key", "did-1", true)

        assertEquals(
            Rees46Config("shop", "api.example", "flutter", false, true, false, "TAG", false, LogLevel.DEBUG, "key", "did-1", true),
            made,
        )
        assertEquals("app.rees46.ru", made.appDomain)
    }

    @Test
    fun `the 2 52 constructor is still there in the form of a call with defaults`() {
        val withDefaults = Rees46Config::class.java.getDeclaredConstructor(*(parameters + Int::class.javaPrimitiveType + marker).toTypedArray())
        assertTrue(withDefaults.isSynthetic)

        // Rees46Config("shop") as 2.52 compiled it: every parameter but the shop left to its default.
        val shopOnly = withDefaults.newInstance("shop", null, null, false, false, false, null, false, null, null, null, false, leaving(0), null)
        assertEquals(Rees46Config("shop"), shopOnly)

        // Rees46Config("shop", stream = "flutter", logLevel = DEBUG, deviceId = "did-1", sendAdvertisingId = true).
        val some = withDefaults.newInstance("shop", null, "flutter", false, false, false, null, false, LogLevel.DEBUG, null, "did-1", true, leaving(0, 2, 8, 10, 11), null)
        assertEquals(Rees46Config("shop", stream = "flutter", logLevel = LogLevel.DEBUG, deviceId = "did-1", sendAdvertisingId = true), some)

        // A Boolean passed as false where its default is true stays false.
        val off = withDefaults.newInstance("shop", null, null, false, false, false, null, false, null, null, null, false, leaving(0, 3, 5, 7), null)
        assertEquals(Rees46Config("shop", autoSendPushToken = false, addTrailingSlash = false, enableAutoPopupPresentation = false), off)
    }

    @Test
    fun `the 2 52 copy is still there in both forms, and keeps the host`() {
        val config = Rees46Config("shop", appDomain = "app.cluster.example")

        val copy = Rees46Config::class.java.getMethod("copy", *parameters.toTypedArray())
        assertTrue(copy.isSynthetic)
        val copied = copy.invoke(config, "other", "api.rees46.ru", "android", true, false, true, "SDK", true, LogLevel.DEBUG, null, null, false)
        assertEquals(Rees46Config("other", logLevel = LogLevel.DEBUG, appDomain = "app.cluster.example"), copied)

        // config.copy(shopId = "other") as 2.52 compiled it.
        val withDefaults = Rees46Config::class.java.getDeclaredMethod(
            "copy\$default",
            *(listOf(Rees46Config::class.java) + parameters + Int::class.javaPrimitiveType + Any::class.java).toTypedArray(),
        )
        val copiedWithDefaults = withDefaults.invoke(null, config, "other", null, null, false, false, false, null, false, null, null, null, false, leaving(0), null)
        assertEquals(config.copy(shopId = "other"), copiedWithDefaults)
    }

    @Test
    fun `the components of 2 52 keep their places and the host comes after them`() {
        val config = Rees46Config("shop", sendAdvertisingId = true, appDomain = "app.cluster.example")

        assertEquals(true, Rees46Config::class.java.getMethod("component12").invoke(config))
        assertEquals("app.cluster.example", Rees46Config::class.java.getMethod("component13").invoke(config))
    }
}
