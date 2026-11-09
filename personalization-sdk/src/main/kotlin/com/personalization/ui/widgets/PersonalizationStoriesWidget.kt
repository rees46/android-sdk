package com.personalization.ui.widgets

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.personalization.InternalPersonalizationV3Api
import com.personalization.OnClickListener
import com.personalization.SDK

/**
 * Управление сторис из Compose (B8): то же, что методы [PersonalizationStories].
 * Передаётся в [PersonalizationStoriesWidget]. Открытие до показа виджета ждёт его, как
 * ждёт загрузки блока (F11); пауза и остальное без виджета ничего не делают.
 */
class PersonalizationStoriesController {

    internal var view: PersonalizationStories? = null
        set(value) {
            field = value
            if (value != null) pendingOpen?.also { pendingOpen = null }?.invoke(value)
        }

    /** Последнее открытие, вызванное до показа виджета. */
    private var pendingOpen: ((PersonalizationStories) -> Unit)? = null

    fun open(index: Int) = openWhenShown { it.open(index) }

    fun openStory(storyId: Int) = openWhenShown { it.openStory(storyId) }

    /** То же по id строкой (F12). */
    fun openStory(storyId: String) = openWhenShown { it.openStory(storyId) }

    private fun openWhenShown(action: (PersonalizationStories) -> Unit) {
        val target = view
        if (target != null) action(target) else pendingOpen = action
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
    PersonalizationStoriesWidgetContent(
        code, modifier, shopId, sdk, controller, onClickListener, onError, imageLoader,
        promoCaption, promoCopiedText, productButtonText, showProductsText, hideProductsText,
        retryText, closeLabel, soundOnLabel, soundOffLabel
    )
}

/**
 * [PersonalizationStoriesWidget] на API v3 (за шлюзом v3): [apiV3Domain], [shareLabel],
 * [shareUrlTransform], тексты опросов и [pollsTransport] — как у [PersonalizationStories].
 */
@InternalPersonalizationV3Api
@JvmSynthetic
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
    soundOffLabel: CharSequence? = null,
    shareLabel: CharSequence? = null,
    apiV3Domain: String? = null,
    shareUrlTransform: ((String) -> String)? = null,
    pollReanswerText: CharSequence? = null,
    pollErrorText: CharSequence? = null,
    pollSubmitText: CharSequence? = null,
    pollsTransport: StoriesPollsTransport? = null
) {
    PersonalizationStoriesWidgetContent(
        code, modifier, shopId, sdk, controller, onClickListener, onError, imageLoader,
        promoCaption, promoCopiedText, productButtonText, showProductsText, hideProductsText,
        retryText, closeLabel, soundOnLabel, soundOffLabel,
        shareLabel, apiV3Domain, shareUrlTransform, pollReanswerText, pollErrorText, pollSubmitText, pollsTransport
    )
}

/** Оба виджета; без параметров v3 — как вьюха без них. */
@Composable
private fun PersonalizationStoriesWidgetContent(
    code: String,
    modifier: Modifier,
    shopId: String?,
    sdk: SDK?,
    controller: PersonalizationStoriesController?,
    onClickListener: OnClickListener?,
    onError: ((code: Int, message: String?) -> Unit)?,
    imageLoader: ((ImageView, String) -> Unit)?,
    promoCaption: CharSequence?,
    promoCopiedText: CharSequence?,
    productButtonText: CharSequence?,
    showProductsText: CharSequence?,
    hideProductsText: CharSequence?,
    retryText: CharSequence?,
    closeLabel: CharSequence?,
    soundOnLabel: CharSequence?,
    soundOffLabel: CharSequence?,
    shareLabel: CharSequence? = null,
    apiV3Domain: String? = null,
    shareUrlTransform: ((String) -> String)? = null,
    pollReanswerText: CharSequence? = null,
    pollErrorText: CharSequence? = null,
    pollSubmitText: CharSequence? = null,
    pollsTransport: StoriesPollsTransport? = null
) {
    val currentOnError = rememberUpdatedState(onError)
    // Код, магазин, SDK и хост v3 задают, какой блок грузить, — их смена пересоздаёт вьюху.
    key(code, shopId, sdk, apiV3Domain) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                PersonalizationStories(context).apply {
                    this.code = code
                    this.shopId = shopId
                    this.apiV3Domain = apiV3Domain
                    this.onError = { errorCode, message -> currentOnError.value?.invoke(errorCode, message) }
                    sdk?.let(::attach)
                }
            },
            update = { view ->
                val defaults = StoriesTexts.defaults(view.context)
                view.onClickListener = onClickListener
                view.imageLoader = imageLoader
                view.shareUrlTransform = shareUrlTransform
                view.pollsTransport = pollsTransport
                view.promoCaption = promoCaption ?: defaults.promoCaption
                view.promoCopiedText = promoCopiedText ?: defaults.promoCopiedText
                view.productButtonText = productButtonText ?: defaults.productButtonText
                view.showProductsText = showProductsText ?: defaults.showProductsText
                view.hideProductsText = hideProductsText ?: defaults.hideProductsText
                view.retryText = retryText ?: defaults.retryText
                view.closeLabel = closeLabel ?: defaults.closeLabel
                view.soundOnLabel = soundOnLabel ?: defaults.soundOnLabel
                view.soundOffLabel = soundOffLabel ?: defaults.soundOffLabel
                view.shareLabel = shareLabel ?: defaults.shareLabel
                view.pollReanswerText = pollReanswerText ?: defaults.pollReanswerText
                view.pollErrorText = pollErrorText ?: defaults.pollErrorText
                view.pollSubmitText = pollSubmitText ?: defaults.pollSubmitText
                controller?.view = view
            },
            onRelease = { view -> if (controller?.view === view) controller.view = null }
        )
    }
}
