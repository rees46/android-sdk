package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Экран фильтров выдачи: заголовок с крестиком, секции и две кнопки внизу.
 *
 * Источник: Figma Mobile SDK UI Kit, страница SearchResultsScreen — Search Results/Filters
 * (203:8083) и /Filters Dark Theme (241:10831). Заголовок — [PersonalizationTitle] c
 * ghost-кнопкой `cross-large` (Title/Filters, 204:8340). Секции двух видов: диапазон
 * «From — to» из двух [PersonalizationInputField] MD и список
 * [PersonalizationCheckboxWithLabel] с [PersonalizationAccordion] «показать ещё».
 * Внизу [PersonalizationButton] MD: secondary «Reset» и primary «Apply» поровну.
 * Шаг между секциями 16, внутри секции 12, заголовок секции — LG/Emphasized 18/28.
 * Фон экрана — Background/Generic.
 *
 * Состояние чекбоксов и раскрытия секций живёт здесь; хост получает изменения
 * через [onOptionToggle] и [onRangeChanged] и на «Apply» читает [sections].
 */
@InternalPersonalizationUiApi
class PersonalizationFilters @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    sealed class Section(val id: String, val title: CharSequence) {
        /**
         * Два поля «от — до». [select] — поля типа Select, список открывает хост.
         * [fromPlaceholder] / [toPlaceholder] — подсказки в пустых полях, например границы диапазона.
         */
        class Range(
            id: String,
            title: CharSequence,
            val fromLabel: CharSequence,
            val toLabel: CharSequence,
            val from: CharSequence? = null,
            val to: CharSequence? = null,
            val select: Boolean = false,
            val fromPlaceholder: CharSequence? = null,
            val toPlaceholder: CharSequence? = null
        ) : Section(id, title)

