package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.personalization.R

/**
 * Блок рекомендаций: заголовок, товары, у карусели — точки.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Card — Recommender/Carousel (90:655),
 * Recommender/Grid (126:1944), Recommender/List (126:2551).
 * У карусели заголовок с кнопкой «Show all» и точки под лентой, шаг блока 16;
 * у плитки и списка только заголовок, шаг 12.
 *
 * Собран из [PersonalizationTitle], [PersonalizationButton],
 * [PersonalizationProductsCarousel] / [PersonalizationProductsGrid], [PersonalizationDots].
 */
class PersonalizationRecommenderBlock @JvmOverloads constructor(
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

    var text: CharSequence?
        get() = title.text
        set(value) {
            title.text = value
        }

    /** Подпись кнопки «показать все» у карусели. `null` — без кнопки. */
    var showAllText: CharSequence? = null
        set(value) {
            field = value
            showAllButton.text = value
            title.setTrailing(if (value.isNullOrEmpty() || layout != Layout.CAROUSEL) null else showAllButton)
        }

    var onShowAll: (() -> Unit)? = null

    /** У карусели точки под лентой; выключаются, как в макете (showDots). */
    var showDots: Boolean = true
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
        val gap = resources.getDimensionPixelSize(
            if (layout == Layout.CAROUSEL) R.dimen.personalization_spacing_xl else R.dimen.personalization_spacing_lg
        )
        title.setTrailing(if (layout == Layout.CAROUSEL && !showAllText.isNullOrEmpty()) showAllButton else null)
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val list = when (layout) {
            Layout.CAROUSEL -> carousel
            Layout.GRID -> grid.also { it.view = PersonalizationProductsGrid.View.GRID }
            Layout.LIST -> grid.also { it.view = PersonalizationProductsGrid.View.LIST }
        }
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })

        dots.isVisible = showDots && layout == Layout.CAROUSEL
        addView(dots, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
    }
}
