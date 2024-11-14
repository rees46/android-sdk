package com.personalization.features.notification.presentation.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import com.personalization.R
import java.io.InputStream
import java.net.URL
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

    suspend fun loadBitmaps(urls: String?): Pair<List<Bitmap>, Boolean> {

        return withContext(Dispatchers.IO) {

            if (urls == null) return@withContext Pair(emptyList(), true)

            val urlArray = urls.split(",").toTypedArray()

            var hasError = false

            val bitmaps: List<Bitmap> = coroutineScope {
                urlArray.mapIndexed { index, url ->
                    async {
                        try {
                            val inputStream: InputStream = URL(url).openStream()
                            BitmapFactory.decodeStream(inputStream)
                        } catch (exception: Exception) {
                            hasError = true
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            Pair(bitmaps, hasError)
        }
    }
}
