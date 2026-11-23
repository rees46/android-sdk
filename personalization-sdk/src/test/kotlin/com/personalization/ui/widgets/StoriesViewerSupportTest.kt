package com.personalization.ui.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.R
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The viewer's helpers: timers that stand still in the background, the kept sound choice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesViewerSupportTest {

    private val looper = shadowOf(Looper.getMainLooper())
    private val handler = Handler(Looper.getMainLooper())

    @Test
    fun `a frozen timer stands still and then runs out its remaining time`() {
        var fired = 0
        val timer = StoryTimer(handler) { fired++ }
        timer.start(10_000)
        idle(4_000)

        timer.freeze(true)
        idle(60_000)
        assertEquals(0, fired)
        assertTrue(timer.isScheduled)

        timer.freeze(false)
        idle(5_999)
        assertEquals(0, fired)
        idle(1)
        assertEquals(1, fired)
        assertFalse(timer.isScheduled)
    }

    @Test
    fun `a timer started while frozen counts only after the thaw`() {
        var fired = 0
        val timer = StoryTimer(handler) { fired++ }
        timer.freeze(true)
        timer.start(15_000)
        idle(30_000)
        assertEquals(0, fired)

        timer.freeze(false)
        idle(15_000)
        assertEquals(1, fired)
    }

    @Test
    fun `a cancelled or restarted timer does not fire for the old start`() {
        var fired = 0
        val timer = StoryTimer(handler) { fired++ }
        timer.start(10_000)
        timer.freeze(true)
        timer.cancel()
        timer.freeze(false)
        idle(20_000)
        assertEquals(0, fired)

        timer.start(10_000)
        idle(6_000)
        timer.start(10_000)
        idle(6_000)
        assertEquals(0, fired)
        idle(4_000)
        assertEquals(1, fired)
    }

    @Test
    fun `an audio interruption silences this viewer but keeps the chosen sound`() {
        val sound = StoriesSound()
        sound.choose(false)

        assertTrue(sound.silence())
        assertTrue(sound.muted)
        assertFalse(StoriesSound.chosenMuted)
        // The next viewer opens with the kept choice.
        assertFalse(StoriesSound().muted)
    }

    @Test
    fun `the user's own choice lifts the silence`() {
        val sound = StoriesSound()
        sound.choose(false)
        sound.silence()

        sound.choose(false)
        assertFalse(sound.muted)
        assertFalse(sound.silenced)
    }

    @Test
    fun `nothing is silenced while the sound is already off`() {
        val sound = StoriesSound()
        sound.choose(true)

        assertFalse(sound.silence())
        assertFalse(sound.silenced)
        assertTrue(StoriesSound.chosenMuted)
    }

    @Test
    fun `the poll texts the library shipped before stay in it`() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        assertEquals("Results", resources.getString(R.string.personalization_stories_poll_results))
        assertEquals("Thanks!", resources.getString(R.string.personalization_stories_poll_thanks))
        assertEquals("Vote counted", resources.getString(R.string.personalization_stories_poll_vote_counted))
        assertEquals("1 vote", resources.getQuantityString(R.plurals.personalization_stories_poll_votes, 1, "1"))
        assertEquals("5 votes", resources.getQuantityString(R.plurals.personalization_stories_poll_votes, 5, "5"))
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the poll texts the library shipped before stay in russian too`() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        assertEquals("Результаты", resources.getString(R.string.personalization_stories_poll_results))
        assertEquals("Спасибо!", resources.getString(R.string.personalization_stories_poll_thanks))
        assertEquals("Голос учтён", resources.getString(R.string.personalization_stories_poll_vote_counted))
        assertEquals("2 голоса", resources.getQuantityString(R.plurals.personalization_stories_poll_votes, 2, "2"))
        assertEquals("5 голосов", resources.getQuantityString(R.plurals.personalization_stories_poll_votes, 5, "5"))
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the built-in poll texts are the english ones of the web SDK in any language, the rest stays localized`() {
        val texts = StoriesTexts.defaults(ApplicationProvider.getApplicationContext())
        assertEquals(PollTexts("Answer again", "Failed to send the answer. Please try again", "Send"), texts.pollTexts)
        assertEquals(PollTexts.DEFAULTS, texts.pollTexts)
        assertEquals("Поделиться", texts.shareLabel)
        // The host's texts, then the poll's labels, replace them; an empty host text keeps the built-in one.
        val host = PollTexts("Ещё раз", "", "Готово").or(PollTexts.DEFAULTS)
        assertEquals(PollTexts("Ещё раз", "Failed to send the answer. Please try again", "Готово"), host)
        assertEquals(PollTexts("Ответить заново", "Failed to send the answer. Please try again", "Готово"),
            host.with(StoryPoll.Labels(reanswer = "Ответить заново")))
    }

    @After
    fun resetSound() {
        StoriesSound.chosenMuted = true
    }

    private fun idle(ms: Long) = looper.idleFor(ms, TimeUnit.MILLISECONDS)
}
