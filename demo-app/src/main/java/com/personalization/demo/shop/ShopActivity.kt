package com.personalization.demo.shop

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.personalization.Rees46
import com.personalization.demo.BuildConfig
import com.personalization.demo.R
import com.personalization.demo.openStoriesLinkIntent

/**
 * The "shop only" demo — the launcher activity of the `shop` build type (see `src/shop/AndroidManifest.xml`):
 * the demo shop full screen, without the API / UI Kit tabs and the developer buttons. The SDK is the
 * same shop instance [com.personalization.demo.DemoApplication] initializes for the full demo.
 *
 * Stories links (`rees46://…`, `https://demo.rees46.ru/…`) open their campaign over the shop, on a cold
 * start and while the shop is open; a product tapped in that campaign opens its page in the shop.
 */
class ShopActivity : AppCompatActivity() {

    private lateinit var shop: DemoShop

    /** System back walks the shop's own stack; at Home it falls through to the system. */
    private val shopBack = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (shop.back()) return
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this)
        setContentView(container)
        shop = DemoShop(
            context = this,
            sdk = Rees46.getInstance(BuildConfig.SHOP_ID),
            shopId = BuildConfig.SHOP_ID,
            storiesCode = getString(R.string.shop_stories_code),
            container = container
        )
        // Popups v3: every screen of the shop reports its code; a product of a popup's recommendations
        // opens here, in the shop.
        shop.onScreen = { screen -> shop.sdk.popups.screenOpened(screen) }
        shop.sdk.popups.productHandler = { product ->
            shop.open(ShopRoute.Product(product.id))
            true
        }
        shop.start(savedInstanceState?.getBundle(STATE_SHOP))
        onBackPressedDispatcher.addCallback(this, shopBack)
        // A recreated activity (rotation, theme) must not handle its push or link again.
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** A tapped push reports its click, as in the full demo; a stories link opens its campaign. */
    private fun handle(intent: Intent?) {
        intent?.extras
            ?.takeIf { it.getString("NOTIFICATION_TYPE") != null || it.getString("NOTIFICATION_ID") != null }
            ?.let { shop.sdk.notificationClicked(it) }
        openStoriesLinkIntent(this, intent, BuildConfig.SHOP_ID, shop.storiesClicks)
    }

    override fun onDestroy() {
        // The handler holds this activity's shop; a recreated activity sets its own.
        shop.sdk.popups.productHandler = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(STATE_SHOP, shop.save())
    }

    private companion object {
        const val STATE_SHOP = "shop"
    }
}
