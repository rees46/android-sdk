package com.personalization.demo.game

import com.personalization.demo.DemoLink

/** What the game screen loads. */
internal object GamePage {

    /**
     * The demo's own game: one web page, the same in every demo, in the assets — no network. It takes
     * the theme as `?theme=dark|light`.
     */
    const val BUILT_IN = "file:///android_asset/game/tic-tac-toe.html"

    /**
     * The page for the game link [link]: the `https` address of its `url` parameter — a game of one's
     * own from its server, as it is — or else the demo's own game in the theme of the app.
     */
    fun url(link: String?, dark: Boolean): String =
        link?.let(DemoLink::gameAddress) ?: "$BUILT_IN?theme=${if (dark) "dark" else "light"}"
}
