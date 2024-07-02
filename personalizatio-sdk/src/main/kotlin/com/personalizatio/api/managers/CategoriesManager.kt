package com.personalizatio.api.managers

import com.personalizatio.Params
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.listeners.OnCategoriesListener

interface CategoriesManager {

    fun getCategories(listener: OnApiCallbackListener)
    fun getCategories(params: Params, listener: OnApiCallbackListener)

    fun getCategories(listener: OnCategoriesListener)
    fun getCategories(params: Params, listener: OnCategoriesListener)

    fun getCategory(categoryId: String, listener: OnApiCallbackListener)
    fun getCategory(categoryId: String, params: Params,listener: OnApiCallbackListener)

    fun getCategory(categoryId: String, listener: OnCategoriesListener)
    fun getCategory(categoryId: String, params: Params,listener: OnCategoriesListener)
}
