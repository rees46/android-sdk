package com.personalization.features.notification.presentation.helpers

import android.content.Context
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.FutureTarget
import com.personalization.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object NotificationImageHelper {

    fun displayImages(
        customView: RemoteViews,
        images: List<Bitmap>?,
        currentIndex: Int,
        hasError: Boolean = false
    ) {
        if (hasError) {
            customView.setViewVisibility(R.id.loadingProgressBar, View.GONE)
            customView.setViewVisibility(R.id.retryButton, View.VISIBLE)
            customView.setImageViewResource(R.id.largeImage, R.drawable.image_error)
            customView.setViewVisibility(R.id.largeImage, View.VISIBLE)
        } else if (!images.isNullOrEmpty() && currentIndex in images.indices) {
            customView.setViewVisibility(R.id.loadingProgressBar, View.GONE)
            customView.setViewVisibility(R.id.retryButton, View.GONE)
            customView.setImageViewBitmap(R.id.smallImage, images[currentIndex])
            customView.setImageViewBitmap(R.id.largeImage, images[currentIndex])
            customView.setViewVisibility(R.id.actionContainer, View.VISIBLE)
            customView.setViewVisibility(R.id.smallImage, View.VISIBLE)
            customView.setViewVisibility(R.id.largeImage, View.VISIBLE)
        } else {
            customView.setViewVisibility(R.id.actionContainer, View.GONE)
            customView.setViewVisibility(R.id.expandArrow, View.GONE)
            customView.setViewVisibility(R.id.smallImage, View.GONE)
        }
    }

    /**
     * Loads the push images through Glide instead of decoding them by hand.
     *
     * Downloading with `URL.openStream()` and decoding with `BitmapFactory` used to allocate the
     * bitmap at the source resolution — a 4000x3000 product photo costs ~46 MB of heap for a view
     * that is 150dp tall — which is what Google Play reports as "manually downloading and decoding
     * images from the network". Glide downsamples straight into the notification's box, caches the
     * download (the notification is rebuilt on every arrow tap, so the same URLs are re-requested)
     * and manages the bitmap pool.
     *
     * @return the loaded bitmaps and whether at least one URL failed.
     */
    suspend fun loadBitmaps(
        context: Context,
        urls: String?
    ): Pair<List<Bitmap>, Boolean> = withContext(Dispatchers.IO) {
        val urlList = parseUrls(urls = urls)

        if (urlList.isEmpty()) return@withContext Pair(emptyList(), true)

        val applicationContext = context.applicationContext
        val (width, height) = resolveTargetSize(context = applicationContext)

        val bitmaps = coroutineScope {
            urlList.map { url ->
                async {
                    loadBitmap(
                        context = applicationContext,
                        url = url,
                        width = width,
                        height = height
                    )
                }
            }.awaitAll()
        }

        Pair(bitmaps.filterNotNull(), bitmaps.any { bitmap -> bitmap == null })
    }

    /**
     * The payload carries the images comma-separated and pads them with spaces often enough that
     * an untrimmed entry would be requested verbatim — and fail.
     */
    internal fun parseUrls(urls: String?): List<String> = urls
        ?.split(IMAGE_URL_SEPARATOR)
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()

    private fun loadBitmap(
        context: Context,
        url: String,
        width: Int,
        height: Int
    ): Bitmap? {
        var target: FutureTarget<Bitmap>? = null
        return try {
            target = Glide.with(context)
                .asBitmap()
                // The notification shows the image with centerCrop, so decode exactly the box that
                // ends up on screen: no larger (wasted heap, and the bitmap travels to the system
                // UI through a Binder transaction) and no smaller (it would be upscaled and soft).
                .downsample(DownsampleStrategy.CENTER_OUTSIDE)
                .centerCrop()
                // A Config.HARDWARE bitmap has no pixel data to copy out of and cannot be handed
                // to RemoteViews; keep the decode in software.
                .disallowHardwareConfig()
                .load(url)
                .submit(width, height)
            // A push must not keep the service alive indefinitely if the CDN stalls.
            target.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // Glide hands back a pooled bitmap that clear() below may hand to the next decode,
                // while this one is still referenced by the posted notification. The copy is the
                // price of releasing the request instead of leaking it in the request manager.
                ?.let { bitmap -> bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) }
        } catch (_: Exception) {
            // Failure, timeout and cancellation all mean the same to the caller: no image, and a
            // retry button in the notification.
            null
        } finally {
            target?.let { Glide.with(context).clear(it) }
        }
    }

    /**
     * The box of the large image in `custom_notification.xml`: the full notification width by
     * [LARGE_IMAGE_HEIGHT_DP]. Capped at [MAX_IMAGE_WIDTH_PX] (keeping the aspect ratio, so the
     * cropped region stays the same) — beyond that the extra pixels are invisible in the shade.
     */
    private fun resolveTargetSize(context: Context): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.takeIf { it > 0 } ?: MAX_IMAGE_WIDTH_PX
        val height = TypedValue.applyDimension(
            /* unit = */ TypedValue.COMPLEX_UNIT_DIP,
            /* value = */ LARGE_IMAGE_HEIGHT_DP,
            /* metrics = */ metrics
        ).toInt().coerceAtLeast(1)

        if (width <= MAX_IMAGE_WIDTH_PX) return Pair(width, height)

        val scale = MAX_IMAGE_WIDTH_PX.toFloat() / width
        return Pair(MAX_IMAGE_WIDTH_PX, (height * scale).toInt().coerceAtLeast(1))
    }

    private const val IMAGE_URL_SEPARATOR = ","
    private const val LARGE_IMAGE_HEIGHT_DP = 150f
    private const val MAX_IMAGE_WIDTH_PX = 1080
    private const val LOAD_TIMEOUT_SECONDS = 15L
}
