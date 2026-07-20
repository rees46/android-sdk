package com.personalization.stories.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.personalization.OnClickListener
import com.personalization.SDK
import com.personalization.stories.views.StoriesView

/**
 * Compose wrapper around the View-based [StoriesView].
 *
 * [StoriesView] is normally declared in XML, where `app:code` supplies the block code and the host
 * then hands the view to `SDK.initializeStoriesView`. This wrapper does both from Compose, so a
 * host only needs:
 *
 * ```kotlin
 * StoriesWidget(sdk = sdk, code = stringResource(R.string.stories_code))
 * ```
 *
 * Compose is an optional, `compileOnly` dependency of the SDK — see the note in `build.gradle`.
 * Calling this function requires the host to have Compose on its own classpath.
 *
 * @param sdk the initialized SDK instance the block is loaded through.
 * @param code the stories block code configured for the shop.
 * @param needOpeningWebView whether the SDK opens tapped links itself. Mirrors the
 *   `app:need_opening_web_view` attribute; pass `false` to route every link in the host.
 * @param productBannerTapDefaultMessage toast shown when a promocode is copied.
 * @param onClickListener receives taps on links and products. Returning `false` from
 *   [OnClickListener.onClick] keeps the SDK from opening that url, so a host that routes it
 *   itself does not navigate twice.
 */
@Composable
fun StoriesWidget(
    sdk: SDK,
    code: String,
    modifier: Modifier = Modifier,
    needOpeningWebView: Boolean = true,
    productBannerTapDefaultMessage: String = DEFAULT_PROMOCODE_COPIED_MESSAGE,
    onClickListener: OnClickListener? = null
) {
    val currentClickListener = rememberUpdatedState(onClickListener)

    // The code and the web-view flag are constructor arguments of StoriesView and the block is
    // loaded once during initialize, so changing either has to rebuild the view rather than update
    // it in place.
    key(code, needOpeningWebView, productBannerTapDefaultMessage) {
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
                    sdk.initializeStoriesView(this)
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
