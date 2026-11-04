package com.personalization.demo.shop

import android.content.Context
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import com.personalization.R as SdkR
import com.personalization.api.responses.search.Category
import com.personalization.demo.DEMO_POPUPS
import com.personalization.demo.STORIES_API_V3_DOMAIN
import com.personalization.demo.demoShareUrl
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationSuggestionRow
import com.personalization.ui.components.PersonalizationTitle
import com.personalization.ui.widgets.PersonalizationRecommenderBlock
import com.personalization.ui.widgets.PersonalizationStories

/**
 * Shop home: the header — "Shop", the round popup and search buttons — then the stories bar, the
 * recommender block and the shop's top-level categories, a group of rows on the card surface under the
 * same kit title the recommender block has.
 *
 * The search button puts the page in the search mode ([ShopSearchBar]): the kit's instant search field
 * takes the header's place, focused, with its panel under it, and the rest of the page is out of the
 * way until "Cancel" or back.
 *
 * Rhythm: page edges 16; header → stories 12, then 24 between the sections, 32 at the bottom. The
 * stories bar runs edge to edge and keeps its own 16 at the sides, so the covers line up with the rest
 * of the page.
 */
internal class ShopHome(private val shop: DemoShop) : ShopScreen {

    private val context: Context = shop.context
    private val sections = LinearLayout(context)
    private val categoriesSection = LinearLayout(context)
    private val categories = LinearLayout(context)
    private val categoriesStatus = ShopStatus(context)
    private val page = LinearLayout(context)
    private val header = LinearLayout(context)
    private val search = ShopSearchBar(shop, page) { searching ->
        header.isVisible = !searching
        searchField.isVisible = searching
        sections.isVisible = !searching
    }
    private val searchField: View get() = search.field
    private var request = 0

    override val view: View = ScrollView(context).apply {
        isFillViewport = true
        setBackgroundColor(context.pageBackground())
    }

    init {
        val edge = context.spacing(SdkR.dimen.personalization_spacing_padding_x)
        val sectionGap = context.spacing(SdkR.dimen.personalization_spacing_xl3)
        page.orientation = LinearLayout.VERTICAL
        sections.orientation = LinearLayout.VERTICAL

        page.addView(header(), block(edge))
        searchField.isVisible = false
        page.addView(searchField, block(edge))
        sections.addView(stories(), block(0, top = context.spacing(SdkR.dimen.personalization_spacing_lg)))
        sections.addView(recommenderBlock(), block(edge, top = sectionGap))
        sections.addView(categoriesSection(), block(edge, top = sectionGap))
        page.addView(sections, block(0))
        (view as ScrollView).addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val top = context.spacing(SdkR.dimen.personalization_spacing_md)
        val bottom = context.spacing(SdkR.dimen.personalization_spacing_xl4)
        view.onSystemBars { barTop, barBottom -> page.setPadding(0, barTop + top, 0, barBottom + bottom) }
        loadCategories()
    }

    override fun onBack(): Boolean = search.onBack()

