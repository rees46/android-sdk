package com.personalization.demo

import com.personalization.SDK

/**
 * Screen codes the demo reports to popups v3 (`sdk.popups.screenOpened`) — the codes a marketer puts
 * into a popup's `target_screens`.
 */
internal object DemoScreens {
    // The shop: both the full demo's "Shop" tab and the shop-only build.
    const val HOME = "home"
    const val CATEGORY = "category"
    const val PRODUCT = "product"
    const val SEARCH = "search"

    // The other tabs of the full demo; its first tab, the API one, is its home.
    const val UI_KIT = "ui_kit"
    const val LEGACY_UI = "legacy_ui"
    const val MULTI_INSTANCE = "multi_instance"
}

/**
 * Popups of the demo shop that its Home shows by hand, from the popup button of the header (`popups.show`,
 * no trigger, screens or frequency): id → the title of the menu item. A new popup is one more line.
 */
internal val DEMO_POPUPS: Map<Int, String> = linkedMapOf(
    181 to "#181 Banner · top",
    182 to "#182 Modal · center",
    185 to "#185 Fullscreen"
)

/**
 * Popups v3 on for the demo shop. They sleep until the host turns them on; the demo also asks not to
 * wait for the `popups_v3` flag of `/init`, so they work whatever the shop's setting. The engine
 * decisions go to the SDK log (debug).
 */
internal fun SDK.enablePopupsV3() {
    popups.appHost = STORIES_API_V3_DOMAIN
    popups.debug = true
    popups.ignoreInitFlag = true
    popups.enabled = true
}
