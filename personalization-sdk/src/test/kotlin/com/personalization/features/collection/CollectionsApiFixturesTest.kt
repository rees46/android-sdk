package com.personalization.features.collection

import com.personalization.core.BridgeApi
import com.personalization.core.internal.NamespaceFixtures
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `sdk.collections` against the contract's fixtures (`contract/0/collections.json`). */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CollectionsApiFixturesTest {

    private val fixtures = NamespaceFixtures("collections-shop")
    private lateinit var collections: CollectionsApiImpl

    @Before
    fun setUp() {
        fixtures.start()
        collections = CollectionsApiImpl(fixtures.calls)
    }

    @After
    fun tearDown() = fixtures.stop()

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        when (method) {
            "get" -> collections.get(args.getString("collectionId"))
            else -> error("no collections.$method")
        }
    }

    @Test
    fun `get makes the contract's request, or fails with invalidArgument and sends nothing`() {
        fixtures.checkRequests("collections", invoke) { MockResponse().setBody("""{"products":[]}""") }
        fixtures.checkErrors("collections", invoke)
    }

    @Test
    fun `get reads each answer of the contract into its result or its error`() {
        fixtures.checkAnswers("collections", invoke)
    }
}
