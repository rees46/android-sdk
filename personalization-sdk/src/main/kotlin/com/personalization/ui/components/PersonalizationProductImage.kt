package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Изображение товара с фиксированной пропорцией.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Image (88:93), фрейм Product (88:94):
 * три пропорции — 1:1, 4:3, 3:4. Ширину задаёт разметка, высота считается сама.
 *
 * Картинку компонент не грузит: сеть — не его дело. Наружу отдан [imageView],
 * в который хост кладёт изображение своим загрузчиком (в SDK уже есть Glide).
 * До загрузки виден плейсхолдер цвета Background/Card — в макете заливка
 * плейсхолдера к переменной не привязана, взят ближайший токен.
 */
@InternalPersonalizationUiApi
class PersonalizationProductImage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Aspect(val width: Int, val height: Int) {
        SQUARE(1, 1),
        LANDSCAPE(4, 3),
        PORTRAIT(3, 4)
    }

    /** Сюда хост загружает изображение. */
    val imageView = AppCompatImageView(context)

    var aspect: Aspect = Aspect.SQUARE
        set(value) {
            field = value
            requestLayout()
        }

    init {
        // Заглушка до загрузки. В макете она нетокенный серый, здесь — Neutral 50:
        // полупрозрачная, поэтому видна и на карточке, и прямо на фоне экрана.
        setBackgroundColor(
            PersonalizationTheme.color(context, R.color.personalization_neutral_50)
        )
        imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = width * aspect.height / aspect.width
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val exactHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        super.onMeasure(exactWidth, exactHeight)
    }
}
