package com.personalization.core.internal

import android.content.Context
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.concurrent.TimeUnit

/**
 * A namespace's fixture file (`contract/0/<namespace>.json`) run against its implementation over a
 * [CoreV2] and a mock server: the request a call makes, the `invalidArgument` a call with wrong arguments
 * ends in without a request, and what each answer of the server reads into. A test hands over how to make
 * a case's call ([Invoke]) and how its result reads in the contract's names ([Json]).
 */
internal class NamespaceFixtures(private val shop: String) {

    /** Makes the call of a case: its method with its `args`. */
    fun interface Invoke {
        suspend fun call(method: String, args: JSONObject): Any?
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

    fun start(context: Context) {
        SdkLogs.reset()
        server.start()
        val client = CoreHttpClient({ server.url("/").toString() }, settings, connectivity, shop)
        core = CoreV2(client, shop).also { it.init = startedCoreInit(context, settings, connectivity, shop) }
        calls = NamespaceCalls(core = { core }, dispatchers = { CoreDispatchers.DEFAULT }, shopId = { shop })
    }

    fun stop() {
        core.release()
        server.close()
        SdkLogs.reset()
    }

    /** The cases of [file] whose call is of [namespace]. */
    private fun cases(file: String, namespace: String, kind: String): List<JSONObject> =
        ContractFixtures.cases(file).filter { it.has(kind) && it.getJSONObject("call").getString("namespace") == namespace }

    private fun outcome(case: JSONObject, invoke: Invoke): SdkResult<Any?> {
        val call = case.getJSONObject("call")
        return try {
            SdkResult.Success(runBlocking { withTimeout(10_000) { invoke.call(call.getString("method"), call.getJSONObject("args")) } })
        } catch (e: SdkException) {
            SdkResult.Failure(e.error)
        }
    }

    /**
     * Every call of [namespace] in [file] that pairs with a request makes it: its method, its path, and as
     * fields of its own exactly those of the case, beside the common ones the core adds.
     */
    fun checkRequests(file: String, namespace: String, invoke: Invoke): Int {
        val cases = cases(file, namespace, "request")
        cases.forEach { case ->
            val name = case.getString("name")
            val expected = case.getJSONObject("request")
            server.enqueue(MockResponse().setBody("{}"))

            outcome(case, invoke)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS) ?: throw AssertionError("$name: no request")
            val url = recorded.requestUrl!!
            assertEquals(name, expected.getString("method"), recorded.method)
            assertEquals(name, "/" + expected.getString("path"), url.encodedPath)
            if (recorded.method == "GET") {
                val own = url.queryParameterNames - COMMON_FIELDS
                assertEquals(name, normalize(expected.getJSONObject("query")), own.associateWith { queryValue(url, it) })
                assertEquals(name, COMMON_FIELDS, url.queryParameterNames intersect COMMON_FIELDS)
            } else {
                val body = JSONObject(recorded.body.readUtf8())
                val own = body.keys().asSequence().filter { it !in COMMON_FIELDS }.associateWith { normalize(body.get(it)) }
                assertEquals(name, normalize(expected.getJSONObject("body")), own)
                assertEquals(name, COMMON_FIELDS, body.keys().asSequence().toSet() intersect COMMON_FIELDS)
            }
        }
        return cases.size
    }

    /** A query field as the fixtures write it: one value as a string, a repeated key as the list of its values. */
    private fun queryValue(url: HttpUrl, key: String): Any? {
        val values = url.queryParameterValues(key)
        return if (key.endsWith("[]")) values else values.single()
    }

    /** Every call of [namespace] in [file] that pairs with an error ends in it, before any request. */
    fun checkErrors(file: String, namespace: String, invoke: Invoke): Int {
        val cases = cases(file, namespace, "error")
        cases.forEach { case ->
            val name = case.getString("name")
            val expected = ContractFixtures.errorOf(case.getJSONObject("error"))

            val outcome = outcome(case, invoke)

            val error = (outcome as? SdkResult.Failure)?.error
            if (expected is SdkError.InvalidArgument) {
                assertTrue("$name: $outcome", error is SdkError.InvalidArgument)
            } else {
                assertEquals(name, expected, error)
            }
            assertNull("$name: no request", server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
        return cases.size
    }

    /** Every answer of the server in [file] for a call of [namespace] reads into the case's result or error. */
    fun checkAnswers(file: String, namespace: String, invoke: Invoke, json: Json): Int {
        val cases = cases(file, namespace, "response")
        cases.forEach { case ->
            val name = case.getString("name")
            val response = ContractFixtures.mockResponse(case)
            // No answer: the request finds no network.
            connectivity.set(response != null)
            response?.let(server::enqueue)
            val expect = case.getJSONObject("expect")

            val outcome = outcome(case, invoke)

            if (expect.has("result")) {
                assertTrue("$name: $outcome", outcome is SdkResult.Success)
                assertMatches(name, normalize(expect.get("result")), normalize(json.of((outcome as SdkResult.Success).value)))
            } else {
                assertEquals(name, SdkResult.Failure(ContractFixtures.errorOf(expect.getJSONObject("error"))), outcome)
            }
            if (response != null) server.takeRequest(5, TimeUnit.SECONDS)
        }
        connectivity.set(true)
        return cases.size
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

        /** The strings of a JSON array in a case's `args`; null when the key is missing. */
        fun JSONObject.stringsOrNull(key: String): List<String>? =
            optJSONArray(key)?.let { array -> List(array.length()) { array.getString(it) } }

        /** A string of a case's `args`; null when the key is missing. */
        fun JSONObject.textOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

        /** A boolean of a case's `args`; null when the key is missing. */
        fun JSONObject.flagOrNull(key: String): Boolean? = if (has(key) && !isNull(key)) getBoolean(key) else null
    }
}
