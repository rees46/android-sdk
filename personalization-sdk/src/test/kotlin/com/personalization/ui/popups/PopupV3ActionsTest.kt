package com.personalization.ui.popups

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.Fixtures
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.params.ProfileParams
import com.personalization.popups.PopupV3
import com.personalization.popups.PopupV3Specs
import com.personalization.popups.PopupV3Texts
import com.personalization.popups.PopupsV3Transport
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.widgets.StoriesPollsResult
import com.personalization.ui.widgets.StoriesPollsTransport
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Действия элементов: тела подписки, профиля, сегмента и отзыва, события, ссылки, буфер. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PopupV3ActionsTest {

    private val sdk = mockk<SDK>(relaxed = true)
    private val events = mutableListOf<Triple<Int, String, String?>>()
    private val links = mutableListOf<String>()
    private val reviews = mutableListOf<Pair<String, Map<String, Any>>>()
    private var reviewResult: StoriesPollsResult = StoriesPollsResult.Success()
    private var closed = 0
    private var navigated = 0
    private var handledProduct: PersonalizationProduct? = null
    private var productHandler: ((PersonalizationProduct) -> Boolean)? = { product -> handledProduct = product; product.id == "1" }
    private lateinit var handler: PopupV3ActionHandler
    private val texts = PopupV3Texts(sendFailed = "Не удалось отправить")
    private val app: Context = ApplicationProvider.getApplicationContext<Application>()

    private val transport = object : PopupsV3Transport {
        override fun list(testMode: Boolean, debug: Boolean, callback: (com.personalization.popups.PopupsV3ListResult) -> Unit) = Unit
        override fun one(id: Int, testMode: Boolean, callback: (PopupV3?) -> Unit) = Unit
        override fun event(id: Int, event: String, channel: String?) {
            events += Triple(id, event, channel)
        }
        override fun promoCode(listId: String, callback: (String?) -> Unit) = Unit
    }

    @Before
    fun setUp() {
        handler = PopupV3ActionHandler(
            sdk = sdk,
            popupId = 9106,
            transport = transport,
            appHost = { "app.rees46.ru" },
            linkHandler = { { url: String -> links += url; url.startsWith("rees46demo://") } },
            productHandler = { productHandler },
            texts = { texts },
            context = { app },
            onClose = { closed++ },
            onNavigate = { navigated++ }
        )
        handler.reviews = object : StoriesPollsTransport {
            override fun answerMiniPoll(code: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) = Unit
            override fun review(kind: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) {
                reviews += kind to body
                callback(reviewResult)
            }
        }
    }

    private fun element(fixture: String, id: String) =
        com.personalization.popups.PopupV3Design.parse(JSONObject(Fixtures.read("popups-v3/$fixture")).getJSONObject("design"))
            .elements.first { it.id == id }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `subscription goes with the consent flags, then the segment by did and both events`() {
        val spec = PopupV3Specs.email(element("element-email-collector.json", "el-email-inline"), texts)
        val flags = slot<HashMap<String, Boolean>>()
        val listener = slot<OnApiCallbackListener>()
        every { sdk.manageSubscription(any(), any(), capture(flags), capture(listener)) } answers {
            listener.captured.onSuccess(JSONObject())
        }
        var result: String? = "pending"
        handler.subscribe(spec, "demo+android@rees46.test") { result = it }
        idle()
        verify { sdk.manageSubscription("demo+android@rees46.test", null, any<HashMap<String, Boolean>>(), any()) }
        assertEquals(mapOf("email_bulk" to true, "email_chain" to true), flags.captured)
        verify { sdk.addToSegment("123", null, null) }
        assertEquals(listOf(Triple(9106, "subscribe", "email"), Triple(9106, "submit", null)), events)
        assertNull(result)
    }

    @Test
    fun `a failed subscription shows the server message and sends nothing`() {
        val spec = PopupV3Specs.email(element("element-email-collector.json", "el-email-bare"), texts)
        val listener = slot<OnApiCallbackListener>()
        every { sdk.manageSubscription(any(), any(), any<HashMap<String, Boolean>>(), capture(listener)) } answers {
            listener.captured.onError(400, """{"status":"error","message":"Invalid email"}""")
        }
        var result: String? = null
        handler.subscribe(spec, "x@y.z") { result = it }
        idle()
        assertEquals("Invalid email", result)
        verify(exactly = 0) { sdk.addToSegment(any(), any(), any(), any()) }
        assertTrue(events.isEmpty())

        every { sdk.manageSubscription(any(), any(), any<HashMap<String, Boolean>>(), capture(listener)) } answers {
            listener.captured.onError(-1, null)
        }
        handler.subscribe(spec, "x@y.z") { result = it }
        idle()
        assertEquals("Не удалось отправить", result)
    }

    @Test
    fun `a form saves the profile, then the consent flags, then the segment`() {
        val popup = JSONObject(Fixtures.read("popups-v3/element-form.json"))
        val spec = PopupV3Specs.form(element("element-form.json", "el-form"), texts)
        val example = popup.getJSONObject("_fixture").getJSONObject("expect").getJSONObject("el-form")
            .getJSONObject("submit").getJSONObject("profile_set_example")
        val values = example.keys().asSequence().associateWith { example.getString(it) }
        val profile = slot<ProfileParams>()
        val profileListener = slot<OnApiCallbackListener>()
        val subscriptionListener = slot<OnApiCallbackListener>()
        val flags = slot<HashMap<String, Boolean>>()
        every { sdk.profile(capture(profile), capture(profileListener)) } answers { profileListener.captured.onSuccess(JSONObject()) }
        every { sdk.manageSubscription(any(), any(), capture(flags), capture(subscriptionListener)) } answers {
            subscriptionListener.captured.onSuccess(JSONObject())
        }
        var result: String? = "pending"
        handler.saveProfile(spec, values, consent = true) { result = it }
        idle()
        assertEquals(example.toString(), profile.captured.toJson().toString())
        verify { sdk.manageSubscription("anna@example.com", "+79990000000", any<HashMap<String, Boolean>>(), any()) }
        assertEquals(mapOf("email_bulk" to true), flags.captured)
        verify { sdk.addToSegment("456", null, null) }
        assertEquals(listOf(Triple(9106, "submit", null as String?)), events)
        assertNull(result)
    }

    @Test
    fun `without the consent a form sets no flags`() {
        val spec = PopupV3Specs.form(element("element-form.json", "el-form"), texts)
        val listener = slot<OnApiCallbackListener>()
        every { sdk.profile(any(), capture(listener)) } answers { listener.captured.onSuccess(JSONObject()) }
        handler.saveProfile(spec, mapOf("email" to "a@b.c"), consent = false) { }
        idle()
        verify(exactly = 0) { sdk.manageSubscription(any(), any(), any<HashMap<String, Boolean>>(), any()) }
        verify { sdk.addToSegment("456", null, null) }
    }

    @Test
    fun `a survey review goes to its scale through polls v3, then submit`() {
        val nps = PopupV3Specs.survey(element("element-survey.json", "el-survey-nps"), texts)
        var result: String? = "pending"
        handler.review(nps, 0, "Долгая доставка") { result = it }
        assertEquals("nps", reviews.single().first)
        assertEquals(
            JSONObject(Fixtures.read("popups-v3/requests.json")).getJSONObject("review_nps").getJSONObject("json").let { json ->
                json.keys().asSequence().filter { it !in setOf("shop_id", "did", "sid") }.associateWith { json.get(it) }
            },
            reviews.single().second
        )
        assertNull(result)
        assertEquals(listOf(Triple(9106, "submit", null as String?)), events)

        reviewResult = StoriesPollsResult.Failure(404, "Channel not found")
        handler.review(nps, 9, null) { result = it }
        assertEquals("Channel not found", result)
        assertEquals(1, events.size)
    }

    @Test
    fun `links go to the host first, javascript never`() {
        handler.openLink("rees46demo://sale")
        handler.openLink("javascript:alert(1)")
        handler.openLink("  ")
        assertEquals(listOf("rees46demo://sale"), links)
        handler.openLink("https://demo.rees46.ru/")
        assertEquals("https://demo.rees46.ru/", shadowOf(app as Application).nextStartedActivity.dataString)
    }

    @Test
    fun `copy puts the text on the clipboard`() {
        handler.copy("SALE10")
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("SALE10", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun `a product click is a click, the host may open it and the popup leaves`() {
        handler.product(PersonalizationProduct(id = "1", name = "A", price = "1"))
        assertEquals(listOf(Triple(9106, "click", null as String?)), events)
        assertEquals(1, navigated)
        handler.product(PersonalizationProduct(id = "2", name = "B", price = "1", url = "rees46demo://p/2"))
        assertEquals(1, navigated)
        assertEquals("rees46demo://p/2", links.last())
        handler.close()
        assertEquals(1, closed)
    }

    @Test
    fun `a failing or missing product handler opens the deeplink, else the url, and the popup stays`() {
        productHandler = { throw IllegalStateException("host bug") }
        handler.product(PersonalizationProduct(id = "1", name = "A", price = "1", url = "https://demo.rees46.ru/p/1", deeplink = "rees46demo://p/1"))
        assertEquals("rees46demo://p/1", links.last())
        productHandler = null
        handler.product(PersonalizationProduct(id = "2", name = "B", price = "1", url = "rees46demo://p/2", deeplink = " "))
        assertEquals("rees46demo://p/2", links.last())
        assertEquals(listOf("click", "click"), events.map { it.second })
        assertEquals(0, navigated)
        assertEquals(0, closed)
    }
}
