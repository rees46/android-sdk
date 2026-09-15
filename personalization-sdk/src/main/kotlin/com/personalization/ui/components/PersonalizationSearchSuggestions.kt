package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Панель подсказок поиска: теги-подсказки, категории, товары.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Card, фрейм Search (151:3880).
 * Экран там нарисован наброском — шапка вручную, а не из InputField, шрифт
 * SF Pro, — поэтому сюда взято только то, что читается однозначно: ряд тегов
 * с шагом 8, блоки категорий и товаров с шагом 12 внутри и между, разделитель
 * 1px, боковые поля 16. Разделитель в макете — чёрный 4%, ближайший токен
 * Line/Generic Subtle (5%).
 *
 * Строки — [PersonalizationSuggestionRow], теги — [PersonalizationTag].
 * Картинки хост грузит через [imageLoader].
 */
@InternalPersonalizationUiApi
class PersonalizationSearchSuggestions @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    data class Suggestion(
        val id: String,
        val title: CharSequence,
        /** Цена у товара, родительская категория у категории. */
        val subtitle: CharSequence? = null,
        val imageUrl: String? = null
    )

    private val tagsScroll = HorizontalScrollView(context)
    private val tagsRow = LinearLayout(context)
    private val categoriesColumn = LinearLayout(context)
    private val separator = View(context)
    private val productsColumn = LinearLayout(context)

    /** Подстрока запроса, выделяемая в подсказках полужирным. */
    var highlight: CharSequence? = null
        set(value) {
            field = value
            rows().forEach { it.highlight = value }
        }

    /** Показывать ли картинки у строк: в макете есть оба варианта. */
    var showImages: Boolean = false
        set(value) {
            field = value
            rows().forEach { it.showImage = value }
        }

    var imageLoader: ((ImageView, Suggestion) -> Unit)? = null
    var onTagClick: ((CharSequence) -> Unit)? = null
    var onCategoryClick: ((Suggestion) -> Unit)? = null
    var onProductClick: ((Suggestion) -> Unit)? = null

    init {
        orientation = VERTICAL
        val side = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)

        tagsRow.orientation = HORIZONTAL
        tagsScroll.isHorizontalScrollBarEnabled = false
        tagsScroll.addView(tagsRow)
        tagsScroll.setPadding(side, 0, side, 0)
        tagsScroll.clipToPadding = false
        tagsScroll.isVisible = false
        addView(tagsScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        categoriesColumn.orientation = VERTICAL
        categoriesColumn.setPadding(side, 0, side, 0)
        categoriesColumn.isVisible = false
        addView(categoriesColumn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })

        separator.setBackgroundColor(PersonalizationTheme.color(context, R.color.personalization_line_generic_subtle))
        separator.isVisible = false
        addView(separator, LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(1)).apply { topMargin = gap })

        productsColumn.orientation = VERTICAL
        productsColumn.setPadding(side, 0, side, 0)
        productsColumn.isVisible = false
        addView(productsColumn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
    }

    fun setTags(tags: List<CharSequence>) {
        tagsRow.removeAllViews()
        tagsScroll.isVisible = tags.isNotEmpty()
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
        tags.forEachIndexed { index, tag ->
            val view = PersonalizationTag(context).apply {
                text = tag
                tagView = PersonalizationTag.TagView.PRIMARY
                setOnClickListener { onTagClick?.invoke(tag) }
            }
            tagsRow.addView(view, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) marginStart = gap
            })
        }
        applySeparator()
    }

    fun setCategories(items: List<Suggestion>) {
        fill(categoriesColumn, items, PersonalizationSuggestionRow.Kind.CATEGORY) { onCategoryClick?.invoke(it) }
        applySeparator()
    }

    fun setProducts(items: List<Suggestion>) {
        fill(productsColumn, items, PersonalizationSuggestionRow.Kind.PRODUCT) { onProductClick?.invoke(it) }
        applySeparator()
    }

    private fun fill(
        column: LinearLayout,
        items: List<Suggestion>,
        kind: PersonalizationSuggestionRow.Kind,
        onClick: (Suggestion) -> Unit
    ) {
        column.removeAllViews()
        column.isVisible = items.isNotEmpty()
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        items.forEachIndexed { index, item ->
            val row = PersonalizationSuggestionRow(context).apply {
                this.kind = kind
                title = item.title
                subtitle = item.subtitle
                showImage = showImages
                highlight = this@PersonalizationSearchSuggestions.highlight
                setOnClickListener { onClick(item) }
                if (showImages) imageLoader?.invoke(imageView, item)
            }
            column.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = gap
            })
        }
    }

    /** Разделитель нужен только между двумя непустыми блоками. */
    private fun applySeparator() {
        separator.isVisible = categoriesColumn.isVisible && productsColumn.isVisible
    }

    private fun rows(): List<PersonalizationSuggestionRow> =
        (0 until categoriesColumn.childCount).mapNotNull { categoriesColumn.getChildAt(it) as? PersonalizationSuggestionRow } +
            (0 until productsColumn.childCount).mapNotNull { productsColumn.getChildAt(it) as? PersonalizationSuggestionRow }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
