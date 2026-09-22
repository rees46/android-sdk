package com.personalization.ui.components

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Строка подсказки поиска: товар или категория.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Card — Product (151:4003) и Category
 * (151:4011), у обоих варианты Image и Text. Строка с картинкой 40x40 и двумя
 * строками текста (у товара цена, у категории родительская категория), либо одна
 * строка текста. У категории справа шеврон. Шаг между картинкой и текстом 10 —
 * значения нет в шкале отступов, взято из макета как есть.
 *
 * [highlight] выделяет совпадение с запросом полужирным, как в макете подсказок.
 * Картинку хост грузит в [imageView].
 */
@InternalPersonalizationUiApi
class PersonalizationSuggestionRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Kind { PRODUCT, CATEGORY }

    /** Хост грузит картинку сюда; показывается только когда [showImage] включён. */
    val imageView = AppCompatImageView(context)

    private val titleView = AppCompatTextView(context)
    private val subtitleView = AppCompatTextView(context)
    private val chevron = AppCompatImageView(context)
    private val column = LinearLayout(context)

    var kind: Kind = Kind.PRODUCT
        set(value) {
            field = value
            applyKind()
        }

    var showImage: Boolean = false
        set(value) {
            field = value
            imageView.isVisible = value
            subtitleView.isVisible = value && !subtitle.isNullOrEmpty()
            // Без картинки текст начинается с края строки, как в варианте Text.
            (column.layoutParams as LayoutParams).marginStart = if (value) dpToPx(GAP_DP) else 0
            column.requestLayout()
        }

    var title: CharSequence? = null
        set(value) {
            field = value
            applyTitle()
        }

    /** Подстрока запроса, которую надо выделить в [title]. */
    var highlight: CharSequence? = null
        set(value) {
            field = value
            applyTitle()
        }

    /** Цена у товара, родительская категория у категории. Видна только с картинкой. */
    var subtitle: CharSequence? = null
        set(value) {
            field = value
            subtitleView.text = value
            subtitleView.isVisible = showImage && !value.isNullOrEmpty()
        }

    init {
        orientation = HORIZONTAL
        isClickable = true

        imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        imageView.setBackgroundColor(PersonalizationTheme.color(context, R.color.personalization_background_card))
        imageView.isVisible = false
        addView(imageView, LayoutParams(dpToPx(IMAGE_DP), dpToPx(IMAGE_DP)))

        column.orientation = VERTICAL
        column.addView(titleView)
        column.addView(subtitleView)
        addView(column, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        chevron.setImageResource(R.drawable.personalization_ic_angle_large_right)
        ImageViewCompat.setImageTintList(chevron, PersonalizationTheme.colorStateList(context, R.color.personalization_text_hint))
        addView(chevron, LayoutParams(dpToPx(24), dpToPx(24)).apply { marginStart = dpToPx(GAP_DP) })

        styleText(titleView, R.color.personalization_text_primary)
        styleText(subtitleView, R.color.personalization_text_primary)
        subtitleView.isVisible = false
        applyKind()
    }

    private fun applyKind() {
        val category = kind == Kind.CATEGORY
        gravity = if (category) Gravity.CENTER_VERTICAL else Gravity.TOP
        chevron.isVisible = category
        if (category) {
            subtitleView.typeface = PersonalizationTheme.typeface(context, emphasized = false)
            subtitleView.setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_hint))
        } else {
            subtitleView.typeface = PersonalizationTheme.typeface(context, emphasized = true)
            subtitleView.setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_primary))
        }
    }

    private fun applyTitle() {
        val text = title ?: run { titleView.text = null; return }
        val query = highlight?.toString().orEmpty()
        if (query.isEmpty()) { titleView.text = text; return }
        val start = text.toString().indexOf(query, ignoreCase = true)
        if (start < 0) { titleView.text = text; return }
        titleView.text = SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), start, start + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun styleText(view: AppCompatTextView, colorRes: Int) {
        view.includeFontPadding = false
        view.typeface = PersonalizationTheme.typeface(context, emphasized = false)
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.personalization_font_size_sm))
        TextViewCompat.setLineHeight(view, resources.getDimensionPixelSize(R.dimen.personalization_line_height_sm))
        view.setLetterSpacingCompat(0.05f / 14f)
        view.setTextColor(PersonalizationTheme.color(context, colorRes))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private companion object {
        const val IMAGE_DP = 40
        const val GAP_DP = 10
    }
}
