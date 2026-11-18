package com.personalization.core.internal

import com.personalization.core.SdkError
import okhttp3.mockwebserver.MockResponse
import org.json.JSONArray
import org.json.JSONObject

/**
 * The shared fixtures of the SDK contract, copied from `documentation-unified/sdk/fixtures` into
 * `src/test/resources/contract/<version>/`. Every SDK checks its implementation against the same
 * cases; update them by copying the new files into a folder of the new contract version. Beside them,
 * `logging.md` of `sdk/core`, whose table of log codes the logs are checked against.
 *
 * The namespaces' files (`cart.json` and those after it) are written in the fixtures' format from their files
 * of `contract/` and the server's answers in `questions.md`, ahead of the contract's own files, which take
 * them over as they are. `cart.json`, `notifications.json`, `stories.json`, `predict.json`, `loyalty.json`,
 * `sliders.json` and `reviews.json` are the files the iOS SDK tests by. In the `expect.result` of
 * `stories.json`, `predict.json`, `loyalty.json` and `sliders.json` an object lists the fields the case
 * checks. `sliders.json` also holds the banner events, `tracking.bannerView` and `bannerClick` (their
 * `call.namespace` says so), until the contract's `tracking.json` has them.
 */
internal object ContractFixtures {
    const val VERSION = "0"

    /** The `cases` of `<name>.json`; each file declares the contract version it was written for. */
    fun cases(name: String): List<JSONObject> {
        val file = load(name)
        check(file.getString("contract") == VERSION) {
            "$name.json is for contract ${file.getString("contract")}, the tests expect $VERSION"
        }
        val cases: JSONArray = file.getJSONArray("cases")
        return List(cases.length()) { cases.getJSONObject(it) }
    }

    private fun load(name: String): JSONObject = JSONObject(text("$name.json"))

    /** The cases of `<name>.json` that pair a server's answer with what the method returns. */
    fun answers(name: String): List<JSONObject> = cases(name).filter { it.has("response") }

    /** The cases of `<name>.json` that pair a call with the request it makes. */
    fun requests(name: String): List<JSONObject> = cases(name).filter { it.has("request") }

    /**
     * The server's answer of a case (`response`) for a mock server; null for `"status": null`, no answer,
     * which a test makes by other means (no network).
     */
    fun mockResponse(case: JSONObject): MockResponse? {
        val response = case.getJSONObject("response")
        if (response.isNull("status")) return null
        val body = response.opt("body")
        return MockResponse().setResponseCode(response.getInt("status"))
            .setBody(if (body == null || body == JSONObject.NULL) "" else body.toString())
    }

    /** The [SdkError] an `error` object of a case names: `{"type": "http", "code": 404}`. */
    fun errorOf(error: JSONObject): SdkError = when (val type = error.getString("type")) {
        "notInitialized" -> SdkError.NotInitialized
        "shopBlocked" -> SdkError.ShopBlocked
        "invalidArgument" -> SdkError.InvalidArgument(error.optString("message"))
        "network" -> SdkError.Network
        "timeout" -> SdkError.Timeout
        "http" -> SdkError.Http(error.getInt("code"), error.optString("serverCode").ifEmpty { null })
        "decoding" -> SdkError.Decoding
        "cancelled" -> SdkError.Cancelled
        else -> error("unknown SdkError type $type")
    }

    /**
     * A file of the contract copied beside the fixtures as it is, such as `logging.md` of `sdk/core`: what a
     * test reads the contract's own tables from, instead of a copy of them in the test.
     */
    fun text(file: String): String {
        val path = "contract/$VERSION/$file"
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "no contract file $path" }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
