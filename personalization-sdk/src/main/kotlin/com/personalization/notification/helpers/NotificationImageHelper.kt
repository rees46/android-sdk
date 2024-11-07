package com.personalization.notification.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import com.personalization.R
import java.io.IOException
import java.io.InputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotificationImageHelper {

    fun displayImages(
        customView: RemoteViews,
        images: List<Bitmap>?,
        currentIndex: Int
    ) {
        if (!images.isNullOrEmpty() && currentIndex in images.indices) {
            customView.setViewVisibility(R.id.smallImage, View.VISIBLE)
            customView.setViewVisibility(R.id.largeImage, View.VISIBLE)
            customView.setImageViewBitmap(R.id.smallImage, images[currentIndex])
            customView.setImageViewBitmap(R.id.largeImage, images[currentIndex])
            customView.setImageViewResource(R.id.expandArrow, R.drawable.ic_arrow_open)
            customView.setViewVisibility(R.id.actionContainer, View.VISIBLE)
        } else {
            customView.setViewVisibility(R.id.smallImage, View.GONE)
            customView.setViewVisibility(R.id.expandArrow, View.GONE)
            customView.setViewVisibility(R.id.actionContainer, View.GONE)
        }
    }

    suspend fun loadBitmaps(urls: String?): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        if (urls != null) {
            val urlArray = urls.split(",").toTypedArray()
            withContext(Dispatchers.IO) {
                for (url in urlArray) {
                    try {
                        val inputStream: InputStream = URL(url).openStream()
                        bitmaps.add(BitmapFactory.decodeStream(inputStream))
                    } catch (ioException: IOException) {
                        // Handle error
                    }
                }
            }
        }
        return bitmaps
    }
}