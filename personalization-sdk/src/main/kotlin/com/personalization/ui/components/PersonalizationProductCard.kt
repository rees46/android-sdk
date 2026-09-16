package com.personalization.ui.components

import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Карточка товара.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Card, фрейм Product (126:2263):
 * Carousel — колонка 220, Grid — колонка 161 с картинкой во всю ширину, List — строка
 * с картинкой шириной 120 и ценой с кнопкой внизу справа. Пропорция картинки —
 * [imageAspect]: на странице ProductCard (88:69) карточка нарисована с 4:3, 1:1 и 3:4,
 * и её высота идёт за картинкой.
 * У трёх типов разная типографика названия, цены и старой цены, поэтому она задана
 * в таблице. Старая цена карусели — 16/24 по страницам ProductCard и Product Carousel;
 * мастер-компонент Product там же даёт 14/20 — расхождение в макете, взяты страницы.
 *
 * Собрана из готовых блоков: [PersonalizationProductImage], [PersonalizationRating],
 * [PersonalizationBadge] (скидка, вид danger), [PersonalizationButton].
 * Изображение хост грузит сам в `image.imageView`.
 */
@InternalPersonalizationUiApi
class PersonalizationProductCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Type(
        val nameFontRes: Int,
        val nameLineHeightRes: Int,
        val nameLetterSpacingPx: Float,
        val priceFontRes: Int,
        val priceLineHeightRes: Int,
        val oldPriceFontRes: Int,
        val oldPriceLineHeightRes: Int,
        val oldPriceLetterSpacingPx: Float
    ) {
        CAROUSEL(
            R.dimen.personalization_font_size_base, R.dimen.personalization_line_height_base, 0f,
            R.dimen.personalization_font_size_xl, R.dimen.personalization_line_height_xl,
            R.dimen.personalization_font_size_base, R.dimen.personalization_line_height_base, 0f
        ),
        GRID(
            R.dimen.personalization_font_size_base, R.dimen.personalization_line_height_base, 0f,
            R.dimen.personalization_font_size_lg, R.dimen.personalization_line_height_lg,
            R.dimen.personalization_font_size_sm, R.dimen.personalization_line_height_sm, 0.05f
        ),
        LIST(
            R.dimen.personalization_font_size_sm, R.dimen.personalization_line_height_sm, 0.05f,
            R.dimen.personalization_font_size_base, R.dimen.personalization_line_height_base,
            R.dimen.personalization_font_size_sm, R.dimen.personalization_line_height_sm, 0.05f
        )
    }

    /** Изображение: хост грузит картинку в `image.imageView`. */
    val image = PersonalizationProductImage(context)

    private val imageFrame = FrameLayout(context)
    private val imageBadge = PersonalizationBadge(context)
    private val brandView = AppCompatTextView(context)
    private val nameView = AppCompatTextView(context)
    private val rating = PersonalizationRating(context)
    private val priceView = AppCompatTextView(context)
    private val priceBadge = PersonalizationBadge(context)
    private val oldPriceView = AppCompatTextView(context)
    private val button = PersonalizationButton(context)

    var type: Type = Type.CAROUSEL
        set(value) {
            field = value
            rebuild()
        }

    /** Пропорция картинки; высота карточки идёт за ней. */
    var imageAspect: PersonalizationProductImage.Aspect
        get() = image.aspect
        set(value) {
            image.aspect = value
        }

    var brand: CharSequence? = null
        set(value) {
            field = value
            brandView.text = value
            brandView.isVisible = !value.isNullOrEmpty()
        }

    var name: CharSequence?
        get() = nameView.text
        set(value) {
            nameView.text = value
        }

    var price: CharSequence?
        get() = priceView.text
        set(value) {
            priceView.text = value
        }

    /** Старая цена, зачёркнутая. `null` — не показывать. */
    var oldPrice: CharSequence? = null
        set(value) {
            field = value
            oldPriceView.text = value
            oldPriceView.isVisible = !value.isNullOrEmpty()
        }

    /** Скидка, например «-15%». `null` — без бейджа. */
    var discount: CharSequence? = null
        set(value) {
            field = value
            imageBadge.text = value
            priceBadge.text = value
            applyBadgeVisibility()
        }

    /** Подпись кнопки. `null` — без кнопки. */
    var actionText: CharSequence? = null
        set(value) {
            field = value
            button.text = value
            button.isVisible = !value.isNullOrEmpty()
        }

    var onAction: (() -> Unit)? = null

    /** Нажатие на карточку целиком (не на кнопку) — открыть товар. */
    var onClick: (() -> Unit)? = null
        set(value) {
            field = value
            isClickable = value != null
        }

    /**
     * @param value уже отформатированная оценка: в макете «4,7» с запятой;
     *   `null` — товар без оценки, ряд рейтинга прячется.
     */
    fun setRating(value: CharSequence?, reviews: Int) {
        rating.isVisible = value != null
        if (value != null) rating.set(value, reviews)
    }

    init {
        imageBadge.size = PersonalizationBadge.Size.SM
        imageBadge.badgeView = PersonalizationBadge.BadgeView.DANGER
        priceBadge.size = PersonalizationBadge.Size.SM
        priceBadge.badgeView = PersonalizationBadge.BadgeView.DANGER

        brandView.includeFontPadding = false
        brandView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.personalization_font_size_xs))
        TextViewCompat.setLineHeight(brandView, resources.getDimensionPixelSize(R.dimen.personalization_line_height_xs))
        brandView.letterSpacing = 0.05f / 12f
        brandView.setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_secondary))
        brandView.isVisible = false

        nameView.includeFontPadding = false
        nameView.setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_primary))

        priceView.includeFontPadding = false
        priceView.typeface = PersonalizationTheme.typeface(context, emphasized = true)
        priceView.setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_primary))

        oldPriceView.includeFontPadding = false
        oldPriceView.paintFlags = oldPriceView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        oldPriceView.setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_hint))
        oldPriceView.isVisible = false

        button.size = PersonalizationButton.Size.MD
        button.buttonView = PersonalizationButton.ButtonView.PRIMARY
        button.isVisible = false
        button.setOnClickListener { onAction?.invoke() }
        setOnClickListener { onClick?.invoke() }
        isClickable = false

        imageFrame.addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        imageFrame.addView(imageBadge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))

        rebuild()
    }

    private fun rebuild() {
        detachAll()
        removeAllViews()
        applyTypography()
        applyBadgeVisibility()
        if (type == Type.LIST) buildList() else buildColumn()
    }

    private fun applyTypography() {
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(type.nameFontRes))
        TextViewCompat.setLineHeight(nameView, resources.getDimensionPixelSize(type.nameLineHeightRes))
        nameView.letterSpacing = if (type.nameLetterSpacingPx == 0f) 0f
            else type.nameLetterSpacingPx / pxToSp(resources.getDimension(type.nameFontRes))
        priceView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(type.priceFontRes))
        TextViewCompat.setLineHeight(priceView, resources.getDimensionPixelSize(type.priceLineHeightRes))
        oldPriceView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(type.oldPriceFontRes))
        TextViewCompat.setLineHeight(oldPriceView, resources.getDimensionPixelSize(type.oldPriceLineHeightRes))
        oldPriceView.letterSpacing = if (type.oldPriceLetterSpacingPx == 0f) 0f
            else type.oldPriceLetterSpacingPx / pxToSp(resources.getDimension(type.oldPriceFontRes))
    }

    /** У колонок скидка лежит на картинке, у списка — рядом с ценой. */
    private fun applyBadgeVisibility() {
        val has = !discount.isNullOrEmpty()
        imageBadge.isVisible = has && type != Type.LIST
        priceBadge.isVisible = has && type == Type.LIST
    }

    /** Carousel и Grid: картинка, название, рейтинг, цена, кнопка — колонкой с шагом 8. */
    private fun buildColumn() {
        orientation = VERTICAL
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
        val badgeInset = resources.getDimensionPixelSize(
            if (type == Type.GRID) R.dimen.personalization_spacing_sm else R.dimen.personalization_spacing_md
        )
        (imageBadge.layoutParams as FrameLayout.LayoutParams).setMargins(0, badgeInset, badgeInset, 0)

        val imageWidth = if (type == Type.CAROUSEL) dpToPx(CAROUSEL_WIDTH_DP) else LayoutParams.MATCH_PARENT
        addView(imageFrame, LayoutParams(imageWidth, LayoutParams.WRAP_CONTENT))

        addView(nameBlock(gapBetween = R.dimen.personalization_spacing_xs), topMargin(gap))
        addView(rating, topMargin(gap))
        addView(priceRow(), topMargin(gap))
        addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(0, gap, 0, 0) })
    }

    /**
     * List: картинка шириной 120 слева, справа колонка — название с рейтингом сверху,
     * цена с кнопкой снизу. Высоту строки задаёт картинка по своей пропорции.
     */
    private fun buildList() {
        orientation = HORIZONTAL
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        val side = dpToPx(LIST_IMAGE_DP)
        addView(imageFrame, LayoutParams(side, LayoutParams.WRAP_CONTENT))

        val top = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(nameBlock(gapBetween = R.dimen.personalization_spacing_xs))
            addView(rating, topMargin(resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)))
        }
        // Вью переиспользуются между раскладками и приносят с собой прежние LayoutParams
        // (у кнопки из колонки — match_parent), поэтому параметры здесь задаются явно.
        val wrap = { LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT) }
        val priceBlock = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(priceView, wrap())
                addView(priceBadge, wrap().apply {
                    marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
                })
            })
            addView(oldPriceView, wrap())
        }
        val bottom = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(priceBlock, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(button, wrap())
        }
        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(top)
            addView(View(context), LayoutParams(0, 0, 1f))
            addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(column, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = gap })
    }

    private fun nameBlock(gapBetween: Int): LinearLayout =
        LinearLayout(context).apply {
            orientation = VERTICAL
            addView(brandView)
            addView(nameView, topMargin(resources.getDimensionPixelSize(gapBetween)))
        }

    private fun priceRow(): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(priceView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(oldPriceView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
            })
        }

    private fun topMargin(px: Int): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px }

    /** Снимает переиспользуемые вью с прежних родителей перед пересборкой. */
    private fun detachAll() {
        listOf(imageFrame, brandView, nameView, rating, priceView, priceBadge, oldPriceView, button)
            .forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private fun pxToSp(px: Float): Float = px / resources.displayMetrics.scaledDensity

    private companion object {
        const val CAROUSEL_WIDTH_DP = 220
        const val LIST_IMAGE_DP = 120
    }
}
