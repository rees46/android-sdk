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

/**
 * Горизонтальная лента карточек товара.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Card, символ Products Carousel (203:6296):
 * карточки типа Carousel с шагом 16.
 *
 * Собрана на RecyclerView, чтобы карточки переиспользовались; сами карточки —
 * [PersonalizationProductCard]. Картинки грузит хост через [imageLoader].
 */
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

    /** Рисовать ли рейтинг на карточках; выключенный прячет его, даже если оценка есть. */
    var showRating: Boolean
        get() = productsAdapter.showRating
        set(value) {
            productsAdapter.showRating = value
        }

    /** Индекс первой видимой карточки: по нему блок товаров двигает точки. */
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
            val previous = field
            field = value
            // Следующая страница выдачи дописывается в конец: уже показанные карточки не
            // перепривязываются и не перезапрашивают картинки. Иначе каждая страница
            // перерисовывала бы весь список заново.
            val appended = previous.isNotEmpty() && value.size > previous.size &&
                value.subList(0, previous.size) == previous
            if (appended) {
                // Новая карточка может оказаться выше прежних — тогда показанные дотягиваются.
                val grew = tallest.keys.toList().any { width ->
                    val before = tallest.getValue(width).height
                    tallestHeight(width) > before
                }
                notifyItemRangeInserted(previous.size, value.size - previous.size)
                if (grew) notifyItemRangeChanged(0, previous.size, PAYLOAD_HEIGHT)
            } else {
                tallest.clear()
                notifyDataSetChanged()
            }
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
            tallest.clear()
            notifyDataSetChanged()
        }

    /** Рисовать ли рейтинг товара; выключенный прячет ряд, даже если оценка есть. */
    var showRating: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            tallest.clear()
            // Меняется только ряд рейтинга: с пометкой карточка не перепривязывается целиком
            // и не перезапрашивает картинку.
            notifyItemRangeChanged(0, itemCount, PAYLOAD_RATING)
        }

    /**
     * Самая высокая карточка по ширине: сколько товаров уже померено и их наибольшая высота.
     * RecyclerView не выравнивает соседей по ряду, поэтому карточки колонок тянутся до самой
     * высокой в списке (как на iOS), а цена с кнопкой у них прижаты к низу.
     */
    private class Tallest(val measured: Int, val height: Int)

    private val tallest = HashMap<Int, Tallest>()
    private var measuringContext: Context? = null
    private var measuringCard: PersonalizationProductCard? = null

    /** Мерит вне экрана только ещё не померенные товары; следующая страница досчитывается. */
    private fun tallestHeight(width: Int): Int {
        val known = tallest[width]
        if (known != null && known.measured >= items.size) return known.height
        val context = measuringContext ?: return 0
        val card = measuringCard ?: newCard(context).also { measuringCard = it }
        var height = known?.height ?: 0
        for (index in (known?.measured ?: 0) until items.size) {
            bindTexts(card, items[index])
            card.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            height = maxOf(height, card.measuredHeight)
        }
        tallest[width] = Tallest(items.size, height)
        return height
    }

    class Holder(val card: PersonalizationProductCard) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        measuringContext = parent.context
        val card = newCard(parent.context).apply {
            if (type != PersonalizationProductCard.Type.LIST) tallestFor = this@PersonalizationProductsAdapter::tallestHeight
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

    /** Карточка раскладки; две строки под название — ряд рейтинга у соседей на одной высоте. */
    private fun newCard(context: Context) = PersonalizationProductCard(context).apply {
        type = this@PersonalizationProductsAdapter.type
        if (type != PersonalizationProductCard.Type.LIST) nameMinLines = 2
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        when {
            payloads.isEmpty() -> onBindViewHolder(holder, position)
            payloads.all { it == PAYLOAD_RATING || it == PAYLOAD_HEIGHT } -> {
                if (PAYLOAD_RATING in payloads) bindRating(holder.card, items[position])
                holder.card.requestLayout()
            }
            else -> onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = items[position]
        holder.card.apply {
            bindTexts(this, product)
            onAction = { this@PersonalizationProductsAdapter.onAction?.invoke(product) }
            onClick = this@PersonalizationProductsAdapter.onClick?.let { click -> { click(product) } }
            image.imageView.setImageDrawable(null)
            imageLoader?.invoke(image.imageView, product)
        }
    }

    /** Всё, от чего зависит высота карточки; картинку и обработчики ставит привязка. */
    private fun bindTexts(card: PersonalizationProductCard, product: PersonalizationProduct) {
        card.imageAspect = imageAspect
        card.brand = product.brand
        card.name = product.name
        card.price = product.price
        card.oldPrice = product.oldPrice
        card.discount = product.discount
        card.actionText = product.actionText
        bindRating(card, product)
    }

    private fun bindRating(card: PersonalizationProductCard, product: PersonalizationProduct) {
        card.setRating(if (showRating) product.ratingValue else null, product.reviews)
    }

    private companion object {
        const val PAYLOAD_RATING = "rating"
        const val PAYLOAD_HEIGHT = "height"
    }
}
