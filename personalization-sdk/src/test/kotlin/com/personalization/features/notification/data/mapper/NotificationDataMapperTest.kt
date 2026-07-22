package com.personalization.features.notification.data.mapper

import com.google.firebase.messaging.RemoteMessage
import com.personalization.features.notification.actions.model.NotificationAction
import com.personalization.features.notification.event.model.NotificationEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the provider-agnostic push mapper [toNotificationData]. It is the single point both the
 * FCM and the HMS messaging services route a `data` payload through, so a regression here would
 * silently break notification display on one or both providers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationDataMapperTest {

    @Test
    fun `maps a full data payload including actions, urls and event`() {
        val data = mapOf(
            "id" to "123",
            "title" to "Hello",
            "body" to "World",
            "icon" to "https://cdn.example.com/icon.png",
            "type" to "bulk",
            "image" to "https://cdn.example.com/1.png,https://cdn.example.com/2.png",
            "actions" to """[{"action":"open","title":"Open"},{"action":"later","title":"Later"}]""",
            "action_urls" to """["https://example.com/a","https://example.com/b"]""",
            "event" to """{"type":"click","uri":"app://product/1","payload":{"product_id":"1"}}""",
        )

        val result = data.toNotificationData()

        assertEquals("123", result.id)
        assertEquals("Hello", result.title)
        assertEquals("World", result.body)
        assertEquals("https://cdn.example.com/icon.png", result.icon)
        assertEquals("bulk", result.type)
        assertEquals("https://cdn.example.com/1.png,https://cdn.example.com/2.png", result.image)
        assertEquals(
            listOf(
                NotificationAction(action = "open", title = "Open"),
                NotificationAction(action = "later", title = "Later"),
            ),
            result.actions,
        )
        assertEquals(listOf("https://example.com/a", "https://example.com/b"), result.actionUrls)
        assertEquals(
            NotificationEvent(
                type = "click",
                uri = "app://product/1",
                payload = mapOf("product_id" to "1"),
            ),
            result.event,
        )
    }

    @Test
    fun `maps a data-only push carrying just title and body (the HMS case)`() {
        val data = mapOf(
            "title" to "Order shipped",
            "body" to "Your order is on its way",
        )

        val result = data.toNotificationData()

        assertEquals("Order shipped", result.title)
        assertEquals("Your order is on its way", result.body)
        assertNull(result.id)
        assertNull(result.icon)
        assertNull(result.type)
        assertNull(result.image)
        // Absent optional collections resolve to empty, not null.
        assertEquals(emptyList<NotificationAction>(), result.actions)
        assertEquals(emptyList<String>(), result.actionUrls)
    }

    @Test
    fun `empty payload yields null scalars and empty defaults`() {
        val result = emptyMap<String, String>().toNotificationData()

        assertNull(result.id)
        assertNull(result.title)
        assertNull(result.body)
        assertNull(result.icon)
        assertNull(result.type)
        assertNull(result.image)
        assertEquals(emptyList<NotificationAction>(), result.actions)
        assertEquals(emptyList<String>(), result.actionUrls)
        assertEquals(
            NotificationEvent(type = "", uri = "", payload = emptyMap()),
            result.event,
        )
    }

    @Test
    fun `malformed actions, urls and event json degrade to empty instead of crashing`() {
        val data = mapOf(
            "title" to "t",
            "actions" to "not-json",
            "action_urls" to "also-not-json",
            "event" to "still-not-json",
        )

        val result = data.toNotificationData()

        assertEquals("t", result.title)
        assertEquals(emptyList<NotificationAction>(), result.actions)
        assertEquals(emptyList<String>(), result.actionUrls)
        assertEquals(
            NotificationEvent(type = "", uri = "", payload = emptyMap()),
            result.event,
        )
    }

    @Test
    fun `FCM RemoteMessage mapping delegates to the shared data mapper`() {
        val data = mapOf(
            "id" to "9",
            "title" to "Hi",
            "body" to "there",
            "type" to "chain",
        )
        val remoteMessage = mockk<RemoteMessage>()
        every { remoteMessage.data } returns data

        // Both providers must produce identical NotificationData from the same data map: FCM via
        // this extension, HMS via the map overload used by HmsMessagingService.
        assertEquals(data.toNotificationData(), remoteMessage.toNotificationData())
    }
}
