package com.personalizatio.api.managers

import com.personalizatio.Params
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.entities.categories.categories.CategoriesEntity
import com.personalizatio.api.entities.categories.category.CategoryEntity

interface CategoriesManager {

    /**
     * Get categories
     *
     * @param listener Callback
     */
    fun getCategories(
        listener: OnApiCallbackListener
    )

    /**
     * Get categories
     *
     * @param params Params
     * @param listener Callback
     */
    fun getCategories(
        params: Params,
        listener: OnApiCallbackListener
    )

    /**
     * Get categories
     *
     * @param onGetCategories Callback for get categories
     * @param onError Callback for error
     */
    fun getCategories(
        onGetCategories: (CategoriesEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Get categories
     *
     * @param params Params
     * @param onGetCategories Callback for get categories
     * @param onError Callback for error
     */
    fun getCategories(
        params: Params,
        onGetCategories: (CategoriesEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Get category by id
     *
     * @param categoryId External ID
     * @param listener Callback
     */
    fun getCategory(
        categoryId: String,
        listener: OnApiCallbackListener
    )

    /**
     * Get category by id
     *
     * @param categoryId External ID
     * @param params Params
     * @param listener Callback
     */
    fun getCategory(
        categoryId: String,
        params: Params,
        listener: OnApiCallbackListener
    )

    /**
     * Get category by id
     *
     * @param categoryId External ID
     * @param onGetCategory Callback for get category
     * @param onError Callback for error
     */
    fun getCategory(
        categoryId: String,
        onGetCategory: (CategoryEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Get category by id
     *
     * @param categoryId External ID
     * @param params Params
     * @param onGetCategory Callback for get category
     * @param onError Callback for error
     */
    fun getCategory(
        categoryId: String,
        params: Params,
        onGetCategory: (CategoryEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )
}
