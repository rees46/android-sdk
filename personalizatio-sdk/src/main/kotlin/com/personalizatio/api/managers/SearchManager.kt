package com.personalizatio.api.managers

import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.entities.search.SearchBlankEntity
import com.personalizatio.api.entities.search.SearchFullEntity
import com.personalizatio.api.entities.search.SearchInstantEntity
import com.personalizatio.api.params.SearchParams

interface SearchManager {

    /**
     * Search full
     *
     * @param query Search phrase
     * @param onGetSearchFull Callback for search full
     * @param onError Callback for error
     */
    fun searchFull(
        query: String,
        onGetSearchFull: (SearchFullEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Search full
     *
     * @param query Search phrase
     * @param listener Callback
     */
    fun searchFull(
        query: String,
        listener: OnApiCallbackListener
    )

    /**
     * Search full
     *
     * @param query Search phrase
     * @param searchParams SearchParams
     * @param onGetSearchFull Callback for search full
     * @param onError Callback for error
     */
    fun searchFull(
        query: String,
        searchParams: SearchParams,
        onGetSearchFull: (SearchFullEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Search full
     *
     * @param query Search phrase
     * @param searchParams SearchParams
     * @param listener Callback
     */
    fun searchFull(
        query: String,
        searchParams: SearchParams,
        listener: OnApiCallbackListener
    )

    /**
     * Search instant
     *
     * @param query Search phrase
     * @param onGetSearchInstant Callback for search instant
     * @param onError Callback for error
     */
    fun searchInstant(
        query: String,
        onGetSearchInstant: (SearchInstantEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Search instant
     *
     * @param query Search phrase
     * @param listener Callback
     */
    fun searchInstant(
        query: String,
        listener: OnApiCallbackListener
    )

    /**
     * Search instant
     *
     * @param query Search phrase
     * @param searchParams SearchParams
     * @param onGetSearchInstant Callback for search instant
     * @param onError Callback for error
     */
    fun searchInstant(
        query: String,
        searchParams: SearchParams,
        onGetSearchInstant: (SearchInstantEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Search instant
     *
     * @param query Search phrase
     * @param searchParams SearchParams
     * @param listener Callback
     */
    fun searchInstant(
        query: String,
        searchParams: SearchParams,
        listener: OnApiCallbackListener
    )

    /**
     * Search blank
     *
     * @param listener Callback
     */
    fun searchBlank(
        listener: OnApiCallbackListener
    )

    /**
     * Search blank
     *
     * @param onGetSearchBlank Callback for search blank
     * @param onError Callback for error
     */
    fun searchBlank(
        onGetSearchBlank: (SearchBlankEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )
}
