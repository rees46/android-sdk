package com.personalization.demo.shop

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.personalization.ui.widgets.PersonalizationInstantSearchField

/**
 * The shop's instant search field, behind the search button of Home.
 *
 * The field brings its own panel — recent searches, suggestions, categories, products. It shows only in
 * the search mode: [start] (the button) brings it up with the focus and the keyboard, the panel under
 * it and the rest of the screen out of the way ([onSearching]), as in a store app; "Cancel" or system
 * back takes it away again with the keyboard. Submitting a phrase opens the results, a product its page,
 * a category its page — and the screen under them is back in its normal mode. The panel is switched with
 * the field's own show* flags, and an empty query makes it redraw.
 *
 * [focusHolder] — a container above the field that holds the focus at rest: a focused field is an open
 * panel, so the field must not get the focus by itself when the screen shows up.
 */
internal class ShopSearchBar(
    private val shop: DemoShop,
    private val focusHolder: View,
    private val onSearching: (Boolean) -> Unit
) {
    private val context: Context = shop.context

    val field = PersonalizationInstantSearchField(context)

    private var searching = false

    private val keyboard: InputMethodManager?
        get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    init {
        focusHolder.isFocusableInTouchMode = true
        field.shopId = shop.shopId
        field.placeholder = "Search"
        field.recentLabel = "Recent searches"
        field.clearText = "Clear"
        field.moreText = "more"
        field.categoriesLabel = "Categories"
        field.productsLabel = "Products"
        field.showImages = true
        showPanel(false)
        field.view.input.editText.setOnFocusChangeListener { _, focused -> if (focused) open() }
        field.onCancel = { close() }
        field.onSubmit = { query ->
            close()
            shop.open(ShopRoute.Search(query))
        }
        field.onProductClick = { product ->
            close()
            shop.open(ShopRoute.Product(product.id))
        }
        // Categories of a typed query come with their id; the popular ones of the empty field only
        // with a name and a link, so those run as a search.
        field.onCategoryClick = { category ->
            close()
            val id = category.id
            shop.open(if (id.isNullOrEmpty()) ShopRoute.Search(category.name) else ShopRoute.Category(id, category.name))
        }
    }

    /** The search button: the search mode, the field focused and the keyboard up. */
    fun start() {
        open()
        val input = field.view.input.editText
        input.requestFocus()
        // The field has just become visible: the keyboard comes once it is laid out.
        input.post { keyboard?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    /** System back: leaves the search mode. `false` — the screen was not in it. */
    fun onBack(): Boolean {
        if (!searching) return false
        close()
        return true
    }

    private fun open() {
        if (searching) return
        searching = true
        onSearching(true)
        showPanel(true)
    }

    private fun close() {
        if (!searching) return
        searching = false
        showPanel(false)
        val input = field.view.input.editText
        keyboard?.hideSoftInputFromWindow(input.windowToken, 0)
        focusHolder.requestFocus()
        onSearching(false)
    }

    private fun showPanel(show: Boolean) {
        field.showRecent = show
        field.showSuggestions = show
        field.showCategories = show
        field.showProducts = show
        field.cancelText = if (show) "Cancel" else null
        field.query = ""
    }
}
