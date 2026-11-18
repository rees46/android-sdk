package com.personalization.core.internal

import android.content.Context
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
 * that talks to a mock server: the request each call makes, the `invalidArgument` a call with wrong
 * arguments ends with, without a request, and what it makes of each answer. A test class starts it in
 * `@Before` and stops it in `@After`, builds its namespace over [calls], and says how to run a fixture's
 * call ([Invoke]). A file that holds the calls of more than one namespace (`sliders.json` with the banner
 * events) is checked one `namespace` at a time. The mock server stands for both hosts, the API's and that
 * of API v3; [timeouts] are the client's.
 */
internal class NamespaceFixtures(private val shop: String, private val timeouts: CoreTimeouts = CoreTimeouts()) {

    /** Runs the method [method] of the namespace with the fixture's [args]; its value is what the method returns. */
    fun interface Invoke {
        suspend fun invoke(method: String, args: JSONObject): Any?
    }

    /** A method's result as JSON values in the contract's names: maps, lists, strings, numbers, booleans, null. */
    fun interface Json {
        fun of(value: Any?): Any?
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

    /** The path the namespaces take, over the started core. */
    lateinit var calls: NamespaceCalls
        private set

    fun start(context: Context = ApplicationProvider.getApplicationContext()) {
        SdkLogs.reset()
        server.start()
        val client = CoreHttpClient(
            { server.url("/").toString() }, settings, connectivity, shop,
            timeouts = timeouts, appUrl = { server.url("/").toString() },
        )
        core = CoreV2(client, shop).also { it.init = startedCoreInit(context, settings, connectivity, shop) }
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

    /** The outcome of a [case]'s call, made with [invoke]. */
    private fun outcome(case: JSONObject, invoke: Invoke): SdkResult<Any?> {
        val call = case.getJSONObject("call")
        return outcome { invoke.invoke(call.getString("method"), call.optJSONObject("args") ?: JSONObject()) }
    }

    /**
     * The cases of [file] with [kind] (`request`, `error`, `response`): of [namespace] only, when it is given;
     * of [methods] only, when they are given; and none of the other platform's (`"platform": "ios"`).
     */
    private fun cases(file: String, kind: String, namespace: String?, methods: Set<String>? = null): List<JSONObject> =
        ContractFixtures.cases(file).filter { case ->
            val call = case.getJSONObject("call")
            case.has(kind) && !case.isNull(kind) &&
                (namespace == null || call.getString("namespace") == namespace) &&
                (methods == null || call.getString("method") in methods) &&
                case.optString("platform").let { it.isEmpty() || it == "android" }
        }

    /**
     * Every case of [file] that pairs a call with a request: the call sends that method, path and its own
     * fields — the query of a `GET`, the body of a `POST` — and nothing of its own beside them; the common
     * fields the core adds are there too, or only those of `request.commonFields`. [answer] is what the server
     * says to each request. Returns how many cases it checked.
     */
    fun checkRequests(
        file: String,
        invoke: Invoke,
        namespace: String? = null,
        methods: Set<String>? = null,
        answer: () -> MockResponse = { MockResponse().setBody("{}") },
    ): Int {
        val cases = cases(file, "request", namespace, methods)
        assertTrue("$file has no request cases", cases.isNotEmpty())
        cases.forEach { case ->
            val name = case.getString("name")
            server.enqueue(answer())
            outcome(case, invoke)
            val recorded = server.takeRequest(5, TimeUnit.SECONDS) ?: throw AssertionError("$name sent no request")
            checkRequest(name, case.getJSONObject("request"), recorded)
        }
        return cases.size
    }

    /** Every case of [file] that pairs a call with an error fails with it and sends nothing. Returns how many it checked. */
    fun checkErrors(file: String, invoke: Invoke, namespace: String? = null, methods: Set<String>? = null): Int {
        val cases = cases(file, "error", namespace, methods).filter { !it.has("response") }
        cases.forEach { case ->
            val name = case.getString("name")
            assertSameError(name, ContractFixtures.errorOf(case.getJSONObject("error")), outcome(case, invoke))
            assertNull("$name sent a request", server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
        return cases.size
    }

    /**
     * Every case of [file] that pairs an answer with what the method returns: the call fails with
     * `expect.error`, or its value equals `expect.result` — as [ContractJson] writes it, or, when the test
     * gives [json], as that writes it, with an object of `expect.result` listing only the fields the case
     * checks ([assertMatches]). The result of a method of [listed] is matched so too, written as
     * [ContractJson] writes it: a file compared whole names the methods whose released model the platforms
     * name differently (`profile.getOrders`). No answer (`"status": null`) is a request without a network.
     * Returns how many cases it checked.
     */
    fun checkAnswers(
        file: String,
        invoke: Invoke,
        namespace: String? = null,
        json: Json? = null,
        methods: Set<String>? = null,
        listed: Set<String> = emptySet(),
    ): Int {
        val cases = cases(file, "response", namespace, methods)
        assertTrue("$file has no answer cases", cases.isNotEmpty())
        cases.forEach { case ->
            val name = case.getString("name")
            val response = ContractFixtures.mockResponse(case)
            connectivity.set(response != null)
            response?.let(server::enqueue)
            val expect = case.getJSONObject("expect")

            val outcome = outcome(case, invoke)

            if (expect.has("result")) {
                if (outcome !is SdkResult.Success) fail("$name: $outcome")
                val value = (outcome as SdkResult.Success).value
                when {
                    json != null -> assertMatches(name, normalize(expect.get("result")), normalize(json.of(value)))
                    case.getJSONObject("call").getString("method") in listed ->
                        assertMatches(name, normalize(expect.get("result")), normalize(ContractJson.of(value)))
                    else -> ContractJson.assertEquals(name, expect.opt("result"), ContractJson.of(value))
                }
            } else {
                assertSameError(name, ContractFixtures.errorOf(expect.getJSONObject("error")), outcome)
            }
            response?.let { server.takeRequest(5, TimeUnit.SECONDS) }
            connectivity.set(true)
        }
        return cases.size
    }

    /**
     * [recorded] is the request [expected] names. Of the common fields it carries all, or those of
     * `commonFields` alone — a server method's: `shop_id` and none of the device's. A common field the case
     * lists among its own fields is the method's, given by the caller: `did` of a server method.
     */
    private fun checkRequest(name: String, expected: JSONObject, recorded: RecordedRequest) {
        assertEquals(name, expected.getString("method"), recorded.method)
        val url = recorded.requestUrl!!
        assertEquals(name, "/" + expected.getString("path"), url.encodedPath)
        val fields = expected.optJSONObject(if (recorded.method == "GET") "query" else "body") ?: JSONObject()
        val listed = fields.keys().asSequence().toSet()
        val common = expected.optJSONArray("commonFields")?.let { array -> List(array.length()) { array.getString(it) }.toSet() } ?: COMMON_FIELDS
        val added = COMMON_FIELDS - listed
        val own: Map<String, Any?> = if (recorded.method == "GET") {
            assertEquals("$name: common fields", common - listed, url.queryParameterNames intersect added)
            // A list goes as its key repeated (`promo_codes[]=A&promo_codes[]=B`): the fixtures write its values as a list.
            (url.queryParameterNames - added).associateWith { key ->
                url.queryParameterValues(key).let { values -> if (key.endsWith("[]")) values else values.single() }
            }
        } else {
            val body = JSONObject(recorded.body.readUtf8())
            assertEquals("$name: common fields", common - listed, body.keys().asSequence().toSet() intersect added)
            (body.keys().asSequence().toSet() - added).associateWith { body.get(it) }
        }
        assertEquals("$name: fields", listed, own.keys)
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

        /** JSON and Kotlin values alike as maps, lists, strings, booleans, doubles and null: one number type, so `2` is `2.0`. */
        fun normalize(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> value.keys().asSequence().associateWith { normalize(value.get(it)) }
            is JSONArray -> (0 until value.length()).map { normalize(value.get(it)) }
            is Map<*, *> -> value.entries.associate { (key, item) -> key.toString() to normalize(item) }
            is Iterable<*> -> value.map(::normalize)
            is Number -> value.toDouble()
            else -> value
        }

        /**
         * [actual] matches [expected] as the fixtures read `expect.result`: an object lists the fields the case
         * checks, and each must be there with a matching value; a list has as many items, each matching in turn;
         * anything else is equal, `null` included. [at] is where in the result, for the message.
         */
        fun assertMatches(name: String, expected: Any?, actual: Any?, at: String = "result") {
            when (expected) {
                is Map<*, *> -> {
                    assertTrue("$name: $at is $actual, not an object", actual is Map<*, *>)
                    val fields = actual as Map<*, *>
                    expected.forEach { (key, value) ->
                        assertTrue("$name: $at has no $key", fields.containsKey(key))
                        assertMatches(name, value, fields[key], "$at.$key")
                    }
                }
                is List<*> -> {
                    assertTrue("$name: $at is $actual, not a list", actual is List<*>)
                    val items = actual as List<*>
                    assertEquals("$name: the size of $at", expected.size, items.size)
                    expected.forEachIndexed { index, item -> assertMatches(name, item, items[index], "$at[$index]") }
                }
                else -> assertEquals("$name: $at", expected, actual)
            }
        }
    }
}

/**
 * The namespaces' models as the fixtures write them: each property under its own name, which is the
 * contract's; a null property is left out; a list is an array, a map an object. Equality of two such
 * values compares numbers by value, so `19990` equals `19990.0`.
 */
internal object ContractJson {

    fun of(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is String, is Boolean, is Number, is JSONObject, is JSONArray -> value
        // An enum value by the contract's name: FEMALE is `female`, INSTANT_SEARCH is `instantSearch`.
        is Enum<*> -> value.name.lowercase().split('_').let { words -> words.first() + words.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) } }
        is List<*> -> JSONArray().apply { value.forEach { put(of(it) ?: JSONObject.NULL) } }
        is Map<*, *> -> JSONObject().apply { value.forEach { (key, item) -> of(item)?.let { put(key.toString(), it) } } }
        // A method that returns nothing: `"result": null`.
        is Unit -> null
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

internal fun JSONObject.argLong(key: String): Long? = if (isNull(key)) null else getLong(key)

internal fun JSONObject.argDouble(key: String): Double? = if (isNull(key)) null else getDouble(key)

internal fun JSONObject.argBoolean(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)

internal fun JSONObject.argStrings(key: String): List<String>? =
    optJSONArray(key)?.let { array -> List(array.length()) { array.getString(it) } }

/** A map of strings to lists of strings, such as `filters`. */
internal fun JSONObject.argStringLists(key: String): Map<String, List<String>>? = optJSONObject(key)?.let { map ->
    map.keys().asSequence().associateWith { name -> map.getJSONArray(name).let { array -> List(array.length()) { array.getString(it) } } }
}

/** A free-form map of a fixture's `args` as plain Kotlin values: maps, lists, strings, numbers, booleans and null. */
internal fun JSONObject.argMap(key: String): Map<String, Any?>? = optJSONObject(key)?.let { plainArg(it) as Map<String, Any?> }

private fun plainArg(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> value.keys().asSequence().associateWith { plainArg(value.opt(it)) }
    is JSONArray -> List(value.length()) { plainArg(value.opt(it)) }
    else -> value
}
