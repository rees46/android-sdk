package com.personalization.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Содержимое товарного и акционного слайда.
 *
 * Источник: Figma, Card/Story (238:6890), виды Product / Promo / Discount / Action:
 * колонка, разнесённая по высоте. Сверху бейдж ([badgeText], Badge LG) и шапка —
 * [title] SemiBold 20 и [subtitle] SemiBold 16 с интерлиньяжем 140% и шагом 8.
 * Посередине — белая квадратная карточка фото со скруглением 12 и отступом 16
 * ([showImage]); квадрат вписывается в свободную высоту. Снизу с шагом 16 —
 * [price], [timer] и кнопка LG Primary ([buttonText]).
 *
 * Цвет текста шапки — Text/Primary контекста: на медиа-слайде он тёмный режим.
 */
@InternalPersonalizationUiApi
class PersonalizationStoryCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val badge = PersonalizationBadge(context)
    val price = PersonalizationStoryPrice(context)
    val timer = PersonalizationStoryTimer(context)
    val button = PersonalizationButton(context)

    /** Фото товара — хост кладёт картинку своим загрузчиком. */
    val imageView: ImageView = AppCompatImageView(context)

    private val header = LinearLayout(context)
    private val titleView = AppCompatTextView(context)
    private val subtitleView = AppCompatTextView(context)
    private val imageArea = FrameLayout(context)
    private val imageCard = StorySquareFrame(context)
    private val spacer = View(context)
    private val bottom = LinearLayout(context)

    var badgeText: CharSequence? = null
        set(value) {
            field = value
            badge.text = value
            applyVisibility()
        }

    var title: CharSequence? = null
        set(value) {
            field = value
            titleView.text = value
            applyVisibility()
        }

    var subtitle: CharSequence? = null
        set(value) {
            field = value
            subtitleView.text = value
            applyVisibility()
        }

    var showImage: Boolean = true
        set(value) {
            field = value
            applyVisibility()
        }

    var showPrice: Boolean = true
        set(value) {
            field = value
            applyVisibility()
        }

    var showTimer: Boolean = false
        set(value) {
            field = value
            applyVisibility()
        }

    /** Подпись кнопки; `null` — без кнопки. */
    var buttonText: CharSequence? = null
        set(value) {
            field = value
            button.text = value
            applyVisibility()
        }

    var onButtonClick: (() -> Unit)? = null

    /** Тап по фото товара; `null` — фото не нажимается, и касания уходят под карточку. */
    var onImageClick: (() -> Unit)? = null
        set(value) {
            field = value
            if (value == null) {
                imageCard.setOnClickListener(null)
                imageCard.isClickable = false
            } else {
                imageCard.setOnClickListener { onImageClick?.invoke() }
            }
        }

    init {
        orientation = VERTICAL

        badge.size = PersonalizationBadge.Size.LG
        badge.badgeView = PersonalizationBadge.BadgeView.WARNING
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(16)
        })

        header.orientation = VERTICAL
        titleView.headerStyle(20f)
        subtitleView.headerStyle(16f)
        header.addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        header.addView(subtitleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        imageCard.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(IMAGE_CARD_COLOR)
        }
        imageCard.setPadding(dp(16), dp(16), dp(16), dp(16))
        (imageView as AppCompatImageView).scaleType = ImageView.ScaleType.FIT_CENTER
        imageCard.addView(imageView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        imageArea.setPadding(0, dp(16), 0, dp(16))
        imageArea.addView(
            imageCard,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        addView(imageArea, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(spacer, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        bottom.orientation = VERTICAL
        bottom.addView(price, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        bottom.addView(timer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })
        button.size = PersonalizationButton.Size.LG
        button.buttonView = PersonalizationButton.ButtonView.PRIMARY
        button.setOnClickListener { onButtonClick?.invoke() }
        bottom.addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        applyVisibility()
    }

    private fun applyVisibility() {
        badge.visibility = visibleIf(!badgeText.isNullOrEmpty())
        titleView.visibility = visibleIf(!title.isNullOrEmpty())
        subtitleView.visibility = visibleIf(!subtitle.isNullOrEmpty())
        (subtitleView.layoutParams as LayoutParams).topMargin = if (title.isNullOrEmpty()) 0 else dp(8)
        header.visibility = visibleIf(!title.isNullOrEmpty() || !subtitle.isNullOrEmpty())
        imageArea.visibility = visibleIf(showImage)
        spacer.visibility = visibleIf(!showImage)
        price.visibility = visibleIf(showPrice)
        timer.visibility = visibleIf(showTimer)
        button.visibility = visibleIf(!buttonText.isNullOrEmpty())

        // Шаг 16 только между видимыми элементами низа.
        var first = true
        for (child in listOf<View>(price, timer, button)) {
            if (child.visibility != View.VISIBLE) continue
            (child.layoutParams as LayoutParams).topMargin = if (first) 0 else dp(16)
            first = false
        }
        requestLayout()
    }

    private fun AppCompatTextView.headerStyle(sizeSp: Float) {
        includeFontPadding = false
        typeface = PersonalizationTheme.typeface(context, emphasized = true)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setLineSpacing(0f, HEADER_LINE_HEIGHT)
        setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_primary))
    }

    private fun visibleIf(visible: Boolean) = if (visible) View.VISIBLE else View.GONE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        /** Карточка фото в макете белая в обеих темах — фото товаров снимают на белом. */
        const val IMAGE_CARD_COLOR = 0xFFFFFFFF.toInt()
        const val HEADER_LINE_HEIGHT = 1.4f
    }
}

/** Квадрат, вписанный в отведённое место: сторона — меньшая из ширины и высоты. */
internal class StorySquareFrame(context: Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val side = when {
            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED -> width
            MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED -> height
            else -> minOf(width, height)
        }
        val exact = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
        super.onMeasure(exact, exact)
    }
}
