package com.personalization.features.segments

import com.personalization.core.BridgeApi
import com.personalization.core.internal.NamespaceFixtures
import com.personalization.core.internal.argString
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** `sdk.segments` against the contract's fixtures (`contract/0/segments.json`), and its A/B group. */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SegmentsApiFixturesTest {

    private val fixtures = NamespaceFixtures("segments-shop")
    private val changes = CopyOnWriteArrayList<Pair<String, String>>()
    private var abGroup: String? = "B"
    private lateinit var segments: SegmentsApiImpl

    @Before
    fun setUp() {
        fixtures.start()
        segments = SegmentsApiImpl(fixtures.calls, abGroup = { abGroup }) { type, signature -> changes += type to signature }
    }

    @After
    fun tearDown() = fixtures.stop()

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        when (method) {
            "add" -> segments.add(args.getInt("segmentId"), args.argString("email"), args.argString("phone"))
            "remove" -> segments.remove(args.getInt("segmentId"), args.argString("email"), args.argString("phone"))
            "get" -> segments.get()
            else -> error("no segments.$method")
        }
    }

    @Test
    fun `every method makes the contract's request, or fails with invalidArgument and sends nothing`() {
        fixtures.checkRequests("segments", invoke) { MockResponse().setBody("[]") }
        fixtures.checkErrors("segments", invoke)
    }

    @Test
    fun `every method reads each answer of the contract into its result or its error`() {
        fixtures.checkAnswers("segments", invoke)
    }

    @Test
    fun `the A-B group is the one the SDK keeps, and empty on an instance never initialized`() {
        assertEquals("B", segments.getAbGroup())

        abGroup = null

        assertEquals("", segments.getAbGroup())
        assertEquals(0, fixtures.server.requestCount)
    }

    @Test
    fun `add and remove tell popups v3 of the change once the server took it`() {
        fixtures.server.enqueue(MockResponse().setBody("{}"))
        fixtures.server.enqueue(MockResponse().setBody("{}"))
        fixtures.server.enqueue(MockResponse().setResponseCode(500))

        fixtures.outcome { segments.add(12) }
        fixtures.outcome { segments.remove(12, email = "jane@example.com") }
        fixtures.outcome { segments.add(13) }

        assertEquals(
            listOf(
                SegmentsApiImpl.SEGMENTS to """add {"segment_id":12}""",
                SegmentsApiImpl.SEGMENTS to """remove {"segment_id":12,"email":"jane@example.com"}""",
            ),
            changes,
        )
    }
}
