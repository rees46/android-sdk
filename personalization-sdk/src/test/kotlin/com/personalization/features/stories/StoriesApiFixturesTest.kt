package com.personalization.features.stories

import androidx.test.core.app.ApplicationProvider
import com.personalization.api.models.stories.StoriesBlock
import com.personalization.api.models.stories.StoryButtonElement
import com.personalization.api.models.stories.StoryElement
import com.personalization.api.models.stories.StoryProduct
import com.personalization.api.models.stories.StoryProductElement
import com.personalization.api.models.stories.StoryProductsElement
import com.personalization.api.models.stories.StorySlideType
import com.personalization.api.models.stories.StoryTextBlockElement
import com.personalization.core.BridgeApi
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.internal.NamespaceFixtures
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** `stories.get` against the contract's fixtures (`contract/0/stories.json`): the request, the refused arguments, how each answer reads. */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesApiFixturesTest {

    private val fixtures = NamespaceFixtures(SHOP)
    private lateinit var stories: StoriesApiImpl

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        check(method == "get") { "unknown method $method" }
        stories.get(args.getString("code"))
    }

    @Before
    fun setUp() {
        fixtures.start(ApplicationProvider.getApplicationContext())
        stories = StoriesApiImpl(fixtures.calls)
    }

    @After
    fun tearDown() = fixtures.stop()

    @Test
    fun `get makes the contract's request`() {
        assertEquals(1, fixtures.checkRequests(FILE, invoke, NAMESPACE))
    }

    @Test
    fun `an empty code is invalidArgument without a request`() {
        assertEquals(1, fixtures.checkErrors(FILE, invoke, NAMESPACE))
    }

    @Test
    fun `the code is encoded as one segment of the path`() {
        fixtures.server.enqueue(MockResponse().setBody("{}"))

        runBlocking { withTimeout(10_000) { stories.get("main stories/1?") } }

        assertEquals("/stories/main%20stories%2F1%3F", fixtures.server.takeRequest(5, TimeUnit.SECONDS)?.requestUrl?.encodedPath)
    }

    @Test
    fun `a code of spaces is invalidArgument without a request`() {
        val error = try {
            runBlocking { stories.get("  ") }
            null
        } catch (e: SdkException) {
            e.error
        }

        assertTrue("$error", error is SdkError.InvalidArgument)
        assertNull(fixtures.server.takeRequest(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `get reads every answer of the contract into its block or its error`() {
        assertTrue(fixtures.checkAnswers(FILE, invoke, NAMESPACE, NamespaceFixtures.Json { block(it as StoriesBlock) }) >= 9)
    }

    @Test
    fun `an opacity is percent as a string or above 1, a fraction otherwise, and never outside 0 to 1`() {
        val cases = listOf<Pair<Any?, Double>>(
            "40%" to 0.4, "40" to 0.4, " 75 % " to 0.75, 0.5 to 0.5, 50 to 0.5, 1 to 1.0, "150%" to 1.0,
            "-5" to 0.0, 0 to 0.0, "half" to 0.0, null to 0.0, true to 0.0,
        )

        cases.forEach { (value, expected) -> assertEquals("$value", expected, StoriesApiImpl.opacity(value), 1e-9) }
    }

    @Test
    fun `an answer without a body is decoding`() {
        assertEquals(null, StoriesApiImpl.readBlock(null))
    }

    /** The block in the contract's names; an element carries its kind as `type`, as in TypeScript. */
    private fun block(block: StoriesBlock): Map<String, Any?> = mapOf(
        "id" to block.id,
        "stories" to block.stories.map { story ->
            mapOf(
                "id" to story.id, "name" to story.name, "avatar" to story.avatar, "viewed" to story.viewed,
                "pinned" to story.pinned, "startPosition" to story.startPosition,
                "slides" to story.slides.map { slide ->
                    mapOf(
                        "id" to slide.id, "type" to slide.type.wire(), "background" to slide.background,
                        "backgroundColor" to slide.backgroundColor, "preview" to slide.preview, "duration" to slide.duration,
                        "elements" to slide.elements.map(::element),
                    )
                },
            )
        },
    )

    private fun StorySlideType.wire(): String = when (this) {
        StorySlideType.IMAGE -> "image"
        StorySlideType.VIDEO -> "video"
        StorySlideType.PRODUCT -> "product"
    }

    private fun element(element: StoryElement): Map<String, Any?> = when (element) {
        is StoryButtonElement -> mapOf(
            "type" to "button", "title" to element.title, "link" to element.link, "linkAndroid" to element.linkAndroid,
            "linkIos" to element.linkIos, "background" to element.background, "color" to element.color,
            "textBold" to element.textBold,
        )
        is StoryProductElement -> mapOf("type" to "product", "title" to element.title, "item" to element.item?.let(::product))
        is StoryProductsElement -> mapOf(
            "type" to "products", "showLabel" to element.showLabel, "hideLabel" to element.hideLabel,
            "products" to element.products.map(::product),
        )
        is StoryTextBlockElement -> mapOf(
            "type" to "textBlock", "text" to element.text, "fontSize" to element.fontSize, "fontType" to element.fontType,
            "bold" to element.bold, "italic" to element.italic, "textAlign" to element.textAlign,
            "textColor" to element.textColor, "backgroundColor" to element.backgroundColor,
            "backgroundOpacity" to element.backgroundOpacity, "lineSpacing" to element.lineSpacing,
            "yOffset" to element.yOffset, "promocode" to element.promocode,
        )
    }

    private fun product(product: StoryProduct): Map<String, Any?> = mapOf(
        "id" to product.id, "name" to product.name, "brand" to product.brand, "picture" to product.picture,
        "imageUrl" to product.imageUrl, "price" to product.price, "priceFormatted" to product.priceFormatted,
        "oldPrice" to product.oldPrice, "oldPriceFormatted" to product.oldPriceFormatted,
        "discountPercent" to product.discountPercent, "promocode" to product.promocode,
        "priceWithPromocode" to product.priceWithPromocode, "priceWithPromocodeFormatted" to product.priceWithPromocodeFormatted,
        "currency" to product.currency, "url" to product.url, "deeplinkAndroid" to product.deeplinkAndroid,
        "deeplinkIos" to product.deeplinkIos,
    )

    private companion object {
        const val SHOP = "stories-shop"
        const val FILE = "stories"
        const val NAMESPACE = "stories"
    }
}
