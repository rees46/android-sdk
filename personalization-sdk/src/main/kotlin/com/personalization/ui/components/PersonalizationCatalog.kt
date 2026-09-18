package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Экран каталога: заголовок, плитка или список товаров, внизу лоадер, счётчик
 * и кнопка «загрузить ещё».
 *
 * Источник: Figma Mobile SDK UI Kit, секция Card — Search results (167:4477) и
 * Category (167:4856). Оба одной формы, разница только в заголовке: у выдачи
 * [PersonalizationSearchResultsTitle], у категории [PersonalizationTitle] с
 * переключателем вида. Поэтому заголовок здесь — слот, а не вариант.
 * Три нижних элемента в макете скрываемые (showLoader, showCount, showLoadMore).
 * Шаг блока 12.
 *
 * Пустая выдача — страница SearchResultsScreen, Search Results/Empty State (319:7743):
 * заголовок тот же, вместо плитки [PersonalizationEmptyState]. Показывается, когда
 * задан [emptyText] и товаров нет.
 */
@InternalPersonalizationUiApi
class PersonalizationCatalog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val grid = PersonalizationProductsGrid(context)
    private val emptyState = PersonalizationEmptyState(context)
    private val loader = PersonalizationLoader(context)
    private val count = PersonalizationCount(context)
    private val loadMoreButton = PersonalizationButton(context)
    private var headerView: View? = null

    var view: PersonalizationProductsGrid.View
        get() = grid.view
        set(value) {
            grid.view = value
        }

    var products: List<PersonalizationProduct>
        get() = grid.products
        set(value) {
            grid.products = value
            applyEmpty()
        }

    /** Текст пустой выдачи. `null` — без пустого состояния, плитка остаётся на месте. */
    var emptyText: CharSequence? = null
        set(value) {
            field = value
            emptyState.text = value
            applyEmpty()
        }

    var imageLoader: ((ImageView, PersonalizationProduct) -> Unit)?
        get() = grid.imageLoader
        set(value) {
            grid.imageLoader = value
        }

    var onProductAction: ((PersonalizationProduct) -> Unit)?
        get() = grid.onProductAction
        set(value) {
            grid.onProductAction = value
        }

    /** Нажатие на карточку — открыть товар. */
    var onProductClick: ((PersonalizationProduct) -> Unit)?
        get() = grid.onProductClick
        set(value) {
            grid.onProductClick = value
        }

    /** Пропорция картинок карточек, см. [PersonalizationProductCard.imageAspect]. */
    var imageAspect: PersonalizationProductImage.Aspect
        get() = grid.imageAspect
        set(value) {
            grid.imageAspect = value
        }

    /**
     * Во фреймах с лоадером (296:3544, 299:6606) счётчика и кнопки нет — на время
     * загрузки лоадер встаёт на их место.
     */
    var isLoading: Boolean = false
        set(value) {
            field = value
            loader.isVisible = value
            applyFooter()
        }

    /** Подпись кнопки «загрузить ещё». `null` — без кнопки. */
    var loadMoreText: CharSequence? = null
        set(value) {
            field = value
            loadMoreButton.text = value
            applyFooter()
        }

    private var hasCount = false

    var onLoadMore: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)

        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        emptyState.isVisible = false
        addView(emptyState, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Лоадер в макете — по центру строки; на всю ширину он бы прижал кольцо к левому краю.
        loader.isVisible = false
        addView(loader, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = gap
            gravity = Gravity.CENTER_HORIZONTAL
        })

        count.isVisible = false
        addView(count, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })

        loadMoreButton.size = PersonalizationButton.Size.MD
        loadMoreButton.buttonView = PersonalizationButton.ButtonView.SECONDARY
        loadMoreButton.iconStart = R.drawable.personalization_ic_arrow_rotate_cw
        loadMoreButton.isVisible = false
        loadMoreButton.setOnClickListener { onLoadMore?.invoke() }
        addView(loadMoreButton, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
    }

    /** Заголовок над товарами: выдача или категория. `null` — убрать. */
    fun setHeader(view: View?) {
        headerView?.let(::removeView)
        headerView = view
        view?.let { addView(it, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
        val gap = if (view == null) 0 else resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        (grid.layoutParams as LayoutParams).topMargin = gap
        (emptyState.layoutParams as LayoutParams).topMargin = gap
    }

    private fun applyEmpty() {
        val empty = grid.products.isEmpty() && !emptyText.isNullOrEmpty()
        emptyState.isVisible = empty
        grid.isVisible = !empty
    }

    /** Счётчик «показано N из M». Слова — параметры. `prefix == null` — скрыть. */
    fun setCount(prefix: CharSequence?, shown: Int, separator: CharSequence, total: Int) {
        hasCount = prefix != null
        if (prefix != null) count.set(prefix, shown, separator, total)
        applyFooter()
    }

    private fun applyFooter() {
        count.isVisible = hasCount && !isLoading
        loadMoreButton.isVisible = !loadMoreText.isNullOrEmpty() && !isLoading
    }
}
