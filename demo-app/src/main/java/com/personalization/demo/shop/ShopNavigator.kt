package com.personalization.demo.shop

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.personalization.R as SdkR
import com.personalization.demo.DemoScreens

/** A screen of the shop: where it is in the stack, and what it shows. */
internal sealed class ShopRoute {
    object Home : ShopRoute()
    data class Category(val id: String, val name: String) : ShopRoute()
    data class Product(val id: String) : ShopRoute()
    data class Search(val query: String) : ShopRoute()

    /** Code of the screen for popups v3 (`sdk.popups.screenOpened`). */
    val screen: String
        get() = when (this) {
            Home -> DemoScreens.HOME
            is Category -> DemoScreens.CATEGORY
            is Product -> DemoScreens.PRODUCT
            is Search -> DemoScreens.SEARCH
        }

    /** Three strings per route, so the stack fits a plain string list in the saved state. */
    fun fields(): List<String> = when (this) {
        Home -> listOf(HOME, "", "")
        is Category -> listOf(CATEGORY, id, name)
        is Product -> listOf(PRODUCT, id, "")
        is Search -> listOf(SEARCH, query, "")
    }

    companion object {
        private const val HOME = "home"
        private const val CATEGORY = "category"
        private const val PRODUCT = "product"
        private const val SEARCH = "search"

        fun of(fields: List<String>): ShopRoute? = when (fields.getOrNull(0)) {
            HOME -> Home
            CATEGORY -> Category(fields[1], fields[2])
            PRODUCT -> Product(fields[1])
            SEARCH -> Search(fields[1])
            else -> null
        }
    }
}

internal interface ShopScreen {
    val view: View

    /** Kit colour token of the screen's top edge, the one under the status bar. */
    val topSurface: Int get() = SdkR.color.personalization_background_generic

    /** System back inside the screen — closing an open panel. `false` — the screen itself goes. */
    fun onBack(): Boolean = false
}

/**
 * The shop's own stack of screens with back. Screens under the top one stay in the container, only
 * hidden, so coming back finds them as they were — scrolled, loaded, without a second request.
 * The routes survive the activity being recreated (rotation, the appearance switch of the UI Kit tab);
 * the screens are built again from them.
 */
internal class ShopNavigator(
    private val container: FrameLayout,
    private val build: (route: ShopRoute, restored: Boolean) -> ShopScreen,
    /** The screen on top changed: a new one opened, or back uncovered the one under it. */
    private val onTop: (ShopRoute) -> Unit = {}
) {
    private class Entry(val route: ShopRoute, val screen: ShopScreen)

    private val stack = mutableListOf<Entry>()

    fun push(route: ShopRoute, restored: Boolean = false) {
        add(route, restored)
        onTop(route)
    }

    private fun add(route: ShopRoute, restored: Boolean) {
        stack.lastOrNull()?.screen?.view?.visibility = View.GONE
        val screen = build(route, restored)
        stack += Entry(route, screen)
        container.addView(
            screen.view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    /** The route on top. */
    val top: ShopRoute get() = stack.lastOrNull()?.route ?: ShopRoute.Home

    /** The screen on top; `null` before the stack is built. */
    val topScreen: ShopScreen? get() = stack.lastOrNull()?.screen

    /** Back from a screen's own button: the screen under it comes back. */
    fun pop(): Boolean {
        if (stack.size < 2) return false
        val top = stack.removeAt(stack.lastIndex)
        container.removeView(top.screen.view)
        stack.last().screen.view.visibility = View.VISIBLE
        onTop(stack.last().route)
        return true
    }

    /** System back: the top screen first, then the stack. `false` — nothing left to go back to. */
    fun back(): Boolean = stack.lastOrNull()?.screen?.onBack() == true || pop()

    fun save(): Bundle = Bundle().apply {
        putStringArrayList(KEY_ROUTES, ArrayList(stack.flatMap { it.route.fields() }))
    }

    /** Builds the saved stack again, or starts at Home. */
    fun restore(state: Bundle?) {
        val routes = state?.getStringArrayList(KEY_ROUTES).orEmpty()
            .chunked(3)
            .mapNotNull(ShopRoute::of)
            .takeIf { it.firstOrNull() == ShopRoute.Home }
            ?: listOf(ShopRoute.Home)
        routes.forEach { add(it, restored = state != null) }
        onTop(top)
    }

    private companion object {
        const val KEY_ROUTES = "routes"
    }
}
