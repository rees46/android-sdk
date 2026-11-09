package com.personalization.demo.shop

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.personalization.R as SdkR
import com.personalization.api.responses.product.Product
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationBadge
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationProductImage
import com.personalization.ui.widgets.PersonalizationRecommenderBlock

/**
 * A product page, no app bar and no search: `getProductInfo(id)`, opening it tracks the product view.
 *
 * - Hero on the card surface, its bottom corners of the kit's modal radius, running up under the status
 *   bar: the photo (`image_url`, otherwise `picture`) fitted in a square with 32 at the sides, then brand,
 *   name and the price with the old one and the discount (by the card rules, see [toCard]). A round back
 *   button sits over its top-left corner and stays there while the page scrolls.
 * - Under it the recommender blocks for this product — similar, bought with it and popular — each titled
 *   by the server and hidden while it has no products, 24 apart.
 * - A panel pinned to the bottom: the price on the left, the kit's "Add to cart" in the rest of the width.
 *
 * While the product loads — the kit's loader in the middle of the screen; a failed load — the error with
 * Retry, also in the middle.
 */
internal class ShopProduct(
    private val shop: DemoShop,
    private val route: ShopRoute.Product,
    trackView: Boolean
) : ShopScreen {

    private val context: Context = shop.context
    private val image = PersonalizationProductImage(context).apply {
        // The whole photo on the hero's own surface: fitted, without the placeholder fill behind it.
        background = null
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val photo = FrameLayout(context)
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
        SdkR.color.personalization_text_secondary, strikeThrough = true
    )
    private val discount = PersonalizationBadge(context).apply {
        size = PersonalizationBadge.Size.MD
        badgeView = PersonalizationBadge.BadgeView.DANGER
    }
    private val panelPrice = context.kitText(
        SdkR.dimen.personalization_font_size_lg, SdkR.dimen.personalization_line_height_lg,
        SdkR.color.personalization_text_primary, emphasized = true
    )
    private val panelOldPrice = context.kitText(
        SdkR.dimen.personalization_font_size_sm, SdkR.dimen.personalization_line_height_sm,
        SdkR.color.personalization_text_secondary, strikeThrough = true
    )
    private val addToCart = PersonalizationButton(context).apply {
        size = PersonalizationButton.Size.LG
        buttonView = PersonalizationButton.ButtonView.PRIMARY
        text = ADD_TO_CART_TEXT
    }
    private val scroll = ScrollView(context)
    private val panel = LinearLayout(context)
    private val panelRow = LinearLayout(context)
    private val back = context.circleBackButton { shop.close() }
    private val status = ShopStatus(context)
    private var request = 0

    override val view: View = FrameLayout(context)

    override val topSurface: Int = SdkR.color.personalization_background_card

    init {
        val root = view as FrameLayout
        root.setBackgroundColor(context.pageBackground())

        val page = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        page.addView(hero(), matchWidth())
        val edge = context.spacing(SdkR.dimen.personalization_spacing_padding_x)
        val blockGap = context.spacing(SdkR.dimen.personalization_spacing_xl3)
        for (code in listOf(SIMILAR_BLOCK_CODE, ALSO_BOUGHT_BLOCK_CODE, SHOP_BLOCK_CODE)) {
            page.addView(productBlock(code), matchWidth(top = blockGap, sides = edge))
        }
        // The panel is under the page, not over it: the page ends 24 above it.
        page.setPadding(0, 0, 0, blockGap)
        scroll.isFillViewport = true
        scroll.isVisible = false
        scroll.addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        buildPanel()
        panel.isVisible = false

        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(panel, matchWidth())
        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(back)
        (back.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.TOP or Gravity.START
            marginStart = edge
        }

        val photoSides = context.spacing(SdkR.dimen.personalization_spacing_xl4)
        val small = context.spacing(SdkR.dimen.personalization_spacing_md)
        val panelVertical = context.spacing(SdkR.dimen.personalization_spacing_lg)
        root.onSystemBars { top, bottom ->
            photo.setPadding(photoSides, top + small, photoSides, small)
            back.updateLayoutParams<FrameLayout.LayoutParams> { topMargin = top }
            panelRow.setPadding(edge, panelVertical, edge, panelVertical + bottom)
        }

        if (trackView) shop.sdk.tracking.productView(route.id)
        load()
    }

    /**
     * Photo, then — 20 at the sides — brand → 4 → name → 12 → price, old price and the discount 8 apart,
     * 20 below.
     */
    private fun hero(): View {
        val hero = LinearLayout(context)
        hero.orientation = LinearLayout.VERTICAL
        val radius = PersonalizationTheme.radius(context, SdkR.dimen.personalization_radius_modal)
        hero.background = context.surface(SdkR.color.personalization_background_card, 0f, 0f, radius, radius)

        photo.addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        hero.addView(photo, matchWidth())

        val sides = context.spacing(SdkR.dimen.personalization_spacing_xl2)
        val small = context.spacing(SdkR.dimen.personalization_spacing_md)
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sides, 0, sides, sides)
        }
        info.addView(brand, matchWidth())
        info.addView(name, matchWidth(top = context.spacing(SdkR.dimen.personalization_spacing_sm)))
        info.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(price)
            addView(oldPrice, wrap(start = small))
            addView(discount, wrap(start = small))
        }, matchWidth(top = context.spacing(SdkR.dimen.personalization_spacing_lg)))
        hero.addView(info, matchWidth())
        return hero
    }

    /** Card surface, a subtle line on top; the price on the left, "Add to cart" in the rest, 16 apart. */
    private fun buildPanel() {
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(PersonalizationTheme.color(context, SdkR.color.personalization_background_card))
        panel.addView(View(context).apply {
            setBackgroundColor(PersonalizationTheme.color(context, SdkR.color.personalization_line_generic_subtle))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(1)))

        panelRow.orientation = LinearLayout.HORIZONTAL
        panelRow.gravity = Gravity.CENTER_VERTICAL
        val prices = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(panelPrice)
            addView(panelOldPrice)
        }
        panelRow.addView(prices, wrap(start = 0))
        panelRow.addView(addToCart, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = context.spacing(SdkR.dimen.personalization_spacing_xl)
        })
        panel.addView(panelRow, matchWidth())
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
        // Gson leaves null in fields the answer lacks, the non-null types notwithstanding.
        val original: String? = product.imageUrl
        val resized: String? = product.picture
        status.hide()
        Glide.with(image.imageView)
            .load(original?.takeIf { it.isNotEmpty() } ?: resized?.takeIf { it.isNotEmpty() })
            .into(image.imageView)
        brand.text = card.brand
        brand.isVisible = !card.brand.isNullOrEmpty()
        name.text = card.name
        price.text = card.price
        panelPrice.text = card.price
        oldPrice.text = card.oldPrice
        oldPrice.isVisible = !card.oldPrice.isNullOrEmpty()
        panelOldPrice.text = card.oldPrice
        panelOldPrice.isVisible = oldPrice.isVisible
        discount.text = card.discount
        discount.isVisible = !card.discount.isNullOrEmpty()
        addToCart.setOnClickListener { shop.addToCart(addToCart, card.id, product.price) }
        scroll.isVisible = true
        panel.isVisible = true
    }

    /** A recommender block for this product, titled by the server; hidden until it has products. */
    private fun productBlock(blockCode: String): View = PersonalizationRecommenderBlock(context).apply {
        shopId = shop.shopId
        itemId = route.id
        code = blockCode
        actionText = ADD_TO_CART_TEXT
        onProductClick = { product -> shop.open(ShopRoute.Product(product.id)) }
        onProductAction = { product -> shop.addToCart(this, product.id) }
    }

    private fun matchWidth(top: Int = 0, sides: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top
            marginStart = sides
            marginEnd = sides
        }

    private fun wrap(start: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = start
        }
}
