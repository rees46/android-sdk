package com.personalization.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The demo tells its links apart in one place: the game link of any of the demos' schemes opens the game
 * here, a stories link (`r46_stories`) stays a stories link, the rest goes on as before.
 */
class DemoLinkTest {

    @Test
    fun `the game host with the scheme of any of the demos is the game`() {
        listOf(
            "rees46://game",
            "rees46flutter://game",
            "rees46rn://game",
            "rees46://game/",
            "rees46://game/level/2?from=popup#top",
            "REES46://Game",
            "  rees46://game  ",
            "rees46://game:80"
        ).forEach { assertEquals(it, DemoLink.GAME, DemoLink.of(it)) }
    }

    @Test
    fun `a game link with an address is still the game`() {
        assertEquals(DemoLink.GAME, DemoLink.of("rees46://game?url=https%3A%2F%2Fexample.com%2Fgame"))
        assertEquals(DemoLink.GAME, DemoLink.of("rees46://game?url=http%3A%2F%2Fexample.com"))
    }

    @Test
    fun `r46_stories makes a stories link, the game host included`() {
        listOf(
            "rees46://game?r46_stories=abc&r46_story=40",
            "rees46://demo.rees46.ru/?r46_stories=abc&r46_story=40&r46_slide=37",
            "https://demo.rees46.ru/?r46_stories=abc&r46_story=40",
            "rees46://stories?r46_story=40&r46_stories=abc"
        ).forEach { assertEquals(it, DemoLink.STORIES, DemoLink.of(it)) }
    }

    @Test
    fun `anything else is another link`() {
        listOf(
            "",
            "game",
            "rees46:game",
            "rees46://games",
            "rees46://stories",
            "rees46://demo.rees46.ru/game",
            "myapp://game",
            "https://game",
            "https://demo.rees46.ru/catalog",
            "rees46://game.example.com",
            "https://demo.rees46.ru/#r46_stories=abc"
        ).forEach { assertEquals(it, DemoLink.OTHER, DemoLink.of(it)) }
    }

    @Test
    fun `the https address of a game link is decoded`() {
        assertEquals(
            "https://example.com/game?level=2",
            DemoLink.gameAddress("rees46://game?url=https%3A%2F%2Fexample.com%2Fgame%3Flevel%3D2")
        )
        assertEquals("https://example.com/game", DemoLink.gameAddress("rees46rn://game?from=popup&url=https://example.com/game"))
        assertEquals("HTTPS://example.com", DemoLink.gameAddress("rees46://game?url=HTTPS%3A%2F%2Fexample.com"))
    }

    @Test
    fun `an address that is not https or does not parse gives none`() {
        listOf(
            "rees46://game",
            "rees46://game?url=",
            "rees46://game?url=http%3A%2F%2Fexample.com%2Fgame",
            "rees46://game?url=javascript%3Aalert(1)",
            "rees46://game?url=file%3A%2F%2F%2Fsdcard%2Fgame.html",
            "rees46://game?url=https%3A%2F%2F",
            "rees46://game?url=https%3A%2F%2Fexa%20mple.com",
            "rees46://game?url=%E0%A4%A",
            "rees46://game?url=example.com",
            "https://example.com/?url=https%3A%2F%2Fexample.com%2Fgame",
            "rees46://game?r46_stories=abc&r46_story=40&url=https%3A%2F%2Fexample.com"
        ).forEach { assertNull(it, DemoLink.gameAddress(it)) }
    }
}
