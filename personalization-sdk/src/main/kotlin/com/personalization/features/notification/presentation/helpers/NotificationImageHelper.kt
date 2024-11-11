package com.personalization.features.notification.presentation.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import com.personalization.R
import com.personalization.errors.ResourceLoadError
import java.io.IOException
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
        currentIndex: Int
    ) {
        if (!images.isNullOrEmpty() && currentIndex in images.indices) {
            customView.setImageViewResource(R.id.expandArrow, R.drawable.ic_arrow_open)
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

    suspend fun loadBitmaps(urls: String?): List<Bitmap> {
        return withContext(Dispatchers.IO) {
            if (urls == null) return@withContext emptyList<Bitmap>()

            val urlArray = urls.split(",").toTypedArray()

            coroutineScope {
                urlArray.map { url ->
                    async {
                        try {
                            val inputStream: InputStream = URL(url).openStream()
                            BitmapFactory.decodeStream(inputStream)
                        } catch (ioException: IOException) {
                            ResourceLoadError(
                                tag = this@NotificationImageHelper.javaClass.name,
                                functionName = "loadBitmaps",
                                message = "Loading bitmaps"
                            ).logError()
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
    }
}
