package com.personalization.core.internal

import org.json.JSONArray
import org.json.JSONObject

/**
 * The shared fixtures of the SDK contract, copied from `documentation-unified/sdk/fixtures` into
 * `src/test/resources/contract/<version>/`. Every SDK checks its implementation against the same
 * cases; update them by copying the new files into a folder of the new contract version. Beside them,
 * `logging.md` of `sdk/core`, whose table of log codes the logs are checked against.
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
