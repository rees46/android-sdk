package com.personalization.demo

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.ImageViewCompat
import com.bumptech.glide.Glide
import com.personalization.R as SdkR
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationAccordion
import com.personalization.ui.components.PersonalizationBadge
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationButtonGroup
import com.personalization.ui.components.PersonalizationCatalog
import com.personalization.ui.components.PersonalizationCheckbox
import com.personalization.ui.components.PersonalizationCheckboxWithLabel
import com.personalization.ui.components.PersonalizationCount
import com.personalization.ui.components.PersonalizationDots
import com.personalization.ui.components.PersonalizationEmptyState
import com.personalization.ui.components.PersonalizationFavoritesBadge
import com.personalization.ui.components.PersonalizationFilters
import com.personalization.ui.components.PersonalizationInstantSearch
import com.personalization.ui.components.PersonalizationInputField
import com.personalization.ui.components.PersonalizationLink
import com.personalization.ui.components.PersonalizationListLabel
import com.personalization.ui.components.PersonalizationLoader
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.components.PersonalizationProductCard
import com.personalization.ui.components.PersonalizationProductImage
import com.personalization.ui.components.PersonalizationProductsGrid
import com.personalization.ui.components.PersonalizationRating
import com.personalization.ui.components.PersonalizationRecommenderBlock
import com.personalization.ui.components.PersonalizationSearchResultsTitle
import com.personalization.ui.components.PersonalizationTag
import com.personalization.ui.components.PersonalizationTitle
import com.personalization.ui.widgets.PersonalizationInstantSearchField
import com.personalization.ui.widgets.PersonalizationSearchResultsScreen

/**
 * "UI Kit" tab — the design system, one exhibit per component.
 *
 * "Components" walks through the primitives in every size, view and state the design file defines;
 * "Blocks" shows the compositions built from them (product cards, recommender layouts, instant
 * search, the catalogue, the filters screen). "Search" runs the two data-bound search widgets against
 * the demo shop: the instant search field, and the results screen it opens on submit. "Stories" keeps
 * the stories block through the SDK's Compose wrapper, the counterpart of the "Legacy UI" tab.
 *
 * The kit is classic Views, so each exhibit is an [AndroidView] built from a small factory. Product
 * data is static: the kit does not fetch or format anything itself, and images are loaded by the
 * host — Glide here, through the components' `imageLoader` slot.
 */
@Composable
fun UiKitPane(storiesCode: String, shopId: String) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Components", "Blocks", "Search", "Stories")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            backgroundColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> ComponentsShowcase()
            1 -> BlocksShowcase()
            2 -> SearchShowcase(shopId = shopId)
            else -> ComposeStoriesPane(code = storiesCode, shopId = shopId)
        }
    }
}

