package com.personalizatio.categories

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.listeners.OnCategoriesListener
import com.personalizatio.api.managers.CategoriesManager
import com.personalizatio.entities.categories.categories.CategoriesEntity
import com.personalizatio.entities.categories.category.CategoryEntity
import org.json.JSONArray
import org.json.JSONObject

internal class CategoriesManagerImpl(val sdk: SDK) : CategoriesManager {

    override fun getCategories(listener: OnCategoriesListener) {
        getCategories(Params(), listener)
    }

    override fun getCategories(params: Params, listener: OnCategoriesListener) {
        getCategories(params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONArray) {
                val categoriesEntity = Gson().fromJson(response.toString(), CategoriesEntity::class.java)
                listener.onGetCategories(categoriesEntity)
            }
        })
    }

    override fun getCategories(listener: OnApiCallbackListener) {
        getCategories(Params(), listener)
    }

    override fun getCategories(params: Params, listener: OnApiCallbackListener) {
        sdk.getSecretAsync(GET_CATEGORIES_INFO_REQUEST, params.build(), listener)
    }

    override fun getCategory(categoryId: String, listener: OnCategoriesListener) {
        getCategory(categoryId, Params(), listener)
    }

    override fun getCategory(categoryId: String, params: Params, listener: OnCategoriesListener) {
        getCategory(categoryId, params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                val categoryEntity = Gson().fromJson(response.toString(), CategoryEntity::class.java)
                listener.onGetCategory(categoryEntity)
            }
        })
    }

    override fun getCategory(categoryId: String, listener: OnApiCallbackListener) {
        getCategory(categoryId, Params(), listener)
    }

    override fun getCategory(categoryId: String, params: Params,listener: OnApiCallbackListener) {
        val request = "$GET_CATEGORY_REQUEST/$categoryId"
        sdk.getAsync(request, params.build(), listener)
    }

    companion object {
        const val GET_CATEGORIES_INFO_REQUEST = "products/categories"
        const val GET_CATEGORY_REQUEST = "category"
    }
}
