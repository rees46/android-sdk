package com.personalization.ui.widgets

import com.personalization.Params
import com.personalization.SDK
import com.personalization.api.responses.collection.CollectionResponse
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.recommendation.GetExtendedRecommendationResponse

/**
 * Ответ блока рекомендаций: заголовок (у коллекции его нет) и товары API.
 * В общем кэше лежит он, а не карточки: текст кнопки карточки у каждого блока свой.
 */
internal class RecommenderAnswer(val title: String?, val products: List<Product>)

/** Источник блока рекомендаций — ровно один: блок по коду или коллекция. */
internal sealed class RecommenderSource {

    /** Ключ общего кэша: магазин, вид запроса и всё, от чего зависит ответ. */
    abstract fun key(shopId: String): ResponseCache.Key

    abstract fun request(sdk: SDK): LoadRequest<RecommenderAnswer>

    /**
     * Блок рекомендаций `recommend/{code}` с товарами целиком (`extended`).
     * Параметры — те же, что хост кладёт в [Params] для `getExtendedRecommendation`:
     * товар (`item`), категория (`category_id`), локации (`locations`).
     */
    data class Recommendation(
        val code: String,
        val itemId: String? = null,
        val categoryId: String? = null,
        val locations: String? = null
    ) : RecommenderSource() {

        override fun key(shopId: String) = ResponseCache.Key(
            shopId,
            KIND_RECOMMEND,
            mapOf("code" to code, "item" to itemId, "category" to categoryId, "locations" to locations)
        )

        override fun request(sdk: SDK): LoadRequest<RecommenderAnswer> = { onSuccess, onError ->
            val params = Params()
            itemId?.let { params.put(Params.Parameter.ITEM, it) }
            categoryId?.let { params.put(Params.Parameter.CATEGORY_ID, it) }
            locations?.let { params.put(Params.Parameter.LOCATIONS, it) }
            sdk.recommendationManager.getExtendedRecommendation(
                recommenderCode = code,
                params = params,
                onGetExtendedRecommendation = { onSuccess(it.toAnswer()) },
                onError = onError
            )
        }
    }

    /** Коллекция `collection/{id}`: товары без заголовка. */
    data class Collection(val collectionId: String) : RecommenderSource() {

        override fun key(shopId: String) =
            ResponseCache.Key(shopId, KIND_COLLECTION, mapOf("id" to collectionId))

        override fun request(sdk: SDK): LoadRequest<RecommenderAnswer> = { onSuccess, onError ->
            sdk.collectionManager.getCollection(
                collectionId = collectionId,
                onSuccess = { onSuccess(it.toAnswer()) },
                onError = onError
            )
        }
    }

    companion object {
        const val KIND_RECOMMEND = "recommend"
        const val KIND_COLLECTION = "collection"
    }
}

// Gson кладёт null в поля без значения даже при non-null типе — читаем через nullable копии.

internal fun GetExtendedRecommendationResponse.toAnswer(): RecommenderAnswer {
    val heading: String? = title
    val items: List<Product?>? = products
    return RecommenderAnswer(heading?.takeIf { it.isNotBlank() }, items.orEmpty().filterNotNull())
}

internal fun CollectionResponse.toAnswer(): RecommenderAnswer {
    val items: List<Product?>? = products
    return RecommenderAnswer(null, items.orEmpty().filterNotNull())
}