@Composable
private fun ComponentsShowcase() {
    Showcase {
        Exhibit("Button — LG / MD / SM, primary · secondary · ghost") { ctx ->
            ctx.column(
                *PersonalizationButton.Size.values().map { size ->
                    ctx.hscroll(ctx.row(
                        ctx.button("Primary", size, PersonalizationButton.ButtonView.PRIMARY),
                        ctx.button("Secondary", size, PersonalizationButton.ButtonView.SECONDARY),
                        ctx.button("Ghost", size, PersonalizationButton.ButtonView.GHOST)
                    ))
                }.toTypedArray()
            )
        }
        Exhibit("Button — icons and disabled") { ctx ->
            ctx.hscroll(ctx.row(
                ctx.button("Filters", PersonalizationButton.Size.MD).apply { iconStart = SdkR.drawable.personalization_ic_equalizer_horizontal },
                ctx.button("Sort", PersonalizationButton.Size.MD, PersonalizationButton.ButtonView.SECONDARY).apply { iconEnd = SdkR.drawable.personalization_ic_arrows_up_down },
                ctx.button(null, PersonalizationButton.Size.MD, PersonalizationButton.ButtonView.GHOST).apply { iconStart = SdkR.drawable.personalization_ic_copy },
                ctx.button("Disabled", PersonalizationButton.Size.MD).apply { isEnabled = false }
            ))
        }
        Exhibit("Button Group — MD and SM") { ctx ->
            ctx.row(ctx.buttonGroup(PersonalizationButtonGroup.Size.MD), ctx.buttonGroup(PersonalizationButtonGroup.Size.SM))
        }
        Exhibit("Input Field — search LG, input MD, select SM, disabled") { ctx ->
            ctx.column(
                PersonalizationInputField(ctx).apply { size = PersonalizationInputField.Size.LG; type = PersonalizationInputField.Type.SEARCH; placeholder = "Search" },
                PersonalizationInputField(ctx).apply { size = PersonalizationInputField.Size.MD; type = PersonalizationInputField.Type.INPUT; text = "Running shoes" },
                PersonalizationInputField(ctx).apply { size = PersonalizationInputField.Size.SM; type = PersonalizationInputField.Type.SELECT; text = "Size 42" },
                PersonalizationInputField(ctx).apply { size = PersonalizationInputField.Size.MD; type = PersonalizationInputField.Type.INPUT; placeholder = "Disabled"; isEnabled = false }
            )
        }
        Exhibit("Title — plain, with trailing action, with leading back") { ctx ->
            ctx.column(
                PersonalizationTitle(ctx).apply { text = "Recommended for you" },
                PersonalizationTitle(ctx).apply {
                    text = "Recently viewed"
                    setTrailing(ctx.button("Show all", PersonalizationButton.Size.SM, PersonalizationButton.ButtonView.GHOST).apply { iconEnd = SdkR.drawable.personalization_ic_angle_large_right })
                },
                PersonalizationTitle(ctx).apply {
                    text = "Sneakers"
                    setLeading(ctx.button(null, PersonalizationButton.Size.SM, PersonalizationButton.ButtonView.GHOST).apply { iconStart = SdkR.drawable.personalization_ic_arrow_left })
                }
            )
        }
        Exhibit("Link — text action next to a field or a label") { ctx ->
            ctx.row(
                PersonalizationLink(ctx).apply { text = "Cancel" },
                PersonalizationLink(ctx).apply { text = "Clear" }
            )
        }
        Exhibit("Search Results Title") { ctx -> ctx.searchResultsTitle() }
        Exhibit("Accordion — collapsed with count, expanded") { ctx ->
            ctx.column(
                PersonalizationAccordion(ctx).apply { text = "Brand"; count = 12 },
                PersonalizationAccordion(ctx).apply { text = "Size"; expanded = true }
            )
        }
        Exhibit("List Label") { ctx -> PersonalizationListLabel(ctx).apply { text = "Popular categories" } }
        Exhibit("Badge — warning LG / MD / SM, danger") { ctx ->
            ctx.row(
                ctx.badge("New", PersonalizationBadge.Size.LG),
                ctx.badge("New", PersonalizationBadge.Size.MD),
                ctx.badge("New", PersonalizationBadge.Size.SM),
                ctx.badge("-15%", PersonalizationBadge.Size.MD, PersonalizationBadge.BadgeView.DANGER)
            )
        }
        Exhibit("Tag — primary with remove, secondary") { ctx ->
            ctx.row(
                PersonalizationTag(ctx).apply { text = "Nike"; onRemove = {} },
                PersonalizationTag(ctx).apply { text = "Size 42"; tagView = PersonalizationTag.TagView.SECONDARY; onRemove = {} },
                PersonalizationTag(ctx).apply { text = "Running"; tagView = PersonalizationTag.TagView.SECONDARY }
            )
        }
        Exhibit("Checkbox — unchecked, checked, indeterminate, disabled; with label") { ctx ->
            ctx.column(
                ctx.row(
                    ctx.checkbox(PersonalizationCheckbox.CheckState.UNCHECKED),
                    ctx.checkbox(PersonalizationCheckbox.CheckState.CHECKED),
                    ctx.checkbox(PersonalizationCheckbox.CheckState.INDETERMINATE),
                    ctx.checkbox(PersonalizationCheckbox.CheckState.CHECKED).apply { isEnabled = false }
                ),
                PersonalizationCheckboxWithLabel(ctx).apply { text = "In stock only"; checkState = PersonalizationCheckbox.CheckState.CHECKED }
            )
        }
        Exhibit("Dots, Count, Rating") { ctx ->
            ctx.column(
                PersonalizationDots(ctx).apply { count = 5; selectedIndex = 1 },
                PersonalizationCount(ctx).apply { set("Showing", 12, "of", 128) },
                PersonalizationRating(ctx).apply { set("4.7", 128) }
            )
        }
        Exhibit("Loader, Favorites Badge") { ctx -> ctx.row(PersonalizationLoader(ctx), PersonalizationFavoritesBadge(ctx)) }
        Exhibit("Product Image — 1:1, 4:3, 3:4") { ctx ->
            ctx.row(
                ctx.productImage(PersonalizationProductImage.Aspect.SQUARE, DemoProducts.all[0]),
                ctx.productImage(PersonalizationProductImage.Aspect.LANDSCAPE, DemoProducts.all[1]),
                ctx.productImage(PersonalizationProductImage.Aspect.PORTRAIT, DemoProducts.all[2])
            )
        }
        Exhibit("Empty State") { ctx -> PersonalizationEmptyState(ctx).apply { text = "No results for your request." } }
        Exhibit("Icons — the full set, 24 dp") { ctx ->
            ctx.hscroll(ctx.row(*DemoIcons.all.map { ctx.icon(it) }.toTypedArray()))
        }
    }
}

