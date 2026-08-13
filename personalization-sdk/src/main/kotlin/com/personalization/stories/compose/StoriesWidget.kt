package com.personalization.stories.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.personalization.Cancellable
import com.personalization.OnClickListener
import com.personalization.Rees46
import com.personalization.SDK
import com.personalization.stories.views.StoriesView

/**
 * Compose wrapper around the View-based [StoriesView].
 *
 * The widget resolves its SDK itself through [Rees46], so a host only needs the block code (and,
 * for a multi-shop app, the shop it belongs to):
 *
 * ```kotlin
 * StoriesWidget(code = stringResource(R.string.stories_code))            // default instance
 * StoriesWidget(code = stringResource(R.string.stories_code), shopId = "SHOP_ID")
 * ```
 *
 * Compose is an optional, `compileOnly` dependency of the SDK — see the note in `build.gradle`.
 * Calling this function requires the host to have Compose on its own classpath.
 *
 * @param code the stories block code configured for the shop.
 * @param shopId the shop whose instance loads the block. `null` uses the single default instance
 *   (see [Rees46.getInstance]); pass an explicit id in a multi-shop app.
 * @param needOpeningWebView whether the SDK opens tapped links itself. Mirrors the
 *   `app:need_opening_web_view` attribute; pass `false` to route every link in the host.
 * @param productBannerTapDefaultMessage toast shown when a promocode is copied.
 * @param onClickListener receives taps on links and products. Returning `false` from
 *   [OnClickListener.onClick] keeps the SDK from opening that url, so a host that routes it
 *   itself does not navigate twice.
 */
@Composable
fun StoriesWidget(
    code: String,
    modifier: Modifier = Modifier,
    shopId: String? = null,
    needOpeningWebView: Boolean = true,
    productBannerTapDefaultMessage: String = DEFAULT_PROMOCODE_COPIED_MESSAGE,
    onClickListener: OnClickListener? = null
) {
    // Resolve the SDK reactively: renders nothing until the instance for this shop is available,
    // then loads. Subscription is cancelled when the widget leaves composition, so it is not leaked.
    val sdk by produceState<SDK?>(initialValue = null, shopId) {
        val handle = try {
            Rees46.awaitInstance(shopId) { value = it }
        } catch (throwable: Throwable) {
            SDK.error("StoriesWidget: cannot resolve an SDK for shopId=$shopId", throwable)
            Cancellable.NOOP
        }
        awaitDispose { handle.cancel() }
    }

    sdk?.let { resolvedSdk ->
        StoriesWidgetContent(
            sdk = resolvedSdk,
            code = code,
            modifier = modifier,
            needOpeningWebView = needOpeningWebView,
            productBannerTapDefaultMessage = productBannerTapDefaultMessage,
            onClickListener = onClickListener
        )
    }
}

/**
 * Compose wrapper that takes an explicit [SDK].
 */
@Deprecated(
    message = "Pass shopId (or omit it for the default instance) instead of an SDK; the widget " +
        "resolves the instance itself via Rees46.awaitInstance.",
    replaceWith = ReplaceWith(
        "StoriesWidget(code = code, modifier = modifier, needOpeningWebView = needOpeningWebView, " +
            "productBannerTapDefaultMessage = productBannerTapDefaultMessage, onClickListener = onClickListener)"
    )
)
@Composable
fun StoriesWidget(
    sdk: SDK,
    code: String,
    modifier: Modifier = Modifier,
    needOpeningWebView: Boolean = true,
    productBannerTapDefaultMessage: String = DEFAULT_PROMOCODE_COPIED_MESSAGE,
    onClickListener: OnClickListener? = null
) {
    StoriesWidgetContent(
        sdk = sdk,
        code = code,
        modifier = modifier,
        needOpeningWebView = needOpeningWebView,
        productBannerTapDefaultMessage = productBannerTapDefaultMessage,
        onClickListener = onClickListener
    )
}

@Composable
private fun StoriesWidgetContent(
    sdk: SDK,
    code: String,
    modifier: Modifier,
    needOpeningWebView: Boolean,
    productBannerTapDefaultMessage: String,
    onClickListener: OnClickListener?
) {
    val currentClickListener = rememberUpdatedState(onClickListener)

    // The code and the web-view flag are constructor arguments of StoriesView and the block is
    // loaded once during initialize, so changing either has to rebuild the view rather than update
    // it in place.
    androidx.compose.runtime.key(code, needOpeningWebView, productBannerTapDefaultMessage) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                StoriesView(
                    context,
                    code,
                    needOpeningWebView,
                    productBannerTapDefaultMessage
                ).apply {
                    itemClickListener = currentClickListener.value
                    attach(sdk)
                }
            },
            update = { view ->
                view.itemClickListener = currentClickListener.value
            },
            onRelease = StoriesView::release
        )
    }
}

private const val DEFAULT_PROMOCODE_COPIED_MESSAGE = "Copied"
