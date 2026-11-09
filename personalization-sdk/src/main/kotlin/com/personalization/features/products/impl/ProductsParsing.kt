package com.personalization.features.products.impl

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.products.ProductsListResponse
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the raw answers of `products` and `products/get` into models for the typed
 * [com.personalization.api.managers.ProductsManager] methods.
 *
 * Shared by the interface defaults, so an implementation that only has the listener-based
 * methods gets the typed ones for free.
 */
internal object ProductsParsing {

    /** The answer came but could not be read as the expected model. */
    const val PARSE_ERROR_CODE = -1

    private const val PRODUCTS_KEY = "products"

    /**
     * A listing must carry `products`: a page that is not JSON reaches the listener as an
     * empty object and is an error, not an empty listing.
     */
    fun listListener(
        onSuccess: (ProductsListResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ): OnApiCallbackListener = parsingListener(
        method = ProductsManagerImpl.GET_PRODUCT_LIST_REQUEST,
        parse = { json ->
            if (json.has(PRODUCTS_KEY)) Gson().fromJson(json.toString(), ProductsListResponse::class.java) else null
        },
        onSuccess = onSuccess,
        onError = onError
    )

    /** Gson leaves non-null fields empty when the answer lacks them: no id — no product. */
    fun productListener(
        onSuccess: (Product) -> Unit,
        onError: (Int, String?) -> Unit
    ): OnApiCallbackListener = parsingListener(
        method = ProductsManagerImpl.GET_PRODUCT_INFO_REQUEST,
        parse = { json ->
            Gson().fromJson(json.toString(), Product::class.java)?.takeIf { product ->
                val id: String? = product.id
                !id.isNullOrEmpty()
            }
        },
        onSuccess = onSuccess,
        onError = onError
    )

    /**
     * A malformed answer, one [parse] rejects with `null`, or an array instead of an object
     * goes to [onError] instead of throwing on the network thread or never answering.
     */
    private fun <T : Any> parsingListener(
        method: String,
        parse: (JSONObject) -> T?,
        onSuccess: (T) -> Unit,
        onError: (Int, String?) -> Unit
    ): OnApiCallbackListener = object : OnApiCallbackListener() {
        override fun onSuccess(response: JSONObject?) {
            val parsed = try {
                response?.let(parse)
            } catch (e: JsonParseException) {
                onError(PARSE_ERROR_CODE, "$method: ${e.message}")
                return
            }
            if (parsed != null) onSuccess(parsed) else onError(PARSE_ERROR_CODE, "$method: unexpected answer")
        }

        override fun onSuccess(response: JSONArray) {
            onError(PARSE_ERROR_CODE, "$method: an array instead of an object")
        }

        override fun onError(code: Int, msg: String?) {
            onError(code, msg)
        }
    }
}