@Composable
private fun BlocksShowcase() {
    Showcase {
        Exhibit("Product Card — carousel, grid, list") { ctx ->
            ctx.column(
                ctx.productCard(PersonalizationProductCard.Type.CAROUSEL, DemoProducts.all[0]).apply {
                    layoutParams = LinearLayout.LayoutParams(ctx.dp(220), ViewGroup.LayoutParams.WRAP_CONTENT)
                },
                ctx.productCard(PersonalizationProductCard.Type.GRID, DemoProducts.all[1]).apply {
                    layoutParams = LinearLayout.LayoutParams(ctx.dp(161), ViewGroup.LayoutParams.WRAP_CONTENT)
                },
                ctx.productCard(PersonalizationProductCard.Type.LIST, DemoProducts.all[2])
            )
        }
        Exhibit("Product Card — image 4:3, 1:1, 3:4 (carousel and list)") { ctx ->
            val aspects = listOf(
                PersonalizationProductImage.Aspect.LANDSCAPE,
                PersonalizationProductImage.Aspect.SQUARE,
                PersonalizationProductImage.Aspect.PORTRAIT
            )
            ctx.column(
                ctx.hscroll(ctx.row(*aspects.mapIndexed { index, aspect ->
                    ctx.productCard(PersonalizationProductCard.Type.CAROUSEL, DemoProducts.all[index + 3]).apply {
                        imageAspect = aspect
                        layoutParams = LinearLayout.LayoutParams(ctx.dp(220), ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                }.toTypedArray())),
                *aspects.mapIndexed { index, aspect ->
                    ctx.productCard(PersonalizationProductCard.Type.LIST, DemoProducts.all[index + 3]).apply { imageAspect = aspect }
                }.toTypedArray()
            )
        }
        Exhibit("Recommender Block — carousel") { ctx ->
            ctx.recommender(PersonalizationRecommenderBlock.Layout.CAROUSEL, "Recommended for you", DemoProducts.all)
        }
        Exhibit("Recommender Block — grid") { ctx ->
            ctx.recommender(PersonalizationRecommenderBlock.Layout.GRID, "Similar products", DemoProducts.all.take(4))
        }
        Exhibit("Recommender Block — list") { ctx ->
            ctx.recommender(PersonalizationRecommenderBlock.Layout.LIST, "Recently viewed", DemoProducts.all.take(3))
        }
        Exhibit("Instant Search — recent searches") { ctx -> ctx.instantSearch(typing = false, images = false) }
        Exhibit("Instant Search — typing, matches in bold") { ctx -> ctx.instantSearch(typing = true, images = false) }
        Exhibit("Instant Search — with images") { ctx -> ctx.instantSearch(typing = false, images = true) }
        Exhibit("Catalog — header, grid ⇄ list, count, load more") { ctx -> ctx.catalog() }
        Exhibit("Catalog — empty state") { ctx -> ctx.catalogEmpty() }
        Exhibit("Filters — range, checkbox lists with show more, reset / apply") { ctx -> ctx.filters() }
    }
}

/**
 * The search flow as a host would wire it: the instant search field resolves the SDK by shopId and
 * searches on its own; submitting a phrase swaps it for the results screen, whose back button
 * returns. Taps on products and categories only toast here — navigation is the host's.
 */
@Composable
private fun SearchShowcase(shopId: String) {
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    val submitted = query
    if (submitted == null) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> ctx.instantSearchField(shopId) { query = it } }
        )
    } else {
        key(submitted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> ctx.searchResultsScreen(shopId, submitted) { query = null } }
            )
        }
    }
}

