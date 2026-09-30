package com.personalization.ui.components

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
 *
 * Весь каталог — одна лента [RecyclerView]: заголовок, карточки и нижние элементы —
 * её строки. Поэтому его не вкладывают в прокрутку, он прокручивается сам: карточки
 * создаются только под видимую часть и переиспользуются, сколько бы страниц ни
 * догрузилось. Внутри чужой вертикальной прокрутки (высота по содержимому) он
 * раскладывается целиком, как обычный блок.
 */
@InternalPersonalizationUiApi
class PersonalizationCatalog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val emptyState = PersonalizationEmptyState(context)
    private val loader = PersonalizationLoader(context)
    private val count = PersonalizationCount(context)
    private val loadMoreButton = PersonalizationButton(context)

    private val header = SlotsAdapter()
    private var productsAdapter = PersonalizationProductsAdapter(PersonalizationProductCard.Type.GRID)
    private val footer = SlotsAdapter()
    private val concat = ConcatAdapter(header, productsAdapter, footer)
    private val grid = GridLayoutManager(context, COLUMNS)

    var view: PersonalizationProductsGrid.View = PersonalizationProductsGrid.View.GRID
        set(value) {
            if (field == value) return
            field = value
            rebuildProducts()
        }

    var products: List<PersonalizationProduct>
        get() = productsAdapter.items
        set(value) {
            productsAdapter.items = value
            applySlots()
        }

    /** Текст пустой выдачи. `null` — без пустого состояния, плитка остаётся на месте. */
    var emptyText: CharSequence? = null
        set(value) {
            field = value
            emptyState.text = value
            applySlots()
        }

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

    /** Нажатие на карточку — открыть товар. */
    var onProductClick: ((PersonalizationProduct) -> Unit)?
        get() = productsAdapter.onClick
        set(value) {
            productsAdapter.onClick = value
        }

    /** Пропорция картинок карточек, см. [PersonalizationProductCard.imageAspect]. */
    var imageAspect: PersonalizationProductImage.Aspect
        get() = productsAdapter.imageAspect
        set(value) {
            productsAdapter.imageAspect = value
        }

    /**
     * Во фреймах с лоадером (296:3544, 299:6606) счётчика и кнопки нет — на время
     * загрузки лоадер встаёт на их место.
     */
    var isLoading: Boolean = false
        set(value) {
            field = value
            applySlots()
        }

    /** Подпись кнопки «загрузить ещё». `null` — без кнопки. */
    var loadMoreText: CharSequence? = null
        set(value) {
            field = value
            loadMoreButton.text = value
            applySlots()
        }

    private var hasCount = false

    var onLoadMore: (() -> Unit)? = null

    init {
        // Отступы хоста — поля ленты, а не рамка: строки прокручиваются под ними.
        clipToPadding = false
        // Строки-одиночки держат один экземпляр своей view. Анимация изменения создала бы
        // второй холдер того же типа, пока жив первый, и view пришлось бы делить.
        itemAnimator = null

        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (isProduct(position)) 1 else grid.spanCount
        }
        layoutManager = grid
        adapter = concat
        addItemDecoration(Gaps())

        loadMoreButton.size = PersonalizationButton.Size.MD
        loadMoreButton.buttonView = PersonalizationButton.ButtonView.SECONDARY
        loadMoreButton.iconStart = R.drawable.personalization_ic_arrow_rotate_cw
        loadMoreButton.setOnClickListener { onLoadMore?.invoke() }

        applySlots()
    }

    /** Заголовок над товарами: выдача или категория. `null` — убрать. */
    fun setHeader(view: View?) {
        header.slots = listOfNotNull(view?.let { Slot(TYPE_HEADER, it) })
        invalidateItemDecorations()
    }

    /** Счётчик «показано N из M». Слова — параметры. `prefix == null` — скрыть. */
    fun setCount(prefix: CharSequence?, shown: Int, separator: CharSequence, total: Int) {
        hasCount = prefix != null
        if (prefix != null) count.set(prefix, shown, separator, total)
        applySlots()
    }

    private fun applySlots() {
        val empty = productsAdapter.items.isEmpty() && !emptyText.isNullOrEmpty()
        footer.slots = buildList {
            if (empty) add(Slot(TYPE_EMPTY, emptyState))
            // Лоадер в макете — по центру строки; на всю ширину он бы прижал кольцо к левому краю.
            if (isLoading) add(Slot(TYPE_LOADER, loader, centered = true))
            if (hasCount && !isLoading) add(Slot(TYPE_COUNT, count))
            if (!loadMoreText.isNullOrEmpty() && !isLoading) add(Slot(TYPE_LOAD_MORE, loadMoreButton))
        }
    }

    private fun rebuildProducts() {
        val previous = productsAdapter
        productsAdapter = PersonalizationProductsAdapter(
            if (view == PersonalizationProductsGrid.View.GRID) {
                PersonalizationProductCard.Type.GRID
            } else {
                PersonalizationProductCard.Type.LIST
            }
        ).also {
            it.items = previous.items
            it.imageLoader = previous.imageLoader
            it.onAction = previous.onAction
            it.onClick = previous.onClick
            it.imageAspect = previous.imageAspect
        }
        concat.removeAdapter(previous)
        concat.addAdapter(1, productsAdapter)
        grid.spanCount = if (view == PersonalizationProductsGrid.View.GRID) COLUMNS else 1
        invalidateItemDecorations()
    }

    private fun isProduct(position: Int): Boolean {
        val first = header.itemCount
        return position >= first && position < first + productsAdapter.itemCount
    }

    /** Одиночная строка ленты: своя view и свой тип, у каждой вида строки — ровно одна. */
    private class Slot(val type: Int, val view: View, val centered: Boolean = false)

    private class SlotsAdapter : Adapter<SlotsAdapter.Holder>() {

        var slots: List<Slot> = emptyList()
            set(value) {
                val previous = field
                field = value
                if (previous.map { it.view } == value.map { it.view }) return
                // Точечно, а не notifyDataSetChanged: ConcatAdapter поднял бы его до всей ленты
                // и перепривязал видимые карточки на каждое включение лоадера.
                notifyItemRangeRemoved(0, previous.size)
                notifyItemRangeInserted(0, value.size)
            }

        class Holder(val frame: FrameLayout) : ViewHolder(frame)

        override fun getItemCount(): Int = slots.size

        override fun getItemViewType(position: Int): Int = slots[position].type

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(FrameLayout(parent.context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            })

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val slot = slots[position]
            if (slot.view.parent === holder.frame) return
            (slot.view.parent as? ViewGroup)?.removeView(slot.view)
            holder.frame.removeAllViews()
            holder.frame.addView(
                slot.view,
                if (slot.centered) {
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_HORIZONTAL
                    )
                } else {
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                }
            )
        }
    }

    /**
     * Шаги ленты. Между блоками — 12 (шаг блока каталога), внутри плитки — 16 по обеим
     * осям: половина зазора у внутренних краёв колонок, полный сверху со второй строки.
     */
    private inner class Gaps : ItemDecoration() {
        private val block = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        private val tile = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: State) {
            val position = parent.getChildAdapterPosition(view)
            if (position == NO_POSITION) return
            if (!isProduct(position)) {
                if (position > 0) outRect.top = block
                return
            }
            val columns = grid.spanCount
            val index = position - header.itemCount
            val column = index % columns
            outRect.left = tile * column / columns
            outRect.right = tile - tile * (column + 1) / columns
            outRect.top = if (index >= columns) tile else if (header.itemCount > 0) block else 0
        }
    }

    private companion object {
        const val COLUMNS = 2
        const val TYPE_HEADER = 1
        const val TYPE_EMPTY = 2
        const val TYPE_LOADER = 3
        const val TYPE_COUNT = 4
        const val TYPE_LOAD_MORE = 5
    }
}
