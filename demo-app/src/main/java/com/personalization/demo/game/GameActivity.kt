package com.personalization.demo.game

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.personalization.R as SdkR
import com.personalization.Rees46
import com.personalization.SDK
import com.personalization.demo.BuildConfig
import com.personalization.demo.shop.circleButton
import com.personalization.demo.shop.isNight
import com.personalization.demo.shop.kitText
import com.personalization.demo.shop.onSystemBars
import com.personalization.demo.shop.pageBackground
import com.personalization.demo.shop.spacing

/**
 * The demo's mini-game, full screen over the screen it was opened from: a panel with the title and the
 * close button, under it the game — a web page ([GamePage]). For the SDK it is an ordinary deep link
 * (`rees46://game`, see [com.personalization.demo.DemoLink]): a button of a popup or a story, or a link
 * from outside, opens it; the game itself is the app's, and the page plays it all by itself — there is
 * no bridge to the app. The panel's cross and the system back go back where the game was opened from.
 *
 * The page gets the theme of the app in its address; the screen and the page's view take the kit's page
 * background, so nothing flashes white while it loads. A rotation keeps the game (the activity handles
 * it itself, see the manifest); a theme switch starts it again in the new theme. While the game is on screen
 * the popups of triggers do not show ([GamePopups]): they would cover it.
 *
 * No action bar, edge to edge: the screen makes room for the system bars itself.
 */
class GameActivity : AppCompatActivity() {

    private lateinit var page: WebView

    /** The address the page was loaded from. */
    private var loaded: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackground())
        }
        screen.addView(panel(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page = webPage()
        screen.addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        screen.onSystemBars { top, bottom -> screen.setPadding(0, top, 0, bottom) }
        setContentView(screen)
        load(intent)
    }

    /** One more game link while the game is on top: another game opens in its place, the same one stays. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent) {
        val url = GamePage.url(intent.getStringExtra(EXTRA_LINK), isNight)
        if (url == loaded) return
        loaded = url
        page.loadUrl(url)
    }

    // Before super: the SDK hears of the started activity in it and may decide on a popup at once.
    override fun onStart() {
        popups.started()
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        popups.stopped()
    }

    override fun onResume() {
        super.onResume()
        page.onResume()
    }

    override fun onPause() {
        page.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        page.destroy()
        super.onDestroy()
    }

    /** "Крестики-нолики" on the left; on the right, centred on its line, the round close button. */
    private fun panel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val edge = spacing(SdkR.dimen.personalization_spacing_padding_x)
        val vertical = spacing(SdkR.dimen.personalization_spacing_md)
        setPadding(edge, vertical, edge, vertical)
        addView(
            kitText(
                SdkR.dimen.personalization_font_size_xl, SdkR.dimen.personalization_line_height_xl,
                SdkR.color.personalization_text_primary, emphasized = true
            ).apply { text = TITLE_TEXT },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val close = circleButton(
            SdkR.drawable.personalization_ic_cross_large, SdkR.color.personalization_background_card, CLOSE_TEXT
        ) { finish() }
        addView(close, LinearLayout.LayoutParams(close.layoutParams))
    }

    /** The view of the page: scripts on, links of the page stay in it, the kit's background under it. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun webPage(): WebView = WebView(this).apply {
        setBackgroundColor(pageBackground())
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = WebViewClient()
    }

    companion object {
        private const val EXTRA_LINK = "link"

        private const val TITLE_TEXT = "Крестики-нолики"
        private const val CLOSE_TEXT = "Закрыть"

        /** The popups of the demo shop, the one popups v3 are on for. */
        private val popups = GamePopups(
            read = { shopSdk()?.enableAutoPopupPresentation },
            write = { on -> shopSdk()?.enableAutoPopupPresentation = on }
        )

        /** `null` — the shop did not initialize. */
        private fun shopSdk(): SDK? = runCatching { Rees46.getInstance(BuildConfig.SHOP_ID) }.getOrNull()

        /** Opens the game of the game link [link] over the screen of [context]. */
        fun start(context: Context, link: String) {
            val intent = Intent(context, GameActivity::class.java).putExtra(EXTRA_LINK, link)
            // Outside an activity there is no screen to go over: the game starts the demo's task.
            if (context.activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        private val Context.activity: Activity?
            get() = when (this) {
                is Activity -> this
                is ContextWrapper -> baseContext?.activity
                else -> null
            }
    }
}
