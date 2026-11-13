package com.personalization.core.internal

import org.json.JSONArray
import org.json.JSONObject

/**
 * The shared fixtures of the SDK contract, copied from `documentation-unified/sdk/fixtures` into
 * `src/test/resources/contract/<version>/`. Every SDK checks its implementation against the same
 * cases; update them by copying the new files into a folder of the new contract version.
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

    private fun load(name: String): JSONObject {
        val path = "contract/$VERSION/$name.json"
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "no fixture $path" }
        return JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
}
