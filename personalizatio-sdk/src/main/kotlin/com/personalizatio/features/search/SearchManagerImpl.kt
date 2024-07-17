package com.personalizatio.features.search

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.managers.NetworkManager
import com.personalizatio.api.params.SearchParams
import com.personalizatio.api.managers.SearchManager
import com.personalizatio.api.responses.search.SearchBlankResponse
import com.personalizatio.api.responses.search.SearchFullResponse
import com.personalizatio.api.responses.search.SearchInstantResponse
import org.json.JSONObject
import javax.inject.Inject

internal class SearchManagerImpl @Inject constructor(
    val networkManager: NetworkManager
) : SearchManager {

    override fun searchFull(
        query: String,
        searchParams: SearchParams,
        onSearchFull: (SearchFullResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        search(query, TYPE.FULL, searchParams, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val searchFullResponse = Gson().fromJson(it.toString(), SearchFullResponse::class.java)
                    onSearchFull(searchFullResponse)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    override fun searchInstant(
        query: String,
        locations: String?,
        onSearchInstant: (SearchInstantResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val searchParams = SearchParams()

        if(locations != null) searchParams.put(LOCATIONS_PARAMETER, locations)

        search(query, TYPE.INSTANT, searchParams, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val searchInstantResponse = Gson().fromJson(it.toString(), SearchInstantResponse::class.java)
                    onSearchInstant(searchInstantResponse)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    override fun searchBlank(
        onSearchBlank: (SearchBlankResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        networkManager.post(BLANK_SEARCH_REQUEST, Params().build(), object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val searchBlankResponse = Gson().fromJson(it.toString(), SearchBlankResponse::class.java)
                    onSearchBlank(searchBlankResponse)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    private fun search(
        query: String,
        type: TYPE,
        params: SearchParams,
        listener: OnApiCallbackListener
    ) {
        params
            .put(TYPE_PARAMETER, type.value)
            .put(QUERY_PARAMETER, query)

        networkManager.post(SEARCH_REQUEST, params.build(), listener)
    }

    companion object {
        private const val SEARCH_REQUEST = "search"
        private const val BLANK_SEARCH_REQUEST = "search/blank"

        private const val TYPE_PARAMETER = "type"
        private const val QUERY_PARAMETER = "search_query"
        private const val LOCATIONS_PARAMETER = "locations"
    }

    private enum class TYPE(var value: String) {
        INSTANT("instant_search"),
        FULL("full_search")
    }
}
