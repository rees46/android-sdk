package com.personalization.demo

import android.content.Context
import com.personalization.demo.game.GameActivity
import java.net.URI
import java.net.URLDecoder

/**
 * What a link that comes to the demo is — from outside (the intent filters of the manifest) or from a
 * popup or a story of the demo:
 * - [GAME] — the demo's mini-game: `rees46://game`, the host `game` with the scheme of any of the demos
 *   (`rees46`, `rees46flutter`, `rees46rn`), so one link in a popup or a story opens the game in each of
 *   them. `rees46://game?url=<https address>` opens a game of one's own from that address ([gameAddress]);
 * - [STORIES] — a stories link: any scheme, host and path, the `r46_stories` parameter matters (it wins
 *   over the game host);
 * - [OTHER] — anything else.
 */
internal enum class DemoLink {
    GAME, STORIES, OTHER;

    companion object {
        /** Link schemes of the demos: this one's ([DEMO_LINK_SCHEME]), the Flutter and React Native ones. */
        val SCHEMES = setOf(DEMO_LINK_SCHEME, "rees46flutter", "rees46rn")

        const val GAME_HOST = "game"

        private const val STORIES_PARAMETER = "r46_stories"

        /** The parameter of a game link with the address of a game of one's own. */
        private const val GAME_ADDRESS_PARAMETER = "url"

        /** `scheme://authority` at the start of a link. */
        private val START = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)")

        fun of(url: String): DemoLink {
            val link = url.trim()
            if (parameters(link).any { it.first == STORIES_PARAMETER }) return STORIES
            val start = START.find(link) ?: return OTHER
            val scheme = start.groupValues[1].lowercase()
            // The host without a user (`user@`) and a port (`:80`).
            val host = start.groupValues[2].substringAfterLast('@').substringBefore(':').lowercase()
            return if (scheme in SCHEMES && host == GAME_HOST) GAME else OTHER
        }

        /**
         * The address of a game of one's own in a game link — the `url` parameter, encoded as a query
         * value: `rees46://game?url=https%3A%2F%2Fexample.com%2Fgame`. Only an `https` address with a host;
         * `null` — not a game link, no address, or one that is not `https` or does not parse: the demo's
         * own game opens.
         */
        fun gameAddress(url: String): String? {
            if (of(url) != GAME) return null
            val value = parameters(url.trim()).firstOrNull { it.first == GAME_ADDRESS_PARAMETER }?.second ?: return null
            val address = try {
                URLDecoder.decode(value, Charsets.UTF_8.name()).trim()
            } catch (exception: IllegalArgumentException) {
                return null
            }
            val uri = try {
                URI(address)
            } catch (exception: Exception) {
                return null
            }
            return address.takeIf { uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty() }
        }

        /**
         * The query parameters of a link — from the first `?` to a `#` after it, as the SDK reads a stories
         * link — names with their values as they are written (not decoded).
         */
        private fun parameters(link: String): List<Pair<String, String>> {
            val start = link.indexOf('?')
            if (start < 0) return emptyList()
            val end = link.indexOf('#', start).takeIf { it >= 0 } ?: link.length
            return link.substring(start + 1, end).split('&')
                .map { it.substringBefore('=') to it.substringAfter('=', missingDelimiterValue = "") }
                .filter { it.first.isNotEmpty() }
        }
    }
}

/**
 * Opens the game over the screen of [context] when [url] is a game link; `false` — it is not, nothing
 * opens.
 */
internal fun openGameLink(context: Context, url: String): Boolean {
    if (DemoLink.of(url) != DemoLink.GAME) return false
    GameActivity.start(context, url)
    return true
}
