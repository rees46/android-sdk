package com.personalization.core.internal

import androidx.test.core.app.ApplicationProvider
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit

/**
 * A namespace's methods against the contract's fixtures (`contract/0/<namespace>.json`), over a new core
 * that talks to a mock server: the request each call makes (or the `invalidArgument` it ends with, without
 * one), and what it makes of each answer. A test class starts it in `@Before` and stops it in `@After`,
 * builds its namespace over [calls], and says how to run a fixture's call ([Invoke]). The mock server
 * stands for both hosts, the API's and that of API v3; [timeouts] are the client's.
 */
internal class NamespaceFixtures(private val shop: String, private val timeouts: CoreTimeouts = CoreTimeouts()) {

    /** Runs the method [method] of the namespace with the fixture's [args]; its value is what the method returns. */
    fun interface Invoke {
        suspend fun invoke(method: String, args: JSONObject): Any?
    }

    val server = MockWebServer()
    private val connectivity = FakeConnectivity(connected = true)
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getShopId() } returns shop
        every { getDid() } returns "did-1"
        every { getSid() } returns "sid-1"
        every { getStream() } returns "android"
        every { getSegmentForABTesting() } returns "B"
    }
    private lateinit var core: CoreV2
    lateinit var calls: NamespaceCalls
        private set

    fun start() {
        SdkLogs.reset()
        server.start()
        val client = CoreHttpClient(
            { server.url("/").toString() }, settings, connectivity, shop,
            timeouts = timeouts, appUrl = { server.url("/").toString() },
        )
        core = CoreV2(client, shop).also {
            it.init = startedCoreInit(ApplicationProvider.getApplicationContext(), settings, connectivity, shop)
        }
        calls = NamespaceCalls(core = { core }, dispatchers = { CoreDispatchers.DEFAULT }, shopId = { shop })
    }

    fun stop() {
        core.release()
        server.close()
        SdkLogs.reset()
    }

    /** The outcome of [block], a call of a method's suspending form. */
    fun outcome(block: suspend () -> Any?): SdkResult<Any?> = try {
        SdkResult.Success(runBlocking { withTimeout(10_000) { block() } })
    } catch (e: SdkException) {
        SdkResult.Failure(e.error)
    }

    /**
     * Every case of [file] that pairs a call with a request: the call sends that method, path and its own
     * fields — the query of a `GET`, the body of a `POST` — and nothing of its own beside them; the common
     * fields the core adds are there too. A case with `error` instead fails with it and sends nothing.
     * [answer] is what the server says to each request.
     */
    fun checkRequests(file: String, invoke: Invoke, answer: () -> MockResponse = { MockResponse().setBody("{}") }) {
        val cases = ContractFixtures.cases(file).filter { it.has("request") || (it.has("error") && !it.has("response")) }
        assertTrue("$file has no request cases", cases.isNotEmpty())
        cases.forEach { case ->
            val name = case.getString("name")
            val call = case.getJSONObject("call")
            if (case.has("error")) {
                val expected = ContractFixtures.errorOf(case.getJSONObject("error"))
                val outcome = outcome { invoke.invoke(call.getString("method"), call.optJSONObject("args") ?: JSONObject()) }
                assertSameError(name, expected, outcome)
                assertNull("$name sent a request", server.takeRequest(100, TimeUnit.MILLISECONDS))
                return@forEach
            }
            server.enqueue(answer())
            outcome { invoke.invoke(call.getString("method"), call.optJSONObject("args") ?: JSONObject()) }
            val recorded = server.takeRequest(5, TimeUnit.SECONDS) ?: throw AssertionError("$name sent no request")
            checkRequest(name, case.getJSONObject("request"), recorded)
        }
    }

    /**
     * Every case of [file] that pairs an answer with what the method returns: the value, as [ContractJson]
     * writes it, equals `expect.result`; or the call fails with `expect.error`. No answer
     * (`"status": null`) is a request without a network.
     */
    fun checkAnswers(file: String, invoke: Invoke) {
        val cases = ContractFixtures.answers(file)
        assertTrue("$file has no answer cases", cases.isNotEmpty())
        cases.forEach { case ->
            val name = case.getString("name")
            val call = case.getJSONObject("call")
            val response = ContractFixtures.mockResponse(case)
            connectivity.set(response != null)
            response?.let(server::enqueue)
            val expect = case.getJSONObject("expect")

            val outcome = outcome { invoke.invoke(call.getString("method"), call.optJSONObject("args") ?: JSONObject()) }

            if (expect.has("result")) {
                if (outcome !is SdkResult.Success) fail("$name: $outcome")
                ContractJson.assertEquals(name, expect.opt("result"), ContractJson.of((outcome as SdkResult.Success).value))
            } else {
                assertSameError(name, ContractFixtures.errorOf(expect.getJSONObject("error")), outcome)
            }
            response?.let { server.takeRequest(5, TimeUnit.SECONDS) }
            connectivity.set(true)
        }
    }

    private fun checkRequest(name: String, expected: JSONObject, recorded: RecordedRequest) {
        assertEquals(name, expected.getString("method"), recorded.method)
        val url = recorded.requestUrl!!
        assertEquals(name, "/" + expected.getString("path"), url.encodedPath)
        val own: Map<String, Any?> = if (recorded.method == "GET") {
            assertEquals("$name: common fields", COMMON_FIELDS, url.queryParameterNames intersect COMMON_FIELDS)
            (url.queryParameterNames - COMMON_FIELDS).associateWith { url.queryParameter(it) }
        } else {
            val body = JSONObject(recorded.body.readUtf8())
            val common = expected.optJSONArray("commonFields")?.let { array -> List(array.length()) { array.getString(it) }.toSet() } ?: COMMON_FIELDS
            assertEquals("$name: common fields", common, body.keys().asSequence().toSet() intersect COMMON_FIELDS)
            (body.keys().asSequence().toSet() - COMMON_FIELDS).associateWith { body.get(it) }
        }
        val fields = expected.optJSONObject(if (recorded.method == "GET") "query" else "body") ?: JSONObject()
        assertEquals("$name: fields", fields.keys().asSequence().toSet(), own.keys)
        fields.keys().forEach { key ->
            ContractJson.assertEquals("$name: $key", fields.get(key), ContractJson.of(own[key]))
        }
    }

    /** The same error; `invalidArgument` by its kind only, since its message is the platform's. */
    private fun assertSameError(name: String, expected: SdkError, outcome: SdkResult<Any?>) {
        if (expected is SdkError.InvalidArgument) {
            assertTrue("$name: $outcome", outcome is SdkResult.Failure && outcome.error is SdkError.InvalidArgument)
        } else {
            assertEquals(name, SdkResult.Failure(expected), outcome)
        }
    }

    companion object {
        val COMMON_FIELDS = setOf("shop_id", "did", "sid", "seance", "segment", "stream")
    }
}

