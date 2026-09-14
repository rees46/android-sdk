package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R

/**
 * Заголовок выдачи поиска.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Title (167:3802), символ Search results (167:3807).
 * Три ряда: заголовок с кнопкой «назад» и переключателем вида, строка с фильтром,
 * сортировкой и числом найденного, ряд применённых фильтров-тегов.
 * Два нижних ряда в макете скрываемые (showResults, showFilters) — здесь они
 * прячутся сами, когда данных нет.
 *
 * Собран из готовых компонентов: [PersonalizationTitle], [PersonalizationButton],
 * [PersonalizationButtonGroup], [PersonalizationTag].
 */
class PersonalizationSearchResultsTitle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Тег применённого фильтра: подпись и снятие по крестику. */
    data class Filter(val label: CharSequence, val onRemove: () -> Unit)

    private val titleRow = PersonalizationTitle(context)
    private val backButton = PersonalizationButton(context)
    private val viewSwitch = PersonalizationButtonGroup(context)

    private val resultsRow = LinearLayout(context)
    private val filtersButton = PersonalizationButton(context)
    private val sortButton = PersonalizationButton(context)
    private val foundPrefix = AppCompatTextView(context)
    private val foundCount = AppCompatTextView(context)
    private val foundSuffix = AppCompatTextView(context)

    private val filtersRow = LinearLayout(context)

    var text: CharSequence?
        get() = titleRow.text
        set(value) {
            titleRow.text = value
        }

    /** Индекс выбранного вида выдачи: 0 — плитка, 1 — список. */
    var selectedViewIndex: Int
        get() = viewSwitch.selectedIndex
        set(value) {
            viewSwitch.selectedIndex = value
        }

    var onBack: (() -> Unit)? = null
    var onViewChanged: ((Int) -> Unit)? = null
    var onFilters: (() -> Unit)? = null
    var onSort: (() -> Unit)? = null

    init {
        orientation = VERTICAL

        backButton.size = PersonalizationButton.Size.MD
        backButton.buttonView = PersonalizationButton.ButtonView.GHOST
        backButton.iconStart = R.drawable.personalization_ic_arrow_left
        backButton.setOnClickListener { onBack?.invoke() }

        viewSwitch.items = listOf(
            PersonalizationButtonGroup.Item(
                icon = R.drawable.personalization_ic_grid_2x2,
                activeIcon = R.drawable.personalization_ic_grid_2x2_fill
            ),
            PersonalizationButtonGroup.Item(
                icon = R.drawable.personalization_ic_list,
                activeIcon = R.drawable.personalization_ic_list_fill
            )
        )
        viewSwitch.onSelected = { onViewChanged?.invoke(it) }

        titleRow.setLeading(backButton)
        titleRow.setTrailing(viewSwitch)
        addView(titleRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        buildResultsRow()
        buildFiltersRow()
    }

    private fun buildResultsRow() {
        resultsRow.orientation = HORIZONTAL
        resultsRow.gravity = Gravity.CENTER_VERTICAL

        filtersButton.size = PersonalizationButton.Size.MD
        filtersButton.buttonView = PersonalizationButton.ButtonView.GHOST
        filtersButton.iconStart = R.drawable.personalization_ic_equalizer_horizontal
        filtersButton.setOnClickListener { onFilters?.invoke() }

        sortButton.size = PersonalizationButton.Size.MD
        sortButton.buttonView = PersonalizationButton.ButtonView.GHOST
        sortButton.iconStart = R.drawable.personalization_ic_arrows_up_down
        sortButton.setOnClickListener { onSort?.invoke() }

        val controls = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(filtersButton)
            addView(sortButton, marginStart(R.dimen.personalization_spacing_sm))
        }
        resultsRow.addView(controls)

        styleFound(foundPrefix, emphasized = false)
        styleFound(foundCount, emphasized = true)
        styleFound(foundSuffix, emphasized = true)

        val found = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(foundPrefix)
            addView(foundCount, marginStart(R.dimen.personalization_spacing_sm))
            addView(foundSuffix, marginStart(R.dimen.personalization_spacing_sm))
        }
        resultsRow.addView(found, marginStart(R.dimen.personalization_spacing_lg))

        resultsRow.isVisible = false
        addView(
            resultsRow,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
            }
        )
    }

    private fun buildFiltersRow() {
        filtersRow.orientation = HORIZONTAL
        filtersRow.isVisible = false
        addView(
            filtersRow,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
            }
        )
    }

    /**
     * Строка «найдено N товаров». Слова — параметры, локализация за интегратором.
     * Не задана — ряд скрыт.
     */
    fun setResults(prefix: CharSequence?, count: Int, suffix: CharSequence?) {
        if (prefix == null && suffix == null) {
            resultsRow.isVisible = false
            return
        }
        foundPrefix.text = prefix
        foundCount.text = count.toString()
        foundSuffix.text = suffix
        resultsRow.isVisible = true
    }

    /** Применённые фильтры. Пусто — ряд скрыт. */
    fun setFilters(filters: List<Filter>) {
        filtersRow.removeAllViews()
        filtersRow.isVisible = filters.isNotEmpty()
        filters.forEachIndexed { index, filter ->
            val tag = PersonalizationTag(context).apply {
                text = filter.label
                tagView = PersonalizationTag.TagView.SECONDARY
                onRemove = filter.onRemove
            }
            filtersRow.addView(
                tag,
                if (index == 0) LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                else marginStart(R.dimen.personalization_spacing_sm)
            )
        }
    }

    private fun styleFound(view: AppCompatTextView, emphasized: Boolean) {
        view.includeFontPadding = false
        if (emphasized) {
            // Inter в SDK не поставляется, ближайшее системное к Emphasized 600.
            view.typeface = PersonalizationTheme.typeface(context, emphasized = true)
        }
        view.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.personalization_font_size_sm)
        )
        TextViewCompat.setLineHeight(
            view,
            resources.getDimensionPixelSize(R.dimen.personalization_line_height_sm)
        )
        // В макете трекинг 0.05px при кегле 14, у Android он в em.
        view.letterSpacing = 0.05f / 14f
        view.setTextColor(
            PersonalizationTheme.color(context, R.color.personalization_text_secondary)
        )
    }

    private fun marginStart(dimenRes: Int): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = resources.getDimensionPixelSize(dimenRes)
        }
}
