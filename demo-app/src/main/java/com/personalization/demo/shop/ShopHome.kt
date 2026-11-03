package com.personalization.demo.shop

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.isVisible
import com.personalization.R as SdkR
import com.personalization.api.responses.search.Category
import com.personalization.demo.STORIES_API_V3_DOMAIN
import com.personalization.demo.demoShareUrl
import com.personalization.ui.components.PersonalizationSuggestionRow
import com.personalization.ui.components.PersonalizationTitle
import com.personalization.ui.widgets.PersonalizationRecommenderBlock
import com.personalization.ui.widgets.PersonalizationStories

/**
 * Shop home: the instant search field on top ([ShopSearchBar]), then the stories bar, the recommender
 * block and the shop's top-level categories. While the field's panel is open the rest of the page is
 * out of the way.
 */
internal class ShopHome(private val shop: DemoShop) : ShopScreen {

    private val context: Context = shop.context
    private val sections = LinearLayout(context)
    private val categories = LinearLayout(context)
    private val categoriesStatus = ShopStatus(context)
    private val page = LinearLayout(context)
    private val search = ShopSearchBar(shop, page) { searching -> sections.isVisible = !searching }
    private var request = 0

    override val view: View = ScrollView(context).apply {
        isFillViewport = true
        setBackgroundColor(context.pageBackground())
    }

    init {
        val gap = context.spacing(SdkR.dimen.personalization_spacing_xl)
        val blockGap = context.spacing(SdkR.dimen.personalization_spacing_xl3)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(gap, gap, gap, gap)
        sections.orientation = LinearLayout.VERTICAL
        categories.orientation = LinearLayout.VERTICAL

        page.addView(search.field, matchWidth())
        sections.addView(stories(), matchWidth(top = blockGap))
        sections.addView(recommenderBlock(), matchWidth(top = blockGap))
        sections.addView(PersonalizationTitle(context).apply { text = "Categories" }, matchWidth(top = blockGap))
        sections.addView(categories, matchWidth(top = context.spacing(SdkR.dimen.personalization_spacing_md)))
        sections.addView(categoriesStatus, matchWidth())
        page.addView(sections, matchWidth())
        (view as ScrollView).addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        loadCategories()
    }

    override fun onBack(): Boolean = search.onBack()

    /**
     * The stories block the demo uses everywhere, on API v3 with "Share": the shared link carries the
     * demo scheme, so it opens the demo (see the stories links of the manifest). A product tapped in a
     * story opens its page here.
     */
    private fun stories(): View = PersonalizationStories(context).apply {
        shopId = shop.shopId
        code = shop.storiesCode
        apiV3Domain = STORIES_API_V3_DOMAIN
        shareUrlTransform = ::demoShareUrl
        onClickListener = shop.storiesClicks
    }

    private fun recommenderBlock(): View = PersonalizationRecommenderBlock(context).apply {
        shopId = shop.shopId
        code = SHOP_BLOCK_CODE
        actionText = ADD_TO_CART_TEXT
        onProductClick = { product -> shop.open(ShopRoute.Product(product.id)) }
        onProductAction = { product -> shop.addToCart(this, product.id) }
    }

    /** Top-level categories (no parent) out of the `categories` facet of the products listing. */
    private fun loadCategories() {
        val seq = ++request
        categoriesStatus.loading()
        shop.sdk.productsManager.getProductsList(
            limit = 1,
            onSuccess = { response -> onMain { if (seq == request) showCategories(response.categories) } },
            onError = { _, _ -> onMain { if (seq == request) categoriesStatus.failed(::loadCategories) } }
        )
    }

    private fun showCategories(all: List<Category>?) {
        categoriesStatus.hide()
        categories.removeAllViews()
        val rowPadding = context.spacing(SdkR.dimen.personalization_spacing_lg)
        all.orEmpty().forEach { category ->
            // Gson leaves null in missing fields, the non-null types notwithstanding.
            val id: String? = category.id
            val name: String? = category.name
            val parent: String? = category.parent
            if (id.isNullOrEmpty() || !parent.isNullOrEmpty()) return@forEach
            categories.addView(PersonalizationSuggestionRow(context).apply {
                kind = PersonalizationSuggestionRow.Kind.CATEGORY
                title = name ?: id
                setPadding(0, rowPadding, 0, rowPadding)
                setOnClickListener { shop.open(ShopRoute.Category(id, name ?: id)) }
            }, matchWidth())
        }
    }

    private fun matchWidth(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top
        }
}
