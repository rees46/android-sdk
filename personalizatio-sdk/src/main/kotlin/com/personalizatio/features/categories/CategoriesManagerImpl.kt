package com.personalizatio.features.categories

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.managers.CategoriesManager
import com.personalizatio.api.managers.NetworkManager
import com.personalizatio.api.entities.categories.categories.CategoriesEntity
import com.personalizatio.api.entities.categories.category.CategoryEntity
import org.json.JSONArray
import org.json.JSONObject

internal class CategoriesManagerImpl(private val networkManager: NetworkManager) : CategoriesManager {

    override fun getCategories(
        onGetCategories: (CategoriesEntity) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getCategories(Params(), onGetCategories, onError)
    }

    override fun getCategories(
        params: Params,
        onGetCategories: (CategoriesEntity) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getCategories(params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONArray) {
                val categoriesEntity = Gson().fromJson(response.toString(), CategoriesEntity::class.java)
                onGetCategories(categoriesEntity)
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    override fun getCategories(
        listener: OnApiCallbackListener
    ) {
        getCategories(Params(), listener)
    }

    override fun getCategories(
        params: Params,
        listener: OnApiCallbackListener
    ) {
        networkManager.getSecretAsync(GET_CATEGORIES_INFO_REQUEST, params.build(), listener)
    }

    override fun getCategory(
        categoryId: String,
        onGetCategory: (CategoryEntity) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getCategory(categoryId, Params(), onGetCategory, onError)
    }

    override fun getCategory(
        categoryId: String,
        params: Params,
        onGetCategory: (CategoryEntity) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getCategory(categoryId, params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                val categoryEntity = Gson().fromJson(response.toString(), CategoryEntity::class.java)
                onGetCategory(categoryEntity)
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    override fun getCategory(
        categoryId: String,
        listener: OnApiCallbackListener
    ) {
        getCategory(categoryId, Params(), listener)
    }

    override fun getCategory(
        categoryId: String,
        params: Params,
        listener: OnApiCallbackListener
    ) {
        val request = "$GET_CATEGORY_REQUEST/$categoryId"
        networkManager.getAsync(request, params.build(), listener)
    }

    companion object {
        const val GET_CATEGORIES_INFO_REQUEST = "products/categories"
        const val GET_CATEGORY_REQUEST = "category"
    }
}
