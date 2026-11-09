package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.personalization.R

/**
 * Блок товаров: заголовок, товары, у карусели — точки. Товары даёт хост; сам блок ничего
 * не грузит. Блок, который сам грузит рекомендации или коллекцию, —
 * [com.personalization.ui.widgets.PersonalizationRecommenderBlock], он собран на этом.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Card — Recommender/Carousel (90:655),
 * Recommender/Grid (126:1944), Recommender/List (126:2551).
 * У карусели заголовок с кнопкой «Show all» и точки под лентой, шаг блока 16;
 * у плитки и списка только заголовок, шаг 12.
 *
 * Собран из [PersonalizationTitle], [PersonalizationButton],
 * [PersonalizationProductsCarousel] / [PersonalizationProductsGrid], [PersonalizationDots].
 */
class PersonalizationProductsBlock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Layout { CAROUSEL, GRID, LIST }

    private val title = PersonalizationTitle(context)
    private val showAllButton = PersonalizationButton(context)
    private val carousel = PersonalizationProductsCarousel(context)
    private val grid = PersonalizationProductsGrid(context)
    private val dots = PersonalizationDots(context)

    var layout: Layout = Layout.CAROUSEL
        set(value) {
            field = value
            rebuild()
        }

    /** Заголовок. Пустой и без кнопки «показать все» — блок без строки заголовка. */
    var text: CharSequence?
        get() = title.text
        set(value) {
            title.text = value
            applyTitleRow()
        }

    /** Подпись кнопки «показать все» у карусели. `null` — без кнопки. */
    var showAllText: CharSequence? = null
        set(value) {
            field = value
            showAllButton.text = value
            applyTitleRow()
        }

    var onShowAll: (() -> Unit)? = null

    /**
     * Точки под каруселью (showDots в макете). По умолчанию выключены: лента прокручивается
     * свободно, а не постранично, и точка на каждый товар индикатором не служит.
     */
    var showDots: Boolean = false
        set(value) {
            field = value
            dots.isVisible = value && layout == Layout.CAROUSEL
        }

    var products: List<PersonalizationProduct> = emptyList()
        set(value) {
            field = value
            carousel.products = value
            grid.products = value
            dots.count = value.size
        }

    /** Рисовать ли рейтинг на карточках; выключенный прячет его, даже если оценка есть. */
    var showRating: Boolean = true
        set(value) {
            field = value
            carousel.showRating = value
            grid.showRating = value
        }

    var imageLoader: ((ImageView, PersonalizationProduct) -> Unit)? = null
        set(value) {
            field = value
            carousel.imageLoader = value
            grid.imageLoader = value
        }

    var onProductAction: ((PersonalizationProduct) -> Unit)? = null
        set(value) {
            field = value
            carousel.onProductAction = value
            grid.onProductAction = value
        }

    /** Нажатие на карточку — открыть товар. */
    var onProductClick: ((PersonalizationProduct) -> Unit)? = null
        set(value) {
            field = value
            carousel.onProductClick = value
            grid.onProductClick = value
        }

    /** Пропорция картинок карточек, см. [PersonalizationProductCard.imageAspect]. */
    var imageAspect: PersonalizationProductImage.Aspect = PersonalizationProductImage.Aspect.SQUARE
        set(value) {
            field = value
            carousel.imageAspect = value
            grid.imageAspect = value
        }

    init {
        orientation = VERTICAL
        showAllButton.size = PersonalizationButton.Size.SM
        showAllButton.buttonView = PersonalizationButton.ButtonView.GHOST
        showAllButton.iconEnd = R.drawable.personalization_ic_angle_large_right
        showAllButton.setOnClickListener { onShowAll?.invoke() }
        carousel.onFirstVisibleChanged = { dots.selectedIndex = it }
        rebuild()
    }

    private fun rebuild() {
        removeAllViews()
        val gap = gap()
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val list = when (layout) {
            Layout.CAROUSEL -> carousel
            Layout.GRID -> grid.also { it.view = PersonalizationProductsGrid.View.GRID }
            Layout.LIST -> grid.also { it.view = PersonalizationProductsGrid.View.LIST }
        }
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        dots.isVisible = showDots && layout == Layout.CAROUSEL
        addView(dots, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
        applyTitleRow()
    }

    /**
     * Строка заголовка есть, когда в ней что-то есть: текст или кнопка «показать все».
     * Без неё лента встаёт наверх блока, без отступа под заголовок.
     */
    private fun applyTitleRow() {
        val showAll = layout == Layout.CAROUSEL && !showAllText.isNullOrEmpty()
        title.setTrailing(if (showAll) showAllButton else null)
        title.isVisible = showAll || !title.text.isNullOrEmpty()
        val list = getChildAt(1) ?: return
        (list.layoutParams as LayoutParams).topMargin = if (title.isVisible) gap() else 0
        list.requestLayout()
    }

    /** Есть ли строка заголовка — для тестов. */
    internal val hasTitleRow: Boolean
        get() = title.isVisible

    private fun gap(): Int = resources.getDimensionPixelSize(
        if (layout == Layout.CAROUSEL) R.dimen.personalization_spacing_xl else R.dimen.personalization_spacing_lg
    )
}