private fun Context.instantSearchField(shopId: String, onSubmit: (String) -> Unit): View {
    val field = PersonalizationInstantSearchField(this).apply {
        this.shopId = shopId
        placeholder = "Search"
        cancelText = "Cancel"
        recentLabel = "Recent searches"
        clearText = "Clear"
        moreText = "more"
        categoriesLabel = "Categories"
        productsLabel = "Products"
        showImages = true
        this.onSubmit = onSubmit
        onCancel = { query = null }
        onProductClick = { product -> toast("Product: ${product.name}") }
        onCategoryClick = { category -> toast("Category: ${category.name}") }
        onError = { code, message -> toast("Search error $code: $message") }
    }
    return ScrollView(this).apply {
        isFillViewport = true
        addView(field, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        clipToPadding = false
    }
}

private fun Context.searchResultsScreen(shopId: String, query: String, onBack: () -> Unit): View =
    PersonalizationSearchResultsScreen(this).apply {
        this.shopId = shopId
        productActionText = "Add to cart"
        this.onBack = onBack
        onProductClick = { product -> toast("Product: ${product.name}") }
        onProductAction = { product -> toast("Add to cart: ${product.name}") }
        // No sort picker in the design file: cycle relevance → price ↑ → price ↓ on tap.
        onSortClick = {
            when (sortBy) {
                null -> { sortBy = "price"; sortDir = "asc" }
                "price" -> if (sortDir == "asc") sortDir = "desc" else { sortBy = null; sortDir = null }
                else -> { sortBy = null; sortDir = null }
            }
            toast("Sort: ${sortBy ?: "relevance"} ${sortDir.orEmpty()}")
        }
        onError = { code, message -> toast("Search error $code: $message") }
        this.query = query
    }

private fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

// --- Compose scaffolding ------------------------------------------------------------------------

@Composable
private fun Showcase(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = { content() }
    )
}

@Composable
private fun Exhibit(title: String, build: (Context) -> View) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
        AndroidView(factory = build, modifier = Modifier.fillMaxWidth())
    }
}

