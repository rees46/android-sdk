package com.personalization.demo.game

/**
 * Keeps the popups of triggers off while the game is on screen: the SDK shows a popup over the activity in
 * front, and the game would go under it — an app that a game link starts gets its `app_start` popups right
 * over the game. The first game screen started turns the automatic presentation off ([write] `false`), the
 * last one stopped puts back what it was before; a game screen that takes the place of another starts
 * before the old one stops. Popups shown by hand are not affected.
 *
 * [read] — the automatic presentation now, `null` — no SDK: nothing is turned off.
 */
internal class GamePopups(
    private val read: () -> Boolean?,
    private val write: (Boolean) -> Unit
) {
    private var started = 0

    /** What the automatic presentation was before the first game screen. */
    private var before: Boolean? = null

    fun started() {
        if (started++ > 0) return
        before = read()
        if (before != null) write(false)
    }

    fun stopped() {
        if (started == 0 || --started > 0) return
        before?.let(write)
        before = null
    }
}
