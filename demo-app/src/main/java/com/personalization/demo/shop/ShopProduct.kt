package com.personalization.demo.shop

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.personalization.R as SdkR
import com.personalization.api.responses.product.Product
import com.personalization.ui.components.PersonalizationBadge
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationProductImage
import com.personalization.ui.widgets.PersonalizationRecommenderBlock

/**
 * A product page: `getProductInfo(id)` — image, brand, name, price with the old one and the discount
 * (by the card rules, see [toCard]) and "Add to cart". Opening it tracks the product view. Under it —
 * the recommender block for this product, "Similar products", shown with the product. While the
 * product loads — the kit's loader; a failed load — text and Retry.
 */
internal class ShopProduct(
    private val shop: DemoShop,
    private val route: ShopRoute.Product,
    trackView: Boolean
) : ShopScreen {

    private val context: Context = shop.context
    private val image = PersonalizationProductImage(context)
    private val brand = context.kitText(
        SdkR.dimen.personalization_font_size_sm, SdkR.dimen.personalization_line_height_sm,
        SdkR.color.personalization_text_secondary
    )
    private val name = context.kitText(
        SdkR.dimen.personalization_font_size_xl, SdkR.dimen.personalization_line_height_xl,
        SdkR.color.personalization_text_primary, emphasized = true
    )
    private val price = context.kitText(
        SdkR.dimen.personalization_font_size_xl2, SdkR.dimen.personalization_line_height_xl2,
        SdkR.color.personalization_text_primary, emphasized = true
    )
    private val oldPrice = context.kitText(
        SdkR.dimen.personalization_font_size_base, SdkR.dimen.personalization_line_height_base,
        SdkR.color.personalization_text_hint, strikeThrough = true
    )
    private val discount = PersonalizationBadge(context).apply {
        size = PersonalizationBadge.Size.MD
        badgeView = PersonalizationBadge.BadgeView.DANGER
    }
    private val addToCart = PersonalizationButton(context).apply {
        size = PersonalizationButton.Size.LG
        buttonView = PersonalizationButton.ButtonView.PRIMARY
        text = ADD_TO_CART_TEXT
    }
    private val details = LinearLayout(context)
    private val status = ShopStatus(context)
    private var request = 0

    override val view: View = ScrollView(context).apply {
        isFillViewport = true
        setBackgroundColor(context.pageBackground())
    }

    init {
        val gap = context.spacing(SdkR.dimen.personalization_spacing_xl)
        val small = context.spacing(SdkR.dimen.personalization_spacing_md)
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gap, gap, gap, gap)
        }
        page.addView(context.backTitle(null) { shop.close() }, matchWidth())
        page.addView(status, matchWidth())

        details.orientation = LinearLayout.VERTICAL
        details.isVisible = false
        details.addView(image, matchWidth(top = small))
        details.addView(brand, matchWidth(top = gap))
        details.addView(name, matchWidth(top = small))
        details.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(price)
            addView(oldPrice, wrap(start = small))
            addView(discount, wrap(start = small))
        }, matchWidth(top = gap))
        details.addView(addToCart, matchWidth(top = gap))
        details.addView(similar(), matchWidth(top = context.spacing(SdkR.dimen.personalization_spacing_xl4)))
        page.addView(details, matchWidth())
        (view as ScrollView).addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (trackView) shop.sdk.tracking.productView(route.id)
        load()
    }

    private fun load() {
        val seq = ++request
        status.loading()
        shop.sdk.productsManager.getProductInfo(
            itemId = route.id,
            onSuccess = { product -> onMain { if (seq == request) show(product) } },
            onError = { _, _ -> onMain { if (seq == request) status.failed(::load) } }
        )
    }

    private fun show(product: Product) {
        val card = product.toCard()
        status.hide()
        Glide.with(image.imageView).load(card.imageUrl).into(image.imageView)
        brand.text = card.brand
        brand.isVisible = !card.brand.isNullOrEmpty()
        name.text = card.name
        price.text = card.price
        oldPrice.text = card.oldPrice
        oldPrice.isVisible = !card.oldPrice.isNullOrEmpty()
        discount.text = card.discount
        discount.isVisible = !card.discount.isNullOrEmpty()
        addToCart.setOnClickListener { shop.addToCart(addToCart, card.id, product.price) }
        details.isVisible = true
    }

    /** The shop's recommender block for this product; it stays hidden until it has products. */
    private fun similar(): View = PersonalizationRecommenderBlock(context).apply {
        shopId = shop.shopId
        itemId = route.id
        code = SHOP_BLOCK_CODE
        title = "Similar products"
        actionText = ADD_TO_CART_TEXT
        onProductClick = { product -> shop.open(ShopRoute.Product(product.id)) }
        onProductAction = { product -> shop.addToCart(this, product.id) }
    }

    private fun matchWidth(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top
        }

    private fun wrap(start: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = start
        }
}