// --- View factories -----------------------------------------------------------------------------

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun Context.row(vararg children: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    children.forEachIndexed { index, child ->
        val params = (child.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (index > 0) params.marginStart = dp(8)
        addView(child, params)
    }
}

private fun Context.column(vararg children: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    children.forEachIndexed { index, child ->
        val params = (child.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (index > 0) params.topMargin = dp(12)
        addView(child, params)
    }
}

private fun Context.hscroll(child: View): HorizontalScrollView = HorizontalScrollView(this).apply {
    isHorizontalScrollBarEnabled = false
    addView(child, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
}

private fun Context.button(
    label: String?,
    size: PersonalizationButton.Size,
    view: PersonalizationButton.ButtonView = PersonalizationButton.ButtonView.PRIMARY
): PersonalizationButton = PersonalizationButton(this).apply {
    text = label
    this.size = size
    buttonView = view
}

private fun Context.buttonGroup(size: PersonalizationButtonGroup.Size): PersonalizationButtonGroup =
    PersonalizationButtonGroup(this).apply {
        this.size = size
        items = listOf(
            PersonalizationButtonGroup.Item(SdkR.drawable.personalization_ic_grid_2x2, SdkR.drawable.personalization_ic_grid_2x2_fill),
            PersonalizationButtonGroup.Item(SdkR.drawable.personalization_ic_list, SdkR.drawable.personalization_ic_list_fill)
        )
    }

private fun Context.badge(
    label: String,
    size: PersonalizationBadge.Size,
    view: PersonalizationBadge.BadgeView = PersonalizationBadge.BadgeView.WARNING
): PersonalizationBadge = PersonalizationBadge(this).apply {
    text = label
    this.size = size
    badgeView = view
}

private fun Context.checkbox(state: PersonalizationCheckbox.CheckState): PersonalizationCheckbox =
    PersonalizationCheckbox(this).apply { checkState = state }

private fun Context.icon(res: Int): ImageView = AppCompatImageView(this).apply {
    setImageResource(res)
    ImageViewCompat.setImageTintList(this, PersonalizationTheme.colorStateList(context, SdkR.color.personalization_text_primary))
    layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
}

private fun Context.searchResultsTitle(count: Int = 128): PersonalizationSearchResultsTitle =
    PersonalizationSearchResultsTitle(this).apply {
        text = "Sneakers"
        setResults("Found", count, "products")
        setFilters(listOf(
            PersonalizationSearchResultsTitle.Filter("Nike") {},
            PersonalizationSearchResultsTitle.Filter("Size 42") {}
        ))
    }

private fun Context.productImage(aspect: PersonalizationProductImage.Aspect, product: PersonalizationProduct): PersonalizationProductImage =
    PersonalizationProductImage(this).apply {
        this.aspect = aspect
        layoutParams = LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT)
        Glide.with(imageView).load(product.imageUrl).into(imageView)
    }

private fun Context.productCard(type: PersonalizationProductCard.Type, product: PersonalizationProduct): PersonalizationProductCard =
    PersonalizationProductCard(this).apply {
        this.type = type
        brand = product.brand
        name = product.name
        price = product.price
        oldPrice = product.oldPrice
        discount = product.discount
        actionText = product.actionText
        product.ratingValue?.let { setRating(it, product.reviews) }
        Glide.with(image.imageView).load(product.imageUrl).into(image.imageView)
    }

private fun Context.recommender(
    layout: PersonalizationRecommenderBlock.Layout,
    title: String,
    items: List<PersonalizationProduct>
): PersonalizationRecommenderBlock = PersonalizationRecommenderBlock(this).apply {
    this.layout = layout
    text = title
    showAllText = "Show all"
    onShowAll = {}
    imageLoader = DemoProducts.glideLoader
    products = items
}

/**
 * Instant search in its three states: recent searches before typing, suggestions with the
 * query highlighted while typing, and rows with images. The host owns the data; the kit only
 * renders what it is given and reports the taps.
 */
private fun Context.instantSearch(typing: Boolean, images: Boolean): PersonalizationInstantSearch =
    PersonalizationInstantSearch(this).apply {
        placeholder = "want to buy..."
        cancelText = "Cancel"
        showImages = images
        imageLoader = { view, suggestion -> Glide.with(view).load(suggestion.imageUrl).into(view) }
        categoriesLabel = if (typing) "Category" else "Popular category"
        productsLabel = if (typing) "Products" else "Frequently searched"
        if (typing) {
            query = "boots"
            setSuggestions(listOf("winter", "mens", "kids", "for outdoor", "womens", "low", "black", "orange"))
            setCategories(listOf(
                PersonalizationInstantSearch.Suggestion("c1", "Womens boots"),
                PersonalizationInstantSearch.Suggestion("c2", "Mens boots"),
                PersonalizationInstantSearch.Suggestion("c3", "Kids boots")
            ))
            setProducts(listOf(
                PersonalizationInstantSearch.Suggestion("p1", "winter womens boots"),
                PersonalizationInstantSearch.Suggestion("p2", "boots for mens"),
                PersonalizationInstantSearch.Suggestion("p3", "winter boots for mens"),
                PersonalizationInstantSearch.Suggestion("p4", "kids winter boots")
            ))
        } else {
            recentLabel = "Recent searches"
            clearText = "Clear"
            moreText = "more"
            setRecentSearches(listOf("mens winter boots", "kids shoes", "bag", "accessories", "black boots"))
            // Categories come without pictures: the search API returns none for them, so the
            // "with images" state only illustrates product rows.
            setCategories(listOf(
                PersonalizationInstantSearch.Suggestion("c1", "Running shoes", "Shoes"),
                PersonalizationInstantSearch.Suggestion("c2", "Running apparel", "Clothing"),
                PersonalizationInstantSearch.Suggestion("c3", "Trail gear", "Outdoor")
            ))
            setProducts(DemoProducts.all.take(if (images) 3 else 5).map {
                PersonalizationInstantSearch.Suggestion(it.id, it.name.toString(), it.price, it.imageUrl)
            })
        }
    }

/** The filters screen with the sections from the design file; toggles and ranges update its own state. */
private fun Context.filters(): PersonalizationFilters =
    PersonalizationFilters(this).apply {
        text = "Filters"
        resetText = "Reset"
        applyText = "Apply"
        setSections(listOf(
            PersonalizationFilters.Section.Range("size", "Size", "From", "to", "42", "43", select = true),
            PersonalizationFilters.Section.Options(
                "colors", "Colors",
                listOf("All", "Black", "White", "Red", "Light blue", "Green", "Yellow", "Brown", "Grey", "Pink")
                    .mapIndexed { index, label -> PersonalizationFilters.Option(label.lowercase(), label, checked = index == 1) },
                showMoreText = "Show more", showLessText = "Show less"
            ),
            PersonalizationFilters.Section.Range("price", "Price (USD)", "From", "to", "50", "500"),
            PersonalizationFilters.Section.Options(
                "rating", "Rating",
                listOf("All", "5 stars", "4+ stars", "3+ stars")
                    .mapIndexed { index, label -> PersonalizationFilters.Option(label, label, checked = index == 1) }
            )
        ))
    }

/**
 * The catalogue as a host would wire it: the category title (title + view switch, as on the
 * CatalogGrid page) sits in the header slot and drives the grid/list switch, "load more"
 * appends a page after a short simulated delay. The search results title with back, filters
 * and sort belongs to the search flow — see the Search tab.
 */
private fun Context.catalog(): PersonalizationCatalog {
    val catalog = PersonalizationCatalog(this)
    val header = PersonalizationTitle(this).apply {
        text = "Sneakers"
        setTrailing(buttonGroup(PersonalizationButtonGroup.Size.MD).apply {
            onSelected = { index ->
                catalog.view = if (index == 0) PersonalizationProductsGrid.View.GRID else PersonalizationProductsGrid.View.LIST
            }
        })
    }
    var shown = DemoProducts.all.take(4)
    fun render() {
        catalog.products = shown
        catalog.setCount("Showing", shown.size, "of", DemoProducts.total)
    }
    catalog.setHeader(header)
    catalog.imageLoader = DemoProducts.glideLoader
    catalog.loadMoreText = "Load more"
    catalog.onLoadMore = {
        catalog.isLoading = true
        catalog.postDelayed({
            shown = shown + DemoProducts.all.drop(shown.size % DemoProducts.all.size).take(4)
                .map { it.copy(id = "${it.id}-${shown.size}") }
            catalog.isLoading = false
            render()
        }, 800)
    }
    render()
    return catalog
}

/** Empty results: the same header, the empty state in place of the grid, nothing below. */
private fun Context.catalogEmpty(): PersonalizationCatalog =
    PersonalizationCatalog(this).apply {
        setHeader(searchResultsTitle(count = 0))
        emptyText = "No results for your request."
        products = emptyList()
    }

// --- Fixtures -----------------------------------------------------------------------------------

private object DemoIcons {
    val all = listOf(
        SdkR.drawable.personalization_ic_angle_down,
        SdkR.drawable.personalization_ic_angle_large_right,
        SdkR.drawable.personalization_ic_angle_up,
        SdkR.drawable.personalization_ic_arrow_left,
        SdkR.drawable.personalization_ic_arrow_rotate_cw,
        SdkR.drawable.personalization_ic_arrows_up_down,
        SdkR.drawable.personalization_ic_copy,
        SdkR.drawable.personalization_ic_cross_large,
        SdkR.drawable.personalization_ic_cross,
        SdkR.drawable.personalization_ic_equalizer_horizontal,
        SdkR.drawable.personalization_ic_grid_2x2_fill,
        SdkR.drawable.personalization_ic_grid_2x2,
        SdkR.drawable.personalization_ic_list_fill,
        SdkR.drawable.personalization_ic_list,
        SdkR.drawable.personalization_ic_magnifier,
        SdkR.drawable.personalization_ic_spacing_md,
        SdkR.drawable.personalization_ic_star_fill
    )
}

/** Static products for the exhibits. Strings arrive formatted — that is the kit's contract. */
private object DemoProducts {
    const val total = 128

    val glideLoader: (ImageView, PersonalizationProduct) -> Unit = { view, product ->
        Glide.with(view).load(product.imageUrl).into(view)
    }

    /** Photos ship with the app: the showcase must not depend on the network. */
    private fun image(seed: String) = "file:///android_asset/uikit/$seed.jpg"

    val all = listOf(
        PersonalizationProduct("1", "Air Zoom Pegasus 41 running shoes", "\$140", image("pegasus"), "Nike", "4.7", 128, "\$165", "-15%", "Add to cart"),
        PersonalizationProduct("2", "Wireless over-ear headphones", "\$299", image("headphones"), "Sony", "4.8", 2140, actionText = "Add to cart"),
        PersonalizationProduct("3", "Everyday backpack 20 L", "\$89", image("backpack"), "Peak Design", "4.6", 412, "\$110", "-19%", "Add to cart"),
        PersonalizationProduct("4", "Trail running jacket", "\$175", image("jacket"), "Salomon", "4.5", 77, actionText = "Add to cart"),
        PersonalizationProduct("5", "Polarized sunglasses", "\$120", image("sunglasses"), "Oakley", "4.4", 305, "\$150", "-20%", "Add to cart"),
        PersonalizationProduct("6", "Insulated bottle 750 ml", "\$35", image("bottle"), "Hydro Flask", "4.9", 980, actionText = "Add to cart"),
        PersonalizationProduct("7", "GPS running watch", "\$449", image("watch"), "Garmin", "4.7", 1532, actionText = "Add to cart"),
        PersonalizationProduct("8", "Court sneakers", "\$95", image("sneakers"), "Adidas", "4.3", 64, "\$120", "-21%", "Add to cart")
    )
}
