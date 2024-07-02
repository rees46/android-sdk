package com.personalizatio.api.managers

import com.personalizatio.Params
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.listeners.OnCategoriesListener

interface CategoriesManager {

    /**
     * Get categories
     *
     * @param listener Callback
     */
    fun getCategories(listener: OnApiCallbackListener)

    /**
     * Get categories
     *
     * @param params Params
     * @param listener Callback
     */
    fun getCategories(params: Params, listener: OnApiCallbackListener)

    /**
     * Get categories
     *
     * @param listener Callback
     */
    fun getCategories(listener: OnCategoriesListener)

    /**
     * Get categories
     *
     * @param params Params
     * @param listener Callback
     */
    fun getCategories(params: Params, listener: OnCategoriesListener)

    /**
     * Get category by id
     *
     * @param categoryId External ID
     * @param listener Callback
     */
    fun getCategory(categoryId: String, listener: OnApiCallbackListener)

    /**
     * Get category by id
     *
     * @param categoryId External ID
     * @param params Params
     * @param listener Callback
     */
    fun getCategory(categoryId: String, params: Params,listener: OnApiCallbackListener)

    /**
     * Get category by id
     *
     * @param categoryId External ID
     * @param listener Callback
     */
    fun getCategory(categoryId: String, listener: OnCategoriesListener)

    /**
     * Get category by id
     *
     * @param categoryId External ID
     * @param params Params
     * @param listener Callback
     */
    fun getCategory(categoryId: String, params: Params,listener: OnCategoriesListener)
}
