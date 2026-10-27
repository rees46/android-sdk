package com.personalization.demo.shop

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.isVisible
import com.personalization.R as SdkR
import com.personalization.ui.widgets.PersonalizationCategoryScreen

/**
 * A category: the shop's search field on top ([ShopSearchBar]), under it the kit's category screen.
 * The screen runs `getCategory` itself — pages, filters, sorting, the empty state and errors with
 * Retry — and tracks the category view once. Its title is the category name, back goes down the
 * shop's stack, sorting is the shop's picker ([pickSort]), "Add to cart" tracks the cart and shows
 * the toast.
 */
internal class ShopCategory(
    private val shop: DemoShop,
    route: ShopRoute.Category
) : ShopScreen {

    private val context: Context = shop.context
    private val root = LinearLayout(context)
    private val screen = PersonalizationCategoryScreen(context).apply {
        shopId = shop.shopId
        titleText = route.name
        productActionText = ADD_TO_CART_TEXT
        onBack = { shop.close() }
        onSortClick = { pickSort(context, this) }
        onProductClick = { product -> shop.open(ShopRoute.Product(product.id)) }
        onProductAction = { product -> shop.addToCart(this, product.id, product.price) }
        categoryId = route.id
    }
    private val search = ShopSearchBar(shop, root) { searching -> screen.isVisible = !searching }

    override val view: View = root

    init {
        val gap = context.spacing(SdkR.dimen.personalization_spacing_xl)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(context.pageBackground())
        // The open panel is taller than the field: with the screen hidden it scrolls in what is left.
        val bar = ScrollView(context).apply {
            setPadding(gap, gap, gap, 0)
            clipToPadding = false
            addView(search.field, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(screen, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onBack(): Boolean {
        if (search.onBack()) return true
        if (!screen.isFiltersOpen) return false
        screen.closeFilters()
        return true
    }
}
