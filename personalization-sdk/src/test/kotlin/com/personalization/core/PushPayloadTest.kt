package com.personalization.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A push of FCM or HMS parsed as contract/notifications.md has it: every value a string, JSON in text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PushPayloadTest {

    @Test
    fun `a push of the SDK with its buttons, its target and its sound`() {
        val data = mapOf(
            "shop_id" to "shop-1",
            "id" to "m-1",
            "type" to "chain",
            "title" to "Title",
            "body" to "Body",
            "icon" to "https://cdn/icon.png",
            "image" to "https://cdn/1.png,https://cdn/2.png",
            "actions" to """[{"action":"buy","title":"Buy"},{"action":"later","title":"Later"}]""",
            "action_urls" to """["https://shop/buy"]""",
            "event" to """{"type":"product","uri":"37","payload":{"k":"v"}}""",
            "source" to "mailing",
            "android_sound_file" to "chime",
        )

        val payload = PushPayload.parse(data) ?: throw AssertionError("not parsed")

        assertEquals("shop-1", payload.shopId)
        assertEquals("m-1", payload.code)
        assertEquals("chain", payload.type)
        assertEquals(listOf("buy" to "https://shop/buy", "later" to null), payload.actions.map { it.id to it.url })
        assertEquals(PushTargetType.PRODUCT, payload.target?.type)
        assertEquals("37", payload.target?.uri)
        assertEquals(mapOf<String, Any?>("k" to "v"), payload.target?.payload)
        assertEquals("mailing", payload.source)
        assertEquals("chime", payload.sound)
        assertEquals(data, payload.data)
        // Its content stays out of logs.
        assertTrue(!payload.toString().contains("Title"))
    }

    @Test
    fun `an unknown target type comes as it is, broken JSON leaves its field empty, and a tap's extras are read`() {
        val payload = PushPayload.parse(mapOf(
            "NOTIFICATION_ID" to "m-2",
            "NOTIFICATION_TYPE" to "bulk",
            "actions" to "not json",
            "event" to """{"type":"story","uri":"7"}""",
        )) ?: throw AssertionError("not parsed")

        assertEquals("m-2", payload.code)
        assertNull(payload.shopId)
        assertTrue(payload.actions.isEmpty())
        assertEquals("story", payload.target?.type?.value)
    }

    @Test
    fun `without an id or a type it is not a push of the SDK`() {
        assertNull(PushPayload.parse(mapOf("type" to "bulk")))
        assertNull(PushPayload.parse(mapOf("id" to "m-3")))
        assertNull(PushPayload.parse(mapOf("id" to " ", "type" to "bulk")))
    }
}
