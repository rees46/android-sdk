package com.personalization.ui.components

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Каталог товаров: плитка в две колонки или список.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Card, фрейм Products Grid (203:4766):
 * View=Grid — карточки типа Grid в две колонки с шагом 16 по обеим осям,
 * View=List — карточки типа List столбиком с шагом 16.
 *
 * Собран на RecyclerView; карточки — [PersonalizationProductCard]. Картинки
 * грузит хост через [imageLoader].
 */
@InternalPersonalizationUiApi
class PersonalizationProductsGrid @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    enum class View { GRID, LIST }

    private var productsAdapter = PersonalizationProductsAdapter(PersonalizationProductCard.Type.GRID)
    private var gapDecoration: ItemDecoration? = null

    var view: View = View.GRID
        set(value) {
            field = value
            rebuild()
        }

    var products: List<PersonalizationProduct>
        get() = productsAdapter.items
        set(value) {
            productsAdapter.items = value
        }

    /** Хост кладёт изображение товара в `ImageView` своим загрузчиком. */
    var imageLoader: ((ImageView, PersonalizationProduct) -> Unit)?
        get() = productsAdapter.imageLoader
        set(value) {
            productsAdapter.imageLoader = value
        }

    var onProductAction: ((PersonalizationProduct) -> Unit)?
        get() = productsAdapter.onAction
        set(value) {
            productsAdapter.onAction = value
        }

    init {
        isNestedScrollingEnabled = false
        rebuild()
    }

    private fun rebuild() {
        val previous = productsAdapter
        productsAdapter = PersonalizationProductsAdapter(
            if (view == View.GRID) PersonalizationProductCard.Type.GRID else PersonalizationProductCard.Type.LIST
        ).also {
            it.items = previous.items
            it.imageLoader = previous.imageLoader
            it.onAction = previous.onAction
        }
        adapter = productsAdapter

        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
        gapDecoration?.let(::removeItemDecoration)
        if (view == View.GRID) {
            layoutManager = GridLayoutManager(context, COLUMNS)
            gapDecoration = PersonalizationGridGapDecoration(gap, COLUMNS)
        } else {
            layoutManager = LinearLayoutManager(context, VERTICAL, false)
            gapDecoration = PersonalizationGapDecoration(gap, horizontal = false)
        }
        addItemDecoration(gapDecoration!!)
    }

    private companion object {
        const val COLUMNS = 2
    }
}

/** Шаг плитки: половина зазора с каждой стороны у внутренних краёв, полный сверху со второй строки. */
internal class PersonalizationGridGapDecoration(
    private val gap: Int,
    private val columns: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: android.view.View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val column = position % columns
        outRect.left = gap * column / columns
        outRect.right = gap - gap * (column + 1) / columns
        if (position >= columns) outRect.top = gap
    }
}
