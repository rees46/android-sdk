package com.personalization.ui.components

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Горизонтальная лента карточек товара.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Card, символ Products Carousel (203:6296):
 * карточки типа Carousel с шагом 16.
 *
 * Собрана на RecyclerView, чтобы карточки переиспользовались; сами карточки —
 * [PersonalizationProductCard]. Картинки грузит хост через [imageLoader].
 */
@InternalPersonalizationUiApi
class PersonalizationProductsCarousel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val productsAdapter = PersonalizationProductsAdapter(PersonalizationProductCard.Type.CAROUSEL)

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

    /** Индекс первой видимой карточки: по нему Recommender-блок двигает точки. */
    var onFirstVisibleChanged: ((Int) -> Unit)? = null

    init {
        layoutManager = LinearLayoutManager(context, HORIZONTAL, false)
        adapter = productsAdapter
        clipToPadding = false
        addItemDecoration(
            PersonalizationGapDecoration(
                resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl),
                horizontal = true
            )
        )
        addOnScrollListener(object : OnScrollListener() {
            private var last = RecyclerView.NO_POSITION
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val first = (layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                if (first != RecyclerView.NO_POSITION && first != last) {
                    last = first
                    onFirstVisibleChanged?.invoke(first)
                }
            }
        })
    }
}

/** Шаг между карточками: отступ ставится всем, кроме первой. */
internal class PersonalizationGapDecoration(
    private val gap: Int,
    private val horizontal: Boolean
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.getChildAdapterPosition(view) == 0) return
        if (horizontal) outRect.left = gap else outRect.top = gap
    }
}

/** Общий адаптер трёх раскладок: тип карточки задаёт раскладка. */
internal class PersonalizationProductsAdapter(
    private val type: PersonalizationProductCard.Type
) : RecyclerView.Adapter<PersonalizationProductsAdapter.Holder>() {

    var items: List<PersonalizationProduct> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var imageLoader: ((ImageView, PersonalizationProduct) -> Unit)? = null
    var onAction: ((PersonalizationProduct) -> Unit)? = null
    var onClick: ((PersonalizationProduct) -> Unit)? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var imageAspect: PersonalizationProductImage.Aspect = PersonalizationProductImage.Aspect.SQUARE
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class Holder(val card: PersonalizationProductCard) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val card = PersonalizationProductCard(parent.context).apply {
            this.type = this@PersonalizationProductsAdapter.type
            // Соседи по ряду не выравниваются RecyclerView — см. nameMinLines.
            if (this.type != PersonalizationProductCard.Type.LIST) nameMinLines = 2
            layoutParams = ViewGroup.LayoutParams(
                if (this@PersonalizationProductsAdapter.type == PersonalizationProductCard.Type.CAROUSEL) {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                } else {
                    ViewGroup.LayoutParams.MATCH_PARENT
                },
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return Holder(card)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = items[position]
        holder.card.apply {
            imageAspect = this@PersonalizationProductsAdapter.imageAspect
            brand = product.brand
            name = product.name
            price = product.price
            oldPrice = product.oldPrice
            discount = product.discount
            actionText = product.actionText
            setRating(product.ratingValue, product.reviews)
            onAction = { this@PersonalizationProductsAdapter.onAction?.invoke(product) }
            onClick = this@PersonalizationProductsAdapter.onClick?.let { click -> { click(product) } }
            image.imageView.setImageDrawable(null)
            imageLoader?.invoke(image.imageView, product)
        }
    }
}
