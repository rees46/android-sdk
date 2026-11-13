package com.personalization.inAppNotification.view.component.image

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

class ImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    fun loadImage(url: String) {
        try {
            Glide.with(context)
                .load(url)
                .apply(RequestOptions().centerCrop())
                .listener(
                    object : RequestListener<Drawable> {

                        override fun onLoadFailed(
                            exception: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            exception?.let { failed("The popup image failed to load", it) }
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }
                    }
                )
                .into(this)
        } catch (exception: Exception) {
            failed("The popup image could not be requested", exception)
        }
    }

    /** The class of the failure only: its text carries the image's URL. */
    private fun failed(message: String, throwable: Throwable) =
        SdkLogs.log(LogLevel.WARNING, LogCodes.UI, message, throwable = throwable)
}