    /**
     * "Shop" on the left; on the right, centred on the title's line, the round buttons: a popup of the
     * shop shown by hand ([showPopup]) and search, 8 apart.
     */
    private fun header(): View {
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(
            context.kitText(
                SdkR.dimen.personalization_font_size_xl3, SdkR.dimen.personalization_line_height_xl3,
                SdkR.color.personalization_text_primary, emphasized = true
            ).apply { text = SHOP_TITLE_TEXT },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        lateinit var popupButton: View
        popupButton = context.circleButton(
            SdkR.drawable.personalization_ic_rosette, SdkR.color.personalization_background_card, "Show popup"
        ) { showPopup(popupButton) }
        header.addView(popupButton, LinearLayout.LayoutParams(popupButton.layoutParams))
        val searchButton = context.circleButton(
            SdkR.drawable.personalization_ic_magnifier, SdkR.color.personalization_background_card, "Search"
        ) { search.start() }
        header.addView(searchButton, LinearLayout.LayoutParams(searchButton.layoutParams).apply {
            marginStart = context.spacing(SdkR.dimen.personalization_spacing_md)
        })
        return header
    }

    /** The menu of the demo's popups at [anchor]; the picked one is shown by hand, whatever its trigger. */
    private fun showPopup(anchor: View) {
        PopupMenu(context, anchor).apply {
            DEMO_POPUPS.entries.forEachIndexed { index, (id, title) -> menu.add(Menu.NONE, id, index, title) }
            setOnMenuItemClickListener { item ->
                shop.sdk.popups.show(item.itemId)
                true
            }
        }.show()
    }

    /**
     * The stories block the demo uses everywhere, on API v3 with "Share": the shared link carries the
     * demo scheme, so it opens the demo (see the stories links of the manifest). A product tapped in a
     * story opens its page here. The bar keeps its side padding only: the page sets the gaps above and
     * below it.
     */
    private fun stories(): View = PersonalizationStories(context).apply {
        shopId = shop.shopId
        code = shop.storiesCode
        apiV3Domain = STORIES_API_V3_DOMAIN
        shareUrlTransform = ::demoShareUrl
        onClickListener = shop.storiesClicks
        promoCopiedText = "Code copied"
        val edge = context.spacing(SdkR.dimen.personalization_spacing_padding_x)
        bar.setPadding(edge, 0, edge, 0)
    }

    private fun recommenderBlock(): View = PersonalizationRecommenderBlock(context).apply {
        shopId = shop.shopId
        code = SHOP_BLOCK_CODE
        actionText = ADD_TO_CART_TEXT
        onProductClick = { product -> shop.open(ShopRoute.Product(product.id)) }
        onProductAction = { product -> shop.addToCart(this, product.id) }
    }

    /**
     * "Categories": the kit title, under it the group — the card surface with the card radius, rows
     * split by subtle lines — or, while it loads or after a failure, the loader or the error with Retry.
     * With no categories the section is not shown at all.
     */
    private fun categoriesSection(): View {
        categoriesSection.orientation = LinearLayout.VERTICAL
        categoriesSection.addView(PersonalizationTitle(context).apply { text = "Categories" }, block(0))
        val radius = PersonalizationTheme.radius(context, SdkR.dimen.personalization_radius_card)
        categories.orientation = LinearLayout.VERTICAL
        categories.background = context.surface(SdkR.color.personalization_background_card, radius, radius, radius, radius)
        // The rows' ripples stay inside the rounded corners.
        categories.clipToOutline = true
        categories.isVisible = false
        categoriesSection.addView(categories, block(0, top = context.spacing(SdkR.dimen.personalization_spacing_lg)))
        categoriesSection.addView(categoriesStatus, block(0))
        return categoriesSection
    }

    /** Top-level categories (no parent) out of the `categories` facet of the products listing. */
    private fun loadCategories() {
        val seq = ++request
        categoriesSection.isVisible = true
        categories.isVisible = false
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
        val edge = context.spacing(SdkR.dimen.personalization_spacing_padding_x)
        val vertical = context.spacing(SdkR.dimen.personalization_spacing_lg)
        all.orEmpty().forEach { category ->
            // Gson leaves null in missing fields, the non-null types notwithstanding.
            val id: String? = category.id
            val name: String? = category.name
            val parent: String? = category.parent
            if (id.isNullOrEmpty() || !parent.isNullOrEmpty()) return@forEach
            if (categories.childCount > 0) categories.addView(divider(), dividerParams())
            categories.addView(PersonalizationSuggestionRow(context).apply {
                kind = PersonalizationSuggestionRow.Kind.CATEGORY
                title = name ?: id
                minimumHeight = context.dp(ROW_MIN_HEIGHT_DP)
                setPadding(edge, vertical, vertical, vertical)
                setSelectableBackground()
                setOnClickListener { shop.open(ShopRoute.Category(id, name ?: id)) }
            }, block(0))
        }
        categories.isVisible = categories.childCount > 0
        categoriesSection.isVisible = categories.isVisible
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(PersonalizationTheme.color(context, SdkR.color.personalization_line_generic_subtle))
    }

    private fun dividerParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(1))

    /** Full width less [sides] on both sides, [top] above. */
    private fun block(sides: Int, top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = sides
            marginEnd = sides
            topMargin = top
        }
}
