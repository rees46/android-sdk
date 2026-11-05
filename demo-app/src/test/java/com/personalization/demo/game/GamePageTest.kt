package com.personalization.demo.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** The game screen loads the demo's own page in the app's theme, or the https game of the link's `url`. */
class GamePageTest {

    @Test
    fun `the own game takes the theme of the app`() {
        assertEquals("file:///android_asset/game/tic-tac-toe.html?theme=light", GamePage.url("rees46://game", dark = false))
        assertEquals("file:///android_asset/game/tic-tac-toe.html?theme=dark", GamePage.url("rees46flutter://game", dark = true))
        assertEquals("file:///android_asset/game/tic-tac-toe.html?theme=dark", GamePage.url(null, dark = true))
    }

    @Test
    fun `a game of one's own opens from its https address as it is`() {
        assertEquals(
            "https://example.com/game?level=2",
            GamePage.url("rees46://game?url=https%3A%2F%2Fexample.com%2Fgame%3Flevel%3D2", dark = true)
        )
    }

    @Test
    fun `an address that is not https gives the own game`() {
        assertEquals(
            "file:///android_asset/game/tic-tac-toe.html?theme=light",
            GamePage.url("rees46://game?url=http%3A%2F%2Fexample.com%2Fgame", dark = false)
        )
        assertEquals(
            "file:///android_asset/game/tic-tac-toe.html?theme=dark",
            GamePage.url("rees46://game?url=not%20a%20link", dark = true)
        )
    }
}
