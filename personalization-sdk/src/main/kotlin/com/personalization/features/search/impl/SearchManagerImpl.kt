package com.personalization.features.search.impl

import com.google.gson.Gson
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.SearchManager
import com.personalization.api.params.SearchParams
import com.personalization.api.responses.search.SearchBlankResponse
import com.personalization.api.responses.search.SearchFullResponse
import com.personalization.api.responses.search.PriceRange
import com.personalization.api.responses.search.SearchInstantResponse
import com.personalization.core.internal.isEmptyAnswer
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import org.json.JSONObject
import javax.inject.Inject

private const val SEARCH_REQUEST = "search"
private const val BLANK_SEARCH_REQUEST = "search/blank"
private const val TYPE_PARAMETER = "type"
private const val QUERY_PARAMETER = "search_query"

internal class SearchManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : SearchManager {

    override fun searchFull(
        query: String,
        searchParams: SearchParams,
        onSearchFull: (SearchFullResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        search(query, TYPE.FULL, searchParams, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                // A 2xx without a body is an empty result: the callback hears it too.
                val searchFullResponse = if (response.isEmptyAnswer()) emptyFull(query)
                else Gson().fromJson(response.toString(), SearchFullResponse::class.java)
                onSearchFull(searchFullResponse)
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    override fun searchInstant(
        query: String,
        locations: String?,
        excludedMerchants: List<String>?,
        excludedBrands: List<String>?,
        onSearchInstant: (SearchInstantResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val searchParams = SearchParams()
        locations?.let {
            searchParams.put(SearchParams.Parameter.LOCATIONS, locations)
        }
        // The server filters merchants inside the given locations only: without locations — or with
        // an empty list of them — the filter matches no product, and the search comes back empty.
        if (!locations.isNullOrBlank() && excludedMerchants != null) {
            searchParams.put(SearchParams.Parameter.EXCLUDED_MERCHANTS, excludedMerchants.joinToString(","))
        }
        excludedBrands?.let {
            searchParams.put(SearchParams.Parameter.EXCLUDED_BRANDS, excludedBrands.joinToString(","))
        }

        search(query, TYPE.INSTANT, searchParams, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                val searchInstantResponse = if (response.isEmptyAnswer()) emptyInstant(query)
                else Gson().fromJson(response.toString(), SearchInstantResponse::class.java)
                onSearchInstant(searchInstantResponse)
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
        sendNetworkMethodUseCase.get(
            BLANK_SEARCH_REQUEST,
            Params().build(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    val searchBlankResponse = if (response.isEmptyAnswer()) EMPTY_BLANK
                    else Gson().fromJson(response.toString(), SearchBlankResponse::class.java)
                    onSearchBlank(searchBlankResponse)
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

        sendNetworkMethodUseCase.get(SEARCH_REQUEST, params.build(), listener)
    }

    private companion object {
        /** What a search without results answers, for a 2xx without a body. */
        fun emptyFull(query: String) = SearchFullResponse(
            brands = emptyList(), categories = emptyList(), clarification = false, collections = emptyList(), html = "",
            priceMedian = 0.0, priceRange = PriceRange(max = 0.0, min = 0.0), priceRanges = emptyList(), products = emptyList(),
            productsTotal = 0, requestsCount = 0, searchQuery = query,
        )

        fun emptyInstant(query: String) = SearchInstantResponse(
            bookAuthor = emptyList(), categories = emptyList(), clarification = false, collections = emptyList(), html = "",
            products = emptyList(), productsTotal = 0, queries = emptyList(), requestsCount = 0, searchQuery = query,
        )

        val EMPTY_BLANK = SearchBlankResponse(
            html = "", products = emptyList(), suggests = emptyList(), popularCategories = null, popularBrands = null, popularLinks = null,
        )
    }

    private enum class TYPE(var value: String) {
        INSTANT("instant_search"),
        FULL("full_search")
    }
}
