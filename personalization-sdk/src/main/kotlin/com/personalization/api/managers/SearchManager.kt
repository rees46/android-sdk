package com.personalization.api.managers

import com.personalization.api.params.SearchParams
import com.personalization.api.responses.search.SearchBlankResponse
import com.personalization.api.responses.search.SearchFullResponse
import com.personalization.api.responses.search.SearchInstantResponse

interface SearchManager {

    /**
     * Search full
     *
     * @param query Search phrase
     * @param searchParams SearchParams
     * @param onSearchFull Callback for search full
     * @param onError Callback for error
     */
    fun searchFull(
        query: String,
        searchParams: SearchParams = SearchParams(),
        onSearchFull: (SearchFullResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Search instant
     *
     * @param query Search phrase
     * @param locations Comma separated list of locations IDs
     * @param onSearchInstant Callback for search instant
     * @param onError Callback for error
     */
    fun searchInstant(
        query: String,
        locations: String? = null,
        excludeMerchants: String? = null,
        onSearchInstant: (SearchInstantResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Search blank
     *
     * @param onSearchBlank Callback for search blank
     * @param onError Callback for error
     */
    fun searchBlank(
        onSearchBlank: (SearchBlankResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )
}
