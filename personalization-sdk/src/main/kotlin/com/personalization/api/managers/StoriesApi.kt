package com.personalization.api.managers

import com.personalization.api.models.stories.StoriesBlock
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * The stories blocks — the `stories` namespace: `sdk.stories.get(code)`. The events of a story are
 * [TrackingApi.storyView] and [TrackingApi.storyClick].
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 */
@BridgeApi
interface StoriesApi {

    /**
     * The stories block [code] (`GET /stories/{code}`) for this device: its stories, their slides and the
     * slides' elements. A block without stories, a paused one among them, is a block with an empty list.
     *
     * @throws SdkException with [SdkError.InvalidArgument] for an empty [code], [SdkError.NotInitialized]
     * (the legacy core, an instance not initialized or released), [SdkError.ShopBlocked],
     * [SdkError.Network], [SdkError.Timeout], [SdkError.Http] (`404` for an unknown block, `402` when the
     * shop does not pay for stories, `400` for a device the server does not know), [SdkError.Decoding] or
     * [SdkError.Cancelled] (`release()`).
     */
    suspend fun get(code: String): StoriesBlock

    /** [get] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun get(code: String, listener: CompletionListener<StoriesBlock>)
}
