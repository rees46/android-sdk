package com.personalization.ui.widgets

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.personalization.OnClickListener
import com.personalization.SDK
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Управление сторис из Compose (B8): то же, что методы [PersonalizationStories].
 * Передаётся в [PersonalizationStoriesWidget]; до показа виджета вызовы ничего не делают.
 */
@InternalPersonalizationUiApi
class PersonalizationStoriesController {

    internal var view: PersonalizationStories? = null

    fun open(index: Int) {
        view?.open(index)
    }

    fun openStory(storyId: Int) {
        view?.openStory(storyId)
    }

    fun pause() {
        view?.pause()
    }

    fun resume() {
        view?.resume()
    }

    fun reload() {
        view?.reload()
    }
}

/**
 * Compose-обёртка над [PersonalizationStories] — те же параметры, что у вьюхи.
 * Compose у SDK опциональный (`compileOnly`), как у старого `StoriesWidget`: хост
 * подключает его сам. Тексты `null` — по умолчанию из ресурсов SDK.
 *
 * ```kotlin
 * PersonalizationStoriesWidget(code = "STORIES_CODE", onClickListener = listener)
 * ```
 */
@InternalPersonalizationUiApi
@Composable
fun PersonalizationStoriesWidget(
    code: String,
    modifier: Modifier = Modifier,
    shopId: String? = null,
    sdk: SDK? = null,
    controller: PersonalizationStoriesController? = null,
    onClickListener: OnClickListener? = null,
    onError: ((code: Int, message: String?) -> Unit)? = null,
    imageLoader: ((ImageView, String) -> Unit)? = null,
    promoCaption: CharSequence? = null,
    promoCopiedText: CharSequence? = null,
    productButtonText: CharSequence? = null,
    showProductsText: CharSequence? = null,
    hideProductsText: CharSequence? = null,
    retryText: CharSequence? = null,
    closeLabel: CharSequence? = null,
    soundOnLabel: CharSequence? = null,
    soundOffLabel: CharSequence? = null
) {
    val currentOnError = rememberUpdatedState(onError)
    // Код, магазин и SDK задают, какой блок грузить, — их смена пересоздаёт вьюху.
    key(code, shopId, sdk) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                PersonalizationStories(context).apply {
                    this.code = code
                    this.shopId = shopId
                    this.onError = { errorCode, message -> currentOnError.value?.invoke(errorCode, message) }
                    sdk?.let(::attach)
                }
            },
            update = { view ->
                val defaults = StoriesTexts.defaults(view.context)
                view.onClickListener = onClickListener
                view.imageLoader = imageLoader
                view.promoCaption = promoCaption ?: defaults.promoCaption
                view.promoCopiedText = promoCopiedText ?: defaults.promoCopiedText
                view.productButtonText = productButtonText ?: defaults.productButtonText
                view.showProductsText = showProductsText ?: defaults.showProductsText
                view.hideProductsText = hideProductsText ?: defaults.hideProductsText
                view.retryText = retryText ?: defaults.retryText
                view.closeLabel = closeLabel ?: defaults.closeLabel
                view.soundOnLabel = soundOnLabel ?: defaults.soundOnLabel
                view.soundOffLabel = soundOffLabel ?: defaults.soundOffLabel
                controller?.view = view
            },
            onRelease = { view -> if (controller?.view === view) controller.view = null }
        )
    }
}
