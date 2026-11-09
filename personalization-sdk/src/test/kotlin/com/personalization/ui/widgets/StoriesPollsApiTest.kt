package com.personalization.ui.widgets

import android.os.Looper
import android.util.Log
import com.personalization.SDK
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** The HTTP transport of polls: requests of `polls.test.ts` and every kind of failure. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesPollsApiTest {

    private val sdk = mockk<SDK>(relaxed = true)
    private val requests = mutableListOf<Triple<String, String, String?>>()
    private var reply = StoriesApiV3.Response(200, """{"status":"success","payload":{}}""")
    private var networkError: Throwable? = null
    private val originalHttp = StoriesApiV3.http

    @Before
    fun setUp() {
        every { sdk.getDid() } returns "did1"
        every { sdk.getSid() } returns "sid1"
        every { sdk.getUserSettingsValueUseCase.getShopId() } returns "shop1"
        StoriesApiV3.runner = { it.run() }
        StoriesApiV3.http = StoriesApiV3.Http { method, url, body ->
            requests += Triple(method, url, body)
            networkError?.let { throw it }
            reply
        }
    }

    @After
    fun tearDown() {
        StoriesApiV3.http = originalHttp
        StoriesApiV3.runner = { Thread(it).start() }
    }

    @Test
    fun `a mini-poll answer goes to its code on the v3 host with the identity`() {
        answer(code = "gift recipient")

        val (method, url, body) = requests.single()
        assertEquals("POST", method)
        assertEquals("https://app.test/api/v3/polls/mini_polls/gift%20recipient/answers", url)
        val json = JSONObject(body!!)
        assertEquals(listOf("shop_id", "did", "sid", "option_ids", "story_id", "slide_id"), json.keys().asSequence().toList())
        assertEquals("shop1", json.getString("shop_id"))
        assertEquals("did1", json.getString("did"))
        assertEquals("sid1", json.getString("sid"))
        assertEquals(2, json.getJSONArray("option_ids").getInt(0))
        assertEquals(5, json.get("story_id"))
        assertEquals("101", json.get("slide_id"))
    }

    @Test
    fun `a review goes to its scale with channel, category or campaign and rate`() {
        review("nps", mapOf("channel" to "stories", "category" to "delivery", "rate" to 9))
        review("csat", mapOf("channel" to "stories", "campaign" to "support", "rate" to 4))

        assertEquals("https://app.test/api/v3/polls/nps/reviews", requests[0].second)
        assertEquals("""{"shop_id":"shop1","did":"did1","sid":"sid1","channel":"stories","category":"delivery","rate":9}""", requests[0].third)
        assertEquals("https://app.test/api/v3/polls/csat/reviews", requests[1].second)
        assertEquals("support", JSONObject(requests[1].third!!).getString("campaign"))
    }

    @Test
    fun `the session always goes, without one as an empty string`() {
        every { sdk.getSid() } returns ""
        answer()
        every { sdk.getSid() } throws IllegalStateException("not ready")
        review("nps", mapOf("rate" to 1))
        assertEquals(listOf("", ""), requests.map { JSONObject(it.third!!).getString("sid") })
    }

    @Test
    fun `a failure goes to the debug log with its code and message`() {
        reply = StoriesApiV3.Response(429, """{"status":"fail","message":"Too many requests"}""")
        answer()
        val logs = ShadowLog.getLogs().filter { it.type == Log.DEBUG }.map { it.msg }
        assertTrue(logs.toString(), logs.any { it.contains("/polls/mini_polls/gift_recipient/answers failed: 429 Too many requests") })
    }

    @Test
    fun `success takes the answer and results of the payload`() {
        reply = StoriesApiV3.Response(
            200,
            """{"status":"success","payload":{"answer":{"id":77,"option_ids":[2],"text":null},
            "results":{"total":121,"options":[{"id":1,"percent":40},{"id":2,"percent":60}]}}}"""
        )
        val result = answer() as StoriesPollsResult.Success
        assertEquals(StoriesPollAnswer(optionIds = listOf(2), id = 77), result.answer)
        assertEquals(121, result.results!!.total)
        assertEquals(60, result.results!!.percentOf(2))
    }

    @Test
    fun `success without an answer leaves it to the card`() {
        reply = StoriesApiV3.Response(200, """{"status":"success"}""")
        val result = answer() as StoriesPollsResult.Success
        assertNull(result.answer)
        assertNull(result.results)

        reply = StoriesApiV3.Response(200, """{"status":"success","payload":{"review":{"id":1}}}""")
        assertTrue(review("ces", mapOf("rate" to 2)) is StoriesPollsResult.Success)

        // Any answer object is the answer, even without options and text.
        reply = StoriesApiV3.Response(200, """{"status":"success","payload":{"answer":{"id":77},"results":null}}""")
        assertEquals(StoriesPollAnswer(id = 77), (answer() as StoriesPollsResult.Success).answer)
    }

    @Test
    fun `http errors fail with their code and message`() {
        listOf(400, 402, 403, 404, 429, 500, 503).forEach { code ->
            reply = StoriesApiV3.Response(code, """{"status":"fail","message":"Error $code"}""")
            val failure = answer() as StoriesPollsResult.Failure
            assertEquals(code, failure.code)
            assertEquals("Error $code", failure.message)
        }
        reply = StoriesApiV3.Response(502, "<html>Bad gateway</html>")
        assertEquals("<html>Bad gateway</html>", (answer() as StoriesPollsResult.Failure).message)
    }

    @Test
    fun `only 2xx is a success`() {
        listOf(200, 201, 299).forEach { code ->
            reply = StoriesApiV3.Response(code, """{"status":"success"}""")
            assertTrue("$code", answer() is StoriesPollsResult.Success)
        }
        listOf(302, 304, 399).forEach { code ->
            reply = StoriesApiV3.Response(code, """{"status":"success"}""")
            assertEquals(code, (answer() as StoriesPollsResult.Failure).code)
        }
    }

    @Test
    fun `no network, invalid JSON and fail or error at 200 are failures`() {
        networkError = IOException("timeout")
        assertEquals(-1, (answer() as StoriesPollsResult.Failure).code)
        networkError = null

        reply = StoriesApiV3.Response(200, "<html>")
        assertEquals(-1, (answer() as StoriesPollsResult.Failure).code)

        reply = StoriesApiV3.Response(200, """{"status":"fail","message":"Poll not found"}""")
        assertEquals("Poll not found", (answer() as StoriesPollsResult.Failure).message)

        reply = StoriesApiV3.Response(200, """{"status":"error"}""")
        assertTrue(review("nps", mapOf("rate" to 1)) is StoriesPollsResult.Failure)
    }

    @Test
    fun `without a did the request fails after the wait and is never sent`() {
        every { sdk.getDid() } returns null
        val queued = slot<Thread>()
        every { sdk.addTaskToQueueUseCase.invoke(capture(queued)) } just runs
        var result: StoriesPollsResult? = null
        StoriesPollsApi(sdk, "app.test").answerMiniPoll("x", mapOf("text" to "a")) { result = it }
        idle()
        assertNull(result)

        shadowOf(Looper.getMainLooper()).idleFor(StoriesPollsApi.DID_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertEquals("no did", (result as StoriesPollsResult.Failure).message)

        // The did came later: the reported request is not sent anymore.
        every { sdk.getDid() } returns "did1"
        queued.captured.run()
        idle()
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `the did that comes in time sends the request once`() {
        every { sdk.getDid() } returns null
        val queued = slot<Thread>()
        every { sdk.addTaskToQueueUseCase.invoke(capture(queued)) } just runs
        val results = mutableListOf<StoriesPollsResult>()
        StoriesPollsApi(sdk, "app.test").answerMiniPoll("x", mapOf("text" to "a")) { results += it }

        every { sdk.getDid() } returns "did1"
        queued.captured.run()
        shadowOf(Looper.getMainLooper()).idleFor(StoriesPollsApi.DID_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
        assertEquals(1, requests.size)
        assertEquals(1, results.size)
        assertTrue(results.single() is StoriesPollsResult.Success)
    }

    private fun answer(
        code: String = "gift_recipient",
        body: Map<String, Any> = StoryPollBodies.answer(5, "101", optionIds = listOf(2))
    ): StoriesPollsResult {
        var result: StoriesPollsResult? = null
        StoriesPollsApi(sdk, "https://app.test/").answerMiniPoll(code, body) { result = it }
        idle()
        return result!!
    }

    private fun review(kind: String, body: Map<String, Any>): StoriesPollsResult {
        var result: StoriesPollsResult? = null
        StoriesPollsApi(sdk, "app.test").review(kind, body) { result = it }
        idle()
        return result!!
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
