package com.personalization.ui.widgets

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.personalization.SDK
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.components.PersonalizationProductImage
import com.personalization.ui.components.PersonalizationProductsBlock

/**
 * Управление блоком рекомендаций из Compose: то же, что [PersonalizationRecommenderBlock.reload].
 * Передаётся в [PersonalizationRecommenderBlockWidget]; без показанного блока ничего не делает.
 */
class PersonalizationRecommenderBlockController {

    internal var view: PersonalizationRecommenderBlock? = null

    /** Загрузить блок заново — мимо кэша. */
    fun reload() {
        view?.reload()
    }
}

/**
 * Compose-обёртка над [PersonalizationRecommenderBlock] — те же параметры, что у вьюхи.
 * Источник — ровно один: [code] (рекомендации) или [collectionId] (коллекция).
 * Пока блок грузится, пуст или не загрузился, он нулевой высоты.
 *
 * Compose у SDK опциональный (`compileOnly`), как у [PersonalizationStoriesWidget]: хост
 * подключает его сам.
 *
 * ```kotlin
 * PersonalizationRecommenderBlockWidget(code = "BLOCK_CODE", onProductClick = { open(it) })
 * ```
 */
@Composable
fun PersonalizationRecommenderBlockWidget(
    modifier: Modifier = Modifier,
    code: String? = null,
    collectionId: String? = null,
    itemId: String? = null,
    categoryId: String? = null,
    locations: String? = null,
    shopId: String? = null,
    sdk: SDK? = null,
    controller: PersonalizationRecommenderBlockController? = null,
    layout: PersonalizationProductsBlock.Layout = PersonalizationProductsBlock.Layout.CAROUSEL,
    title: CharSequence? = null,
    limit: Int? = null,
    minProducts: Int = 1,
    imageAspect: PersonalizationProductImage.Aspect = PersonalizationProductImage.Aspect.SQUARE,
    actionText: CharSequence? = null,
    showAllText: CharSequence? = null,
    onShowAll: (() -> Unit)? = null,
    showDots: Boolean = false,
    showRating: Boolean = false,
    imageLoader: ((ImageView, String) -> Unit)? = null,
    onProductClick: ((PersonalizationProduct) -> Unit)? = null,
    onProductAction: ((PersonalizationProduct) -> Unit)? = null,
    onLoaded: ((count: Int) -> Unit)? = null,
    onError: ((code: Int, message: String?) -> Unit)? = null
) {
    val currentOnLoaded = rememberUpdatedState(onLoaded)
    val currentOnError = rememberUpdatedState(onError)
    // Магазин и SDK задают, откуда грузить, — их смена пересоздаёт вьюху. Источник и его
    // параметры вьюха меняет сама, с новой загрузкой.
    key(shopId, sdk) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                PersonalizationRecommenderBlock(context).apply {
                    this.shopId = shopId
                    // Источник — до первой загрузки: блок из кэша появляется сразу, без запроса.
                    this.code = code
                    this.collectionId = collectionId
                    this.itemId = itemId
                    this.categoryId = categoryId
                    this.locations = locations
                    this.onLoaded = { count -> currentOnLoaded.value?.invoke(count) }
                    this.onError = { errorCode, message -> currentOnError.value?.invoke(errorCode, message) }
                    sdk?.let(::attach)
                }
            },
            update = { view ->
                view.code = code
                view.collectionId = collectionId
                view.itemId = itemId
                view.categoryId = categoryId
                view.locations = locations
                view.layout = layout
                view.title = title
                view.limit = limit
                view.minProducts = minProducts
                view.imageAspect = imageAspect
                view.actionText = actionText
                view.showAllText = showAllText
                view.onShowAll = onShowAll
                view.showDots = showDots
                view.showRating = showRating
                view.imageLoader = imageLoader
                view.onProductClick = onProductClick
                view.onProductAction = onProductAction
                controller?.view = view
            },
            onRelease = { view -> if (controller?.view === view) controller.view = null }
        )
    }
}