/**
 * The namespaces' models as the fixtures write them: each property under its own name, which is the
 * contract's; a null property is left out; a list is an array, a map an object. Equality of two such
 * values compares numbers by value, so `19990` equals `19990.0`.
 */
internal object ContractJson {

    fun of(value: Any?): Any? = when (value) {
        null -> null
        is String, is Boolean, is Number, is JSONObject, is JSONArray -> value
        is Enum<*> -> value.name
        is List<*> -> JSONArray().apply { value.forEach { put(of(it) ?: JSONObject.NULL) } }
        is Map<*, *> -> JSONObject().apply { value.forEach { (key, item) -> of(item)?.let { put(key.toString(), it) } } }
        is Unit -> JSONObject()
        else -> JSONObject().apply {
            value.javaClass.declaredFields
                .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
                .forEach { field ->
                    field.isAccessible = true
                    of(field.get(value))?.let { put(field.name, it) }
                }
        }
    }

    fun assertEquals(name: String, expected: Any?, actual: Any?) {
        val want = expected.takeUnless { it == JSONObject.NULL }
        val got = actual.takeUnless { it == JSONObject.NULL }
        when {
            want == null || got == null -> org.junit.Assert.assertEquals(name, want, got)
            want is Number && got is Number -> org.junit.Assert.assertEquals(name, want.toDouble(), got.toDouble(), 0.0)
            want is JSONObject && got is JSONObject -> {
                val wantKeys = want.keys().asSequence().filter { !want.isNull(it) }.toSet()
                val gotKeys = got.keys().asSequence().filter { !got.isNull(it) }.toSet()
                org.junit.Assert.assertEquals("$name: keys", wantKeys.sorted(), gotKeys.sorted())
                wantKeys.forEach { key -> assertEquals("$name.$key", want.get(key), got.get(key)) }
            }
            want is JSONArray && got is JSONArray -> {
                org.junit.Assert.assertEquals("$name: size", want.length(), got.length())
                for (index in 0 until want.length()) assertEquals("$name[$index]", want.get(index), got.get(index))
            }
            else -> org.junit.Assert.assertEquals(name, want.toString(), got.toString())
        }
    }
}

/* Reading a fixture's `args`: a key that is missing or `null` is null. */

internal fun JSONObject.argString(key: String): String? = if (isNull(key)) null else getString(key)

internal fun JSONObject.argInt(key: String): Int? = if (isNull(key)) null else getInt(key)

internal fun JSONObject.argDouble(key: String): Double? = if (isNull(key)) null else getDouble(key)

internal fun JSONObject.argBoolean(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)

internal fun JSONObject.argStrings(key: String): List<String>? =
    optJSONArray(key)?.let { array -> List(array.length()) { array.getString(it) } }

/** A map of strings to lists of strings, such as `filters`. */
internal fun JSONObject.argStringLists(key: String): Map<String, List<String>>? = optJSONObject(key)?.let { map ->
    map.keys().asSequence().associateWith { name -> map.getJSONArray(name).let { array -> List(array.length()) { array.getString(it) } } }
}
