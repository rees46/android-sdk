package com.personalization.ui.widgets

import android.app.Activity
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.Insets
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.personalization.Fixtures
import com.personalization.Product
import com.personalization.stories.models.Slide
import com.personalization.stories.models.elements.ButtonElement
import com.personalization.ui.components.PersonalizationStoryTextBlock
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The poll card on a slide: what it draws, where it sits, which touches it keeps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoryPollViewTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val transport = FakePollsTransport()
    private val host = PollHost(transport)

    private val feed: List<Slide> by lazy {
        val story = StoriesApiV3.parse(Fixtures.read("stories_polls_feed.json")).single()
        List(story.slidesCount) { story.getSlide(it) }
    }

    @Before
    fun setUp() = StoryPollStore.clear()

    @After
    fun tearDown() = StoryPollStore.clear()

    @Test
    fun `no transport — no card, the unknown kind — no card`() {
        assertTrue(laidOut(feed[0], PollHost(null)).pollCards.isEmpty())
        assertTrue(laidOut(feed[7]).pollCards.isEmpty())
        assertEquals(1, laidOut(feed[0]).pollCards.size)
    }

    @Test
    fun `single choice draws the question and options and sends on tap`() {
        val card = laidOut(feed[0]).pollCards.single()
        assertEquals("Что вам больше нравится?", texts(card).first())
        val rows = rows(card)
        assertEquals(listOf("Кроссовки", "Ботинки", "Сандалии"), rows.map { it.label.text.toString() })
        assertEquals(listOf("A", "B", "C"), rows.map { it.badge.letter })
        assertTrue("no results header in the form", "Results" !in texts(card))

        rows[1].performClick()
        idle()
        assertEquals(listOf("answer demo_single {option_ids=[2], story_id=9001, slide_id=900}"), transport.calls)
        assertEquals(PollStyle.SENDING_ALPHA, card.alpha)
        assertTrue(rows[1].selectedState)
        assertTrue("the letter of the chosen gives way to a check", rows[1].badge.checked)
        assertTrue(rows.none { it.isEnabled })

        transport.succeed(StoriesPollAnswer(optionIds = listOf(2)), StoriesPollResults(10, listOf(StoriesPollResults.Option(2, 70.0))))
        idle()
        assertEquals(1f, card.alpha)
        val answered = rows(card)
        assertEquals(listOf("0%", "70%", "0%"), answered.map { it.percentText.toString() })
        assertTrue(answered[1].selectedState)
        assertEquals(listOf(false, true, false), answered.map { it.badge.checked })
        // Results header, the question, the own thanks of the poll instead of the default pair and the votes.
        assertEquals(listOf("Results", "Что вам больше нравится?", "Спасибо за ответ!", "10 votes"), texts(card).take(4))
        // allow_reanswer: the link with the label of the element.
        assertTrue(texts(card).contains("Ответить заново"))
    }

    @Test
    fun `an error is shown under the content and a retry is possible`() {
        val card = laidOut(feed[0]).pollCards.single()
        rows(card)[0].performClick()
        transport.fail(429)
        idle()
        val error = texts(card).last()
        assertEquals("Не удалось отправить ответ. Попробуйте еще раз", error)
        assertTrue(rows(card).all { it.isEnabled && !it.selectedState })
        rows(card)[0].performClick()
        assertEquals(2, transport.calls.size)
    }

    @Test
    fun `multiple choice rows toggle and the submit button sends`() {
        val card = laidOut(feed[1]).pollCards.single()
        val submit = card.first { it is TextView && it.text == "Отправить ответ" } as TextView
        assertFalse(submit.isEnabled)
        val rows = rows(card)
        assertEquals(listOf("A", "B", "C", "D", "E"), rows.map { it.badge.letter })
        rows[3].performClick()
        rows[0].performClick()
        rows[1].performClick()
        rows[1].performClick()
        assertEquals(listOf(true, false, false, true, false), rows.map { it.selectedState })
        assertEquals(listOf(true, false, false, true, false), rows.map { it.badge.checked })
        assertTrue(submit.isEnabled)
        submit.performClick()
        assertEquals(listOf("answer demo_multi {option_ids=[1, 4], story_id=9001, slide_id=901}"), transport.calls)

        transport.succeed(null, StoriesPollResults(1248, listOf(StoriesPollResults.Option(1, 52.4), StoriesPollResults.Option(4, 31.0))))
        idle()
        val answered = rows(card)
        assertEquals(listOf(true, false, false, true, false), answered.map { it.selectedState })
        assertEquals(listOf("52%", "0%", "0%", "31%", "0%"), answered.map { it.percentText.toString() })
        assertTrue(texts(card).containsAll(listOf("Results", "Спасибо!", "1,248 votes")))
    }

    @Test
    fun `the text field is cut at max_length, counts and sends the trimmed text`() {
        val card = laidOut(feed[2]).pollCards.single()
        val input = card.input!!
        assertEquals("Ваш ответ", input.hint)
        input.setText("  " + "a".repeat(130))
        assertEquals(120, input.text.length)
        assertTrue(texts(card).contains("120/120"))
        input.setText("  Быстрее  ")
        (card.first { it is TextView && it.text == "Send" } as TextView).performClick()
        assertEquals(listOf("answer demo_text {text=Быстрее, story_id=9001, slide_id=902}"), transport.calls)

        transport.succeed(null, null)
        idle()
        assertNull(card.input)
        assertTrue(texts(card).containsAll(listOf("Спасибо за отзыв!", "Быстрее")))
        assertTrue(all(card).filterIsInstance<PollBadge>().single().checked)
        assertTrue("no results header for the text", "Results" !in texts(card))
    }

    @Test
    fun `a word in composition is not cut, the field is cut when it ends`() {
        val card = laidOut(textSlide(maxLength = 5)).pollCards.single()
        val input = card.input!!
        input.requestFocus()
        val ime = input.onCreateInputConnection(EditorInfo())!!
        ime.commitText("abc", 1)
        ime.setComposingText("defgh", 1)
        assertEquals("abcdefgh", input.text.toString())
        assertTrue("the counter is of the cut text", texts(card).contains("5/5"))
        ime.finishComposingText()
        assertEquals("abcde", input.text.toString())

        ime.setComposingText("xy", 1)
        assertEquals("abcdexy", input.text.toString())
        ime.commitText("xy", 1)
        assertEquals("abcde", input.text.toString())

        // A surrogate pair is not cut in half.
        ime.deleteSurroundingText(1, 0)
        ime.commitText("😀", 1)
        assertEquals("abcd", input.text.toString())
        (card.first { it is TextView && it.text == "Send" } as TextView).performClick()
        assertEquals(listOf("answer t {text=abcd, story_id=9001, slide_id=1}"), transport.calls)
    }

    @Test
    fun `text re-answer empties the field and opens the keyboard`() {
        StoryPollStore.put("", "mini_poll", "demo_text", StoryPollStore.Entry(StoriesPollAnswer(text = "Старый ответ"), null))
        val card = laidOut(feed[2]).pollCards.single()
        assertNull(card.input)
        assertTrue(texts(card).contains("Старый ответ"))

        (card.first { it is TextView && it.text == "Ответить заново" } as TextView).performClick()
        idle()
        val input = card.input!!
        assertEquals("", input.text.toString())
        assertTrue(input.hasFocus())
        assertEquals(listOf(true), host.pollHolds)
    }

    @Test
    fun `a focused field replaced by the answer releases the hold`() {
        val card = laidOut(feed[2]).pollCards.single()
        val input = card.input!!
        input.requestFocus()
        input.setText("Книга")
        (card.first { it is TextView && it.text == "Send" } as TextView).performClick()
        transport.succeed(null, null)
        idle()
        assertNull(card.input)
        assertEquals(listOf(true, false), host.pollHolds)
    }

    @Test
    fun `without y_offset the card is on top, without allow_reanswer the answer has no link`() {
        val view = laidOut(Slide(JSONObject("""{"id":"1","type":"","elements":[
            {"type":"poll","poll_type":"mini_poll","y_offset":null,"allow_reanswer":"1",
             "poll":{"code":"s","kind":"single_choice","question":"Q","thanks_text":"",
             "options":[{"id":1,"label":"A"},{"id":2,"label":"B"}]},
             "answer":{"option_ids":[2]},"results":{"total":3}}]}""")))
        val card = view.pollCards.single()
        assertEquals(0, card.top)
        // No thanks_text — the default pair; no results.options — no total, no votes on the right.
        assertEquals(listOf("Results", "Q", "Thanks! Vote counted"), texts(card))
        assertEquals(listOf(false, true), rows(card).map { it.selectedState })
        assertTrue("no percents without results.options", rows(card).all { it.percentText == null })

        fun offset(value: String) = StoryPoll.parse(JSONObject("""{"type":"poll","poll_type":"nps","y_offset":$value,"poll":{}}"""))!!.yOffset
        assertEquals(listOf(0f, 100f, 12.5f), listOf("-10", "150", "\"12.5\"").map(::offset))
    }

    @Test
    fun `a fractional rate of the feed outlines no cell`() {
        val view = laidOut(Slide(JSONObject("""{"id":"1","type":"","elements":[
            {"type":"poll","poll_type":"csat","poll":{"code":"c","bands":[{"from":3,"to":4,"success":"Хорошо"}]},"answer":{"rate":3.5}}]}""")))
        val card = view.pollCards.single()
        val row = card.first { it is PollScaleRow } as PollScaleRow
        assertEquals(3.5, row.selectedValue)
        assertTrue(row.values.none { row.cell(it)!!.isSelected })
        assertTrue(row.values.all { row.cell(it)!!.alpha == PollStyle.MUTED_ALPHA })
        assertTrue(texts(card).contains("Хорошо"))
    }

    @Test
    fun `a scale draws its cells and the band text after the answer`() {
        val card = laidOut(feed[3]).pollCards.single()
        val row = card.first { it is PollScaleRow } as PollScaleRow
        assertEquals((0..10).toList(), row.values)
        row.cell(9)!!.performClick()
        assertEquals(listOf("review nps {channel=test, category=test, rate=9}"), transport.calls)
        transport.succeed(null, null)
        idle()
        val answered = card.first { it is PollScaleRow } as PollScaleRow
        assertEquals(9.0, answered.selectedValue)
        assertEquals(PollStyle.MUTED_ALPHA, answered.cell(8)!!.alpha)
        assertEquals(1f, answered.cell(9)!!.alpha)
        assertTrue(texts(card).contains("Спасибо, мы рады!"))
    }

    @Test
    fun `the answered poll of the feed shows its results, the store wins after a rebuild`() {
        val card = laidOut(feed[6]).pollCards.single()
        assertEquals(listOf("40%", "60%"), rows(card).map { it.percentText.toString() })
        StoryPollStore.put("", "mini_poll", "demo_single", StoryPollStore.Entry(StoriesPollAnswer(optionIds = listOf(3)), null))
        val again = laidOut(feed[0]).pollCards.single()
        assertTrue(rows(again)[2].selectedState)
        assertNull(rows(again)[2].percentText)
    }

    @Test
    fun `the card top is y_offset of the whole slide, moved inside the middle`() {
        val view = laidOut(feed[3])
        val card = view.pollCards.single()
        val layer = card.parent as View
        // y_offset 60 is a share of the whole slide, like `top` on the web, not of the middle.
        val layerTop = windowTop(layer) - windowTop(view)
        assertTrue("the middle starts under the header", layerTop > 0)
        val expected = (view.height * 0.6f - layerTop).coerceIn(0f, (layer.height - card.height).toFloat())
        assertTrue("${card.top} vs $expected", Math.abs(card.top - expected) <= 1)
        assertEquals((layer.width - card.width) / 2, card.left)
        // 12 from the edges of the slide, the text blocks under it keep 16.
        val inset = (12 * view.resources.displayMetrics.density).toInt()
        assertEquals(inset, windowLeft(card) - windowLeft(view))
        assertEquals(inset, (windowLeft(view) + view.width) - (windowLeft(card) + card.width))
        // y_offset 5 is under the header — the card moves down to the middle.
        assertEquals(0, laidOut(feed[5]).pollCards.single().top)
        // y_offset 90 — the card stays inside the layer.
        val low = laidOut(feed[4]).pollCards.single()
        assertEquals((low.parent as View).height, low.bottom)
    }

    private fun windowTop(view: View): Int = IntArray(2).also(view::getLocationInWindow)[1]

    private fun windowLeft(view: View): Int = IntArray(2).also(view::getLocationInWindow)[0]

    @Test
    fun `a text block and a poll with one y_offset stand at one height`() {
        fun slide(offset: String) = laidOut(Slide(JSONObject("""{"id":"1","type":"","elements":[
            {"type":"text_block","text_input":"Text","y_offset":$offset},
            {"type":"poll","poll_type":"mini_poll","y_offset":$offset,
             "poll":{"code":"s","kind":"single_choice","options":[{"id":1,"label":"A"}]}}]}""")))
        listOf("null", "5", "50").forEach { offset ->
            val view = slide(offset)
            val block = all(view).filterIsInstance<PersonalizationStoryTextBlock>().single()
            assertEquals("y_offset $offset", windowTop(view.pollCards.single()), windowTop(block))
        }
        // 95: both stand on the bottom of the area.
        val view = slide("95")
        val block = all(view).filterIsInstance<PersonalizationStoryTextBlock>().single()
        assertEquals(view.pollCards.single().bottomOnScreen(), block.bottomOnScreen())
    }

    @Test
    fun `a product slide draws the poll too`() {
        val slide = Slide(JSONObject("""{"id":"39_0","type":"","elements":[
            {"type":"product","item":{"id":"p1","name":"Sneakers","price_formatted":"100 ₽"}},
            {"type":"poll","poll_type":"csat","poll":{"code":"c"}}]}"""))
        val view = laidOut(slide)
        (view.pollCards.single().first { it is PollScaleRow } as PollScaleRow).cell(5)!!.performClick()
        assertEquals(listOf("review csat {channel=stories, campaign=c, rate=5}"), transport.calls)
    }

    @Test
    fun `touches on the card do not reach the slide and hold it`() {
        val view = laidOut(feed[3])
        val card = view.pollCards.single()
        val title = card.first { it is TextView && it.text.startsWith("Насколько") }!!
        val (x, y) = center(title)
        val now = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        assertEquals(listOf(true), host.pollHolds)
        shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 500, MotionEvent.ACTION_UP, x, y, 0))

        assertEquals(listOf(true, false), host.pollHolds)
        assertTrue(host.taps.isEmpty())
        assertTrue("no long press pause of the slide", host.holds.isEmpty())
        // Next to the card the slide still pages.
        val aside = view.height * 0.2f
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 600, MotionEvent.ACTION_DOWN, view.width - 5f, aside, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 650, MotionEvent.ACTION_UP, view.width - 5f, aside, 0))
        assertEquals(1, host.taps.size)
    }

    @Test
    fun `with the keyboard open a tap next to the card only hides it`() {
        val view = laidOut(feed[2])
        val card = view.pollCards.single()
        val input = card.input!!
        input.requestFocus()
        assertTrue(input.hasFocus())
        assertEquals(listOf(true), host.pollHolds)

        val now = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, view.width - 5f, view.height - 5f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, view.width - 5f, view.height - 5f, 0))

        assertTrue(host.taps.isEmpty())
        assertFalse(input.hasFocus())
        assertEquals(listOf(true, false), host.pollHolds)
    }

    @Test
    fun `the keyboard lifts the focused card and its hiding takes the focus away`() {
        val view = laidOut(feed[2])
        val card = view.pollCards.single()
        card.input!!.requestFocus()
        val window = view.rootView.height
        view.applyIme(window - card.bottomOnScreen() + 100)
        assertTrue(card.translationY < 0)
        view.applyIme(0)
        assertEquals(0f, card.translationY)
        assertFalse(card.input!!.hasFocus())
    }

    @Test
    fun `with little room above the keyboard the card shrinks and stays below the header`() {
        val view = laidOut(feed[2])
        val card = view.pollCards.single()
        val full = card.height
        card.input!!.requestFocus()
        val layer = card.parent as View
        val layerTop = IntArray(2).also(layer::getLocationInWindow)[1]
        val keyboardTop = layerTop + full / 2
        view.applyIme(view.rootView.height - keyboardTop)
        idle()

        assertTrue(card.height < full)
        assertTrue("not above the middle of the slide", card.top + card.translationY >= 0f)
        assertTrue("above the keyboard", card.bottomOnScreen() <= keyboardTop)
        view.applyIme(0)
        idle()
        assertEquals(full, card.height)
    }

    @Test
    fun `the number of votes is declined in English`() {
        val resources = activity.resources
        assertNull(PollUi.votes(resources, 0))
        assertEquals(listOf("1 vote", "2 votes", "5 votes", "21 votes", "1,248 votes"), listOf(1, 2, 5, 21, 1248).map { PollUi.votes(resources, it) })
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the number of votes is declined in Russian`() {
        val resources = Robolectric.buildActivity(Activity::class.java).setup().get().resources
        assertNull(PollUi.votes(resources, -1))
        val votes = listOf(1, 2, 4, 5, 11, 12, 14, 21, 22, 25, 101, 111, 1248).map { PollUi.votes(resources, it)!!.replace('\u00A0', ' ').replace('\u202F', ' ') }
        assertEquals(
            listOf("1 голос", "2 голоса", "4 голоса", "5 голосов", "11 голосов", "12 голосов", "14 голосов", "21 голос", "22 голоса", "25 голосов", "101 голос", "111 голосов", "1 248 голосов"),
            votes
        )
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the texts of the results are in Russian`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = StorySlideView(activity, 0, 0, Slide(JSONObject("""{"id":"1","type":"","elements":[
            {"type":"poll","poll_type":"mini_poll","poll":{"code":"s","kind":"single_choice","question":"Q",
             "options":[{"id":1,"label":"A"}]},"answer":{"option_ids":[1]},"results":{"total":3,"options":[{"id":1,"percent":100}]}}]}""")), 1, host, null, 9001)
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        idle()
        assertEquals(listOf("Результаты", "Q", "Спасибо! Голос учтён", "3 голоса"), texts(view.pollCards.single()))
    }

    @Test
    fun `option letters, the text on a fill is the more contrasting of white and dark`() {
        assertEquals(listOf("A", "B", "Z", "AA", "AZ", "BA"), listOf(0, 1, 25, 26, 51, 52).map(PollUi::letter))
        val dark = 0xFF000000.toInt()
        assertEquals("dark on the green accent", dark, PollUi.onFill(activity, PollStyle.ACCENT))
        assertEquals("white on a deep fill", Color.WHITE, PollUi.onFill(activity, 0xFF15803D.toInt()))
        // The pale colors of the scale bands — a dark digit.
        listOf(0, 3, 6, 7, 9).forEach { value ->
            assertEquals("NPS $value", dark, PollUi.onFill(activity, StoryPoll.scaleColor(StoryPoll.Type.NPS, value, 0, 10)))
        }
    }

    @Test
    fun `the chosen row is filled only in the form, in the results it keeps the plain surface`() {
        val card = laidOut(feed[1]).pollCards.single()
        assertEquals(PollStyle.SURFACE, rows(card)[0].surface)
        rows(card)[0].performClick()
        assertEquals(selectedFill(), rows(card)[0].surface)
        (card.first { it is TextView && it.text == "Отправить ответ" } as TextView).performClick()
        transport.succeed(null, StoriesPollResults(2, listOf(StoriesPollResults.Option(1, 50.0))))
        idle()
        val chosen = rows(card)[0]
        assertTrue(chosen.selectedState)
        assertEquals(PollStyle.SURFACE, chosen.surface)
    }

    private fun selectedFill(): Int = (PollStyle.ACCENT and 0x00FFFFFF) or (Math.round(PollStyle.ACCENT_FILL_ALPHA * 255) shl 24)

    @Test
    fun `the chosen digit of a scale stands on the color of its band`() {
        val card = laidOut(feed[4]).pollCards.single()
        val row = card.first { it is PollScaleRow } as PollScaleRow
        row.cell(2)!!.performClick()
        assertTrue(row.cell(2)!!.isSelected)
        assertEquals("a dark digit on the pale band", row.cell(1)!!.currentTextColor, row.cell(2)!!.currentTextColor)
        assertEquals(listOf("review csat {channel=stories, campaign=demo_csat, rate=2}"), transport.calls)
    }

    private fun View.bottomOnScreen(): Int = IntArray(2).also(::getLocationInWindow)[1] + height

    private fun textSlide(maxLength: Int): Slide = Slide(JSONObject("""{"id":"1","type":"","elements":[
        {"type":"poll","poll_type":"mini_poll","poll":{"code":"t","kind":"text","max_length":$maxLength}}]}"""))

    private fun laidOut(slide: Slide, polls: PollHost = host): StorySlideView =
        StorySlideView(activity, 0, 0, slide, 1, polls, null, 9001).also { view ->
            activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            idle()
        }

    private fun rows(card: View): List<PollOptionRow> = all(card).filterIsInstance<PollOptionRow>()

    private fun texts(card: View): List<String> =
        all(card).filter { it is TextView && it !is EditText && it.visibility == View.VISIBLE && it.parent !is PollOptionRow }
            .map { (it as TextView).text.toString() }.filter { it.isNotEmpty() }

    private fun View.first(predicate: (View) -> Boolean): View? = all(this).firstOrNull(predicate)

    private fun all(view: View): List<View> =
        if (view !is ViewGroup) listOf(view) else listOf(view) + (0 until view.childCount).flatMap { all(view.getChildAt(it)) }

    private fun center(view: View): Pair<Float, Float> {
        val location = IntArray(2).also(view::getLocationOnScreen)
        return location[0] + view.width / 2f to location[1] + view.height / 2f
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** A slide host with polls through [transport]; `null` — polls are off. */
    internal class PollHost(transport: StoriesPollsTransport?) : StorySlideHost {
        override val texts = StoriesTexts("Promo code", "Copied", "More info", "Used products", "Hide products", "Retry", "Close", "Sound on", "Sound off", "Share")
        override val muted = true
        override val sheetOpen = false
        override val insets: Insets = Insets.NONE
        override val polls: StoryPollsContext? = transport?.let { StoryPollsContext(it, "", texts.pollTexts) }
        val taps = mutableListOf<Float>()
        val holds = mutableListOf<Boolean>()
        val pollHolds = mutableListOf<Boolean>()

        override fun loadImage(view: ImageView, url: String, onReady: (() -> Unit)?, onFailed: (() -> Unit)?) = Unit
        override fun loadSlideImage(view: ImageView, url: String, onAnimation: (Long?, GifDrawable) -> Unit, onReady: () -> Unit, onFailed: () -> Unit) = Unit
        override fun onTap(x: Float, width: Int, rtl: Boolean) {
            taps += x
        }
        override fun onHold(hold: Boolean) {
            holds += hold
        }
        override fun onPollHold(view: StorySlideView, hold: Boolean) {
            pollHolds += hold
        }
        override fun onOutsideSheetTap() = Unit
        override fun onSheet(open: Boolean) = Unit
        override fun onButton(button: ButtonElement, product: Product?) = Unit
        override fun onProduct(product: Product) = Unit
        override fun onCarouselProduct(product: Product) = Unit
        override fun onPromoCopied() = Unit
        override fun onSoundToggle() = Unit
        override fun onRetry(view: StorySlideView) = Unit
        override fun onClose() = Unit
        override fun onShare(url: String) = Unit
    }
}
