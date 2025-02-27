package com.personalization.features.search.impl

import com.google.gson.Gson
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.SearchManager
import com.personalization.api.params.SearchParams
import com.personalization.api.responses.search.SearchBlankResponse
import com.personalization.api.responses.search.SearchFullResponse
import com.personalization.api.responses.search.SearchInstantResponse
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
                response?.let {
                    val searchFullResponse =
                        Gson().fromJson(it.toString(), SearchFullResponse::class.java)
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
        excludedMerchants: List<String>?,
        excludedBrands: List<String>?,
        onSearchInstant: (SearchInstantResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val searchParams = SearchParams()
        locations?.let {
            searchParams.put(SearchParams.Parameter.LOCATIONS, locations)
            excludedMerchants?.let {
                val excludeMerchantsString = excludedMerchants.joinToString(",")
                searchParams.put(SearchParams.Parameter.EXCLUDED_MERCHANTS, excludeMerchantsString)
            }
        }
        excludedBrands?.let {
            searchParams.put(SearchParams.Parameter.EXCLUDED_BRANDS, excludedBrands.joinToString(","))
        }

        search(query, TYPE.INSTANT, searchParams, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val searchInstantResponse =
                        Gson().fromJson(it.toString(), SearchInstantResponse::class.java)
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
        sendNetworkMethodUseCase.get(
            BLANK_SEARCH_REQUEST,
            Params().build(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    response?.let {
                        val searchBlankResponse =
                            Gson().fromJson(it.toString(), SearchBlankResponse::class.java)
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

        sendNetworkMethodUseCase.get(SEARCH_REQUEST, params.build(), listener)
    }

    private enum class TYPE(var value: String) {
        INSTANT("instant_search"),
        FULL("full_search")
    }
}