        /** Список чекбоксов; сверх [collapsedCount] прячется за аккордеон. */
        class Options(
            id: String,
            title: CharSequence,
            val options: List<Option>,
            val collapsedCount: Int = DEFAULT_COLLAPSED,
            val showMoreText: CharSequence? = null,
            val showLessText: CharSequence? = null
        ) : Section(id, title)
    }

    data class Option(val id: String, val label: CharSequence, val checked: Boolean = false)

    private val title = PersonalizationTitle(context)
    private val closeButton = PersonalizationButton(context)
    private val sectionsColumn = LinearLayout(context)
    private val resetButton = PersonalizationButton(context)
    private val applyButton = PersonalizationButton(context)

    /** Текущее состояние секций с учётом нажатий пользователя. */
    var sections: List<Section> = emptyList()
        private set

    var text: CharSequence?
        get() = title.text
        set(value) {
            title.text = value
        }

    var onClose: (() -> Unit)? = null

    var resetText: CharSequence?
        get() = resetButton.text
        set(value) {
            resetButton.text = value
        }

    var applyText: CharSequence?
        get() = applyButton.text
        set(value) {
            applyButton.text = value
        }

    var onReset: (() -> Unit)? = null
    var onApply: (() -> Unit)? = null

    /** Изменился чекбокс: секция, вариант, новое значение. */
    var onOptionToggle: ((sectionId: String, optionId: String, checked: Boolean) -> Unit)? = null

    /** Изменилось поле диапазона: секция, «от», «до». */
    var onRangeChanged: ((sectionId: String, from: CharSequence, to: CharSequence) -> Unit)? = null

    /** Нажато поле диапазона типа Select: секция и какое из двух (`true` — «от»). */
    var onRangeSelectClick: ((sectionId: String, isFrom: Boolean) -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(PersonalizationTheme.color(context, R.color.personalization_background_generic))
        val sectionGap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)

        closeButton.size = PersonalizationButton.Size.MD
        closeButton.buttonView = PersonalizationButton.ButtonView.GHOST
        closeButton.iconStart = R.drawable.personalization_ic_cross_large
        closeButton.setOnClickListener { onClose?.invoke() }
        title.setTrailing(closeButton)
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        sectionsColumn.orientation = VERTICAL
        addView(sectionsColumn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        resetButton.size = PersonalizationButton.Size.MD
        resetButton.buttonView = PersonalizationButton.ButtonView.SECONDARY
        resetButton.setOnClickListener { onReset?.invoke() }
        applyButton.size = PersonalizationButton.Size.MD
        applyButton.buttonView = PersonalizationButton.ButtonView.PRIMARY
        applyButton.setOnClickListener { onApply?.invoke() }
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(resetButton, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(applyButton, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = sectionGap })
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = sectionGap * 2 })
    }

    fun setSections(items: List<Section>) {
        sections = items
        sectionsColumn.removeAllViews()
        val sectionGap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
        items.forEach { section ->
            sectionsColumn.addView(
                buildSection(section),
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = sectionGap }
            )
        }
    }

    private fun buildSection(section: Section): LinearLayout {
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        return LinearLayout(context).apply {
            orientation = VERTICAL
            addView(heading(section.title), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            val content = when (section) {
                is Section.Range -> buildRange(section)
                is Section.Options -> buildOptions(section)
            }
            addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
        }
    }

    private fun heading(text: CharSequence): AppCompatTextView =
        AppCompatTextView(context).apply {
            this.text = text
            includeFontPadding = false
            typeface = PersonalizationTheme.typeface(context, emphasized = true)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.personalization_font_size_lg))
            TextViewCompat.setLineHeight(this, resources.getDimensionPixelSize(R.dimen.personalization_line_height_lg))
            setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_primary))
        }

    private fun bodyLabel(text: CharSequence): AppCompatTextView =
        AppCompatTextView(context).apply {
            this.text = text
            includeFontPadding = false
            typeface = PersonalizationTheme.typeface(context, emphasized = false)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.personalization_font_size_base))
            TextViewCompat.setLineHeight(this, resources.getDimensionPixelSize(R.dimen.personalization_line_height_base))
            setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_primary))
        }

    private fun buildRange(section: Section.Range): LinearLayout {
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        val fromField = rangeField(section, section.from, section.fromPlaceholder, isFrom = true)
        val toField = rangeField(section, section.to, section.toPlaceholder, isFrom = false)
        val notify = {
            val from = fromField.text?.toString().orEmpty()
            val to = toField.text?.toString().orEmpty()
            updateRange(section.id, from, to)
            onRangeChanged?.invoke(section.id, from, to)
        }
        fromField.editText.doAfterTextChanged { notify() }
        toField.editText.doAfterTextChanged { notify() }
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bodyLabel(section.fromLabel), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(fromField, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = gap })
            addView(bodyLabel(section.toLabel), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = gap })
            addView(toField, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = gap })
        }
    }

    private fun rangeField(
        section: Section.Range,
        value: CharSequence?,
        hint: CharSequence?,
        isFrom: Boolean
    ): PersonalizationInputField =
        PersonalizationInputField(context).apply {
            size = PersonalizationInputField.Size.MD
            type = if (section.select) PersonalizationInputField.Type.SELECT else PersonalizationInputField.Type.INPUT
            text = value
            placeholder = hint
            onSelectClick = { onRangeSelectClick?.invoke(section.id, isFrom) }
        }

    private fun buildOptions(section: Section.Options): LinearLayout {
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        val column = LinearLayout(context).apply { orientation = VERTICAL }
        val collapsible = section.options.size > section.collapsedCount && !section.showMoreText.isNullOrEmpty()
        val rows = section.options.mapIndexed { index, option ->
            PersonalizationCheckboxWithLabel(context).apply {
                text = option.label
                checkState = if (option.checked) PersonalizationCheckbox.CheckState.CHECKED
                    else PersonalizationCheckbox.CheckState.UNCHECKED
                onCheckedChange = { state ->
                    val checked = state == PersonalizationCheckbox.CheckState.CHECKED
                    updateOption(section.id, option.id, checked)
                    onOptionToggle?.invoke(section.id, option.id, checked)
                }
                isVisible = !collapsible || index < section.collapsedCount
            }
        }
        rows.forEachIndexed { index, row ->
            column.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = gap
            })
        }
        if (collapsible) {
            val hidden = section.options.size - section.collapsedCount
            val accordion = PersonalizationAccordion(context).apply {
                text = section.showMoreText
                count = hidden
                onToggle = { expanded ->
                    rows.forEachIndexed { index, row -> row.isVisible = expanded || index < section.collapsedCount }
                    text = if (expanded) section.showLessText ?: section.showMoreText else section.showMoreText
                    count = if (expanded) null else hidden
                }
            }
            column.addView(accordion, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
        }
        return column
    }

    private fun updateRange(sectionId: String, from: String, to: String) {
        sections = sections.map { section ->
            if (section.id != sectionId || section !is Section.Range) return@map section
            Section.Range(
                section.id, section.title, section.fromLabel, section.toLabel, from, to, section.select,
                section.fromPlaceholder, section.toPlaceholder
            )
        }
    }

    private fun updateOption(sectionId: String, optionId: String, checked: Boolean) {
        sections = sections.map { section ->
            if (section.id != sectionId || section !is Section.Options) return@map section
            Section.Options(
                section.id, section.title,
                section.options.map { if (it.id == optionId) it.copy(checked = checked) else it },
                section.collapsedCount, section.showMoreText, section.showLessText
            )
        }
    }

    private companion object {
        const val DEFAULT_COLLAPSED = 5
    }
}
