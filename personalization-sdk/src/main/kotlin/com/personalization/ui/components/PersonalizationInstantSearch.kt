package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Экран мгновенного поиска: поле ввода с «Cancel», недавние запросы, подсказки,
 * категории и товары.
 *
 * Источник: Figma Mobile SDK UI Kit, страница InstantSearchField — Instant Search/Text
 * (151:3976), /Input (310:10207), /With Images (310:9829), /Clear Recent Searches
 * (310:10016). Поле — [PersonalizationInputField] типа Search размера MD, ссылки —
 * [PersonalizationLink], подписи блоков — [PersonalizationListLabel], теги —
 * [PersonalizationTag] с переносом по строкам, строки — [PersonalizationSuggestionRow].
 *
 * Недавние запросы — теги с крестиком, в конце синий тег «ещё»; подсказки при вводе —
 * такие же теги без крестика. Перед категориями и перед товарами разделитель 1px.
 * Совпадение с запросом в строках выделяется полужирным само.
 *
 * Внимание: шаг колонки в макете 13 — такого значения в шкале нет, взят LG (12).
 * Разделитель в макете чёрный 4%, ближайший токен Line/Generic Subtle (5%).
 * Картинки хост грузит через [imageLoader].
 */
@InternalPersonalizationUiApi
class PersonalizationInstantSearch @JvmOverloads constructor(
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

    /** Поле ввода: наружу отдано ради inputType и фокуса. */
    val input = PersonalizationInputField(context)

    private val cancelLink = PersonalizationLink(context)
    private val recentLabelView = PersonalizationListLabel(context)
    private val clearLink = PersonalizationLink(context)
    private val recentHeader = LinearLayout(context)
    private val recentFlow: PersonalizationFlowLayout
    private val suggestionsFlow: PersonalizationFlowLayout
    private val categoriesSeparator = View(context)
    private val categoriesLabelView = PersonalizationListLabel(context)
    private val categoriesColumn = LinearLayout(context)
    private val productsSeparator = View(context)
    private val productsLabelView = PersonalizationListLabel(context)
    private val productsColumn = LinearLayout(context)

    private var recentSearches: List<CharSequence> = emptyList()
    private var suggestions: List<CharSequence> = emptyList()

    var query: CharSequence?
        get() = input.text
        set(value) {
            input.text = value
        }

    var placeholder: CharSequence?
        get() = input.placeholder
        set(value) {
            input.placeholder = value
        }

    /** Подпись ссылки справа от поля. `null` — без ссылки. */
    var cancelText: CharSequence? = null
        set(value) {
            field = value
            cancelLink.text = value
            cancelLink.isVisible = !value.isNullOrEmpty()
        }

    var onCancel: (() -> Unit)? = null
    var onQueryChanged: ((CharSequence) -> Unit)? = null
    var onSubmit: ((CharSequence) -> Unit)? = null

    /** Подпись над недавними запросами. `null` — блок без подписи и без «Clear». */
    var recentLabel: CharSequence? = null
        set(value) {
            field = value
            recentLabelView.text = value
            applyRecentVisibility()
        }

    var clearText: CharSequence? = null
        set(value) {
            field = value
            clearLink.text = value
            clearLink.isVisible = !value.isNullOrEmpty()
        }

    var onClearRecent: (() -> Unit)? = null

    /** Подпись синего тега в конце недавних запросов. `null` — без него. */
    var moreText: CharSequence? = null
        set(value) {
            field = value
            rebuildRecent()
        }

    var onMoreRecent: (() -> Unit)? = null
    var onRecentClick: ((CharSequence) -> Unit)? = null
    var onRecentRemove: ((CharSequence) -> Unit)? = null
    var onSuggestionClick: ((CharSequence) -> Unit)? = null

    var categoriesLabel: CharSequence? = null
        set(value) {
            field = value
            categoriesLabelView.text = value
            applySectionVisibility()
        }

    var productsLabel: CharSequence? = null
        set(value) {
            field = value
            productsLabelView.text = value
            applySectionVisibility()
        }

    /** Показывать ли картинки у строк: в макете есть оба варианта. Строка без картинки идёт без плейсхолдера. */
    var showImages: Boolean = false
        set(value) {
            field = value
            rows().forEach { it.showImage = value && (it.tag as? Suggestion)?.imageUrl != null }
        }

    var imageLoader: ((ImageView, Suggestion) -> Unit)? = null
    var onCategoryClick: ((Suggestion) -> Unit)? = null
    var onProductClick: ((Suggestion) -> Unit)? = null

    init {
        orientation = VERTICAL
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        val tagGap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)

        input.type = PersonalizationInputField.Type.SEARCH
        input.size = PersonalizationInputField.Size.MD
        input.editText.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.editText.doAfterTextChanged { text ->
            val value = text?.toString().orEmpty()
            rows().forEach { it.highlight = value }
            onQueryChanged?.invoke(value)
        }
        input.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                onSubmit?.invoke(input.text?.toString().orEmpty())
                true
            } else false
        }
        cancelLink.isVisible = false
        cancelLink.setOnClickListener { onCancel?.invoke() }
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(input, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(cancelLink, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
            })
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        clearLink.isVisible = false
        clearLink.setOnClickListener { onClearRecent?.invoke() }
        recentHeader.orientation = HORIZONTAL
        recentHeader.gravity = Gravity.CENTER_VERTICAL
        recentHeader.addView(recentLabelView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        recentHeader.addView(clearLink, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = gap
        })
        recentHeader.isVisible = false
        addView(recentHeader, block(gap))

        recentFlow = PersonalizationFlowLayout(context, tagGap, tagGap)
        recentFlow.isVisible = false
        addView(recentFlow, block(gap))

        suggestionsFlow = PersonalizationFlowLayout(context, tagGap, tagGap)
        suggestionsFlow.isVisible = false
        addView(suggestionsFlow, block(gap))

        addSection(categoriesSeparator, categoriesLabelView, categoriesColumn, gap)
        addSection(productsSeparator, productsLabelView, productsColumn, gap)
    }

    private fun addSection(separator: View, label: PersonalizationListLabel, column: LinearLayout, gap: Int) {
        separator.setBackgroundColor(PersonalizationTheme.color(context, R.color.personalization_line_generic_subtle))
        separator.isVisible = false
        addView(separator, LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(1)).apply { topMargin = gap })
        label.isVisible = false
        addView(label, block(gap))
        column.orientation = VERTICAL
        column.isVisible = false
        addView(column, block(gap))
    }

    private fun block(gap: Int): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap }

    fun setRecentSearches(items: List<CharSequence>) {
        recentSearches = items
        rebuildRecent()
    }

    fun setSuggestions(items: List<CharSequence>) {
        suggestions = items
        suggestionsFlow.removeAllViews()
        suggestionsFlow.isVisible = items.isNotEmpty()
        items.forEach { item ->
            suggestionsFlow.addView(PersonalizationTag(context).apply {
                text = item
                tagView = PersonalizationTag.TagView.SECONDARY
                setOnClickListener { onSuggestionClick?.invoke(item) }
            })
        }
    }

    fun setCategories(items: List<Suggestion>) {
        fill(categoriesColumn, items, PersonalizationSuggestionRow.Kind.CATEGORY) { onCategoryClick?.invoke(it) }
        applySectionVisibility()
    }

    fun setProducts(items: List<Suggestion>) {
        fill(productsColumn, items, PersonalizationSuggestionRow.Kind.PRODUCT) { onProductClick?.invoke(it) }
        applySectionVisibility()
    }

    private fun rebuildRecent() {
        recentFlow.removeAllViews()
        recentSearches.forEach { item ->
            recentFlow.addView(PersonalizationTag(context).apply {
                text = item
                tagView = PersonalizationTag.TagView.SECONDARY
                onRemove = { onRecentRemove?.invoke(item) }
                setOnClickListener { onRecentClick?.invoke(item) }
            })
        }
        moreText?.takeIf { it.isNotEmpty() && recentSearches.isNotEmpty() }?.let { more ->
            recentFlow.addView(PersonalizationTag(context).apply {
                text = more
                tagView = PersonalizationTag.TagView.PRIMARY
                setOnClickListener { onMoreRecent?.invoke() }
            })
        }
        applyRecentVisibility()
    }

    private fun applyRecentVisibility() {
        val has = recentSearches.isNotEmpty()
        recentFlow.isVisible = has
        recentHeader.isVisible = has && !recentLabel.isNullOrEmpty()
    }

    private fun applySectionVisibility() {
        val hasCategories = categoriesColumn.childCount > 0
        categoriesSeparator.isVisible = hasCategories
        categoriesLabelView.isVisible = hasCategories && !categoriesLabel.isNullOrEmpty()
        categoriesColumn.isVisible = hasCategories
        val hasProducts = productsColumn.childCount > 0
        productsSeparator.isVisible = hasProducts
        productsLabelView.isVisible = hasProducts && !productsLabel.isNullOrEmpty()
        productsColumn.isVisible = hasProducts
    }

    private fun fill(
        column: LinearLayout,
        items: List<Suggestion>,
        kind: PersonalizationSuggestionRow.Kind,
        onClick: (Suggestion) -> Unit
    ) {
        column.removeAllViews()
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        val highlight = input.text?.toString().orEmpty()
        items.forEachIndexed { index, item ->
            val withImage = showImages && item.imageUrl != null
            val row = PersonalizationSuggestionRow(context).apply {
                tag = item
                this.kind = kind
                title = item.title
                subtitle = item.subtitle
                showImage = withImage
                this.highlight = highlight
                setOnClickListener { onClick(item) }
                if (withImage) imageLoader?.invoke(imageView, item)
            }
            column.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = gap
            })
        }
    }

    private fun rows(): List<PersonalizationSuggestionRow> =
        (0 until categoriesColumn.childCount).mapNotNull { categoriesColumn.getChildAt(it) as? PersonalizationSuggestionRow } +
            (0 until productsColumn.childCount).mapNotNull { productsColumn.getChildAt(it) as? PersonalizationSuggestionRow }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
