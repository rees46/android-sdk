package com.personalization.ui.widgets

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** The card logic — the cases of `create-poll.test.ts` of the web SDK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoryPollControllerTest {

    private val transport = FakePollsTransport()
    private val holds = mutableListOf<Boolean>()

    @Before
    fun setUp() = StoryPollStore.clear()

    @After
    fun tearDown() = StoryPollStore.clear()

    @Test
    fun `single choice is sent on tap and shows thanks and results`() {
        val card = card(miniPoll("single_choice"))
        assertFalse(card.answered)

        card.choose(2)
        assertEquals(listOf("answer gift_recipient {option_ids=[2], story_id=5, slide_id=101}"), transport.calls)
        assertTrue(card.sending)
        assertEquals(setOf(2), card.selected)

        transport.succeed(StoriesPollAnswer(optionIds = listOf(2), id = 77), results(1 to 40.0, 2 to 60.0))
        assertTrue(card.answered)
        assertFalse(card.sending)
        assertEquals(listOf(2), card.answer!!.optionIds)
        assertEquals(60, card.results!!.percentOf(2))
        assertFalse(card.error)
    }

    @Test
    fun `nothing is sent twice while the request is pending`() {
        val card = card(miniPoll("single_choice"))
        card.choose(1)
        card.choose(1)
        card.choose(3)
        assertEquals(1, transport.calls.size)
        assertEquals(setOf(1), card.selected)
    }

    @Test
    fun `an error rolls the choice back and allows a retry`() {
        val card = card(miniPoll("single_choice"))
        card.choose(1)
        transport.fail(429)

        assertTrue(card.error)
        assertFalse(card.sending)
        assertTrue(card.selected.isEmpty())

        card.choose(1)
        assertEquals(2, transport.calls.size)
        assertFalse(card.error)
        transport.succeed(StoriesPollAnswer(optionIds = listOf(1)), null)
        assertTrue(card.answered)
        assertNull(card.results)
    }

    @Test
    fun `the sent answer is shown when the server returns none`() {
        val card = card(miniPoll("single_choice"))
        card.choose(3)
        transport.succeed(null, null)
        assertEquals(listOf(3), card.answer!!.optionIds)
    }

    @Test
    fun `multiple choice sends the checked options in the order of the poll`() {
        val card = card(miniPoll("multiple_choice"))
        assertFalse(card.canSubmit)
        card.toggle(3)
        card.toggle(1)
        assertTrue(card.canSubmit)
        assertTrue(transport.calls.isEmpty())
        card.toggle(3)
        card.toggle(3)

        card.submit()
        assertEquals(listOf("answer gift_recipient {option_ids=[1, 3], story_id=5, slide_id=101}"), transport.calls)
        transport.succeed(StoriesPollAnswer(optionIds = listOf(1, 3)), null)
        assertEquals(listOf(1, 3), card.answer!!.optionIds)
    }

    @Test
    fun `submit stays off when every chip is unchecked`() {
        val card = card(miniPoll("multiple_choice"))
        card.toggle(1)
        card.toggle(1)
        assertFalse(card.canSubmit)
        card.submit()
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `the submit label falls back to the texts`() {
        assertEquals("Send", card(miniPoll("multiple_choice", poll = """"submit_label":null""")).texts.submit)
        assertEquals("Ещё раз", card(miniPoll("single_choice", element = """"labels":{"reanswer":"Ещё раз","error":""}""")).texts.reanswer)
        assertEquals("Ошибка", card(miniPoll("single_choice"), texts = PollTexts("a", "Ошибка", "b")).texts.error)
    }

    @Test
    fun `text counts characters, is cut at max_length and sent trimmed`() {
        val card = card(miniPoll("text"))
        assertEquals("0/10", card.counter)
        card.setText("   ")
        assertFalse(card.canSubmit)
        card.setText(" Мастер и Маргарита ")
        assertEquals(" Мастер и ", card.text)
        assertEquals("10/10", card.counter)

        card.submit()
        assertEquals(listOf("answer gift_recipient {text=Мастер и, story_id=5, slide_id=101}"), transport.calls)
        transport.succeed(StoriesPollAnswer(text = "Мастер и"), null)
        assertTrue(card.answered)
        assertEquals("Мастер и", card.answer!!.text)
    }

    @Test
    fun `text keeps a surrogate pair whole at the limit`() {
        val card = card(miniPoll("text", poll = """"max_length":"3.9""""))
        card.setText("ab😀")
        assertEquals("ab", card.text)
        assertEquals("2/3", card.counter)
        card.setText("a😀c")
        assertEquals("a😀", card.text)
        // max_length above 0 but under 1 is no limit, like the web SDK.
        assertNull(card(miniPoll("text", poll = """"max_length":0.5""")).counter)
    }

    @Test
    fun `text has no counter without max_length`() {
        assertNull(card(miniPoll("text", poll = """"max_length":null""")).counter)
    }

    @Test
    fun `nps sends the rate on tap with its category and the default channel`() {
        val card = card(scalePoll("nps", poll = """"channel":null"""))
        card.rate(9)
        assertEquals(listOf("review nps {channel=stories, category=delivery, rate=9}"), transport.calls)
        assertEquals(9.0, card.pressedRate)
        transport.succeed(null, results(1 to 100.0))
        assertTrue(card.answered)
        assertEquals(9.0, card.answer!!.rate)
        assertNull(card.results)
        assertEquals("Спасибо, рады стараться!", card.poll.bandText(card.answer!!.rate))
    }

    @Test
    fun `csat and ces send a campaign`() {
        card(scalePoll("csat")).rate(1)
        card(scalePoll("ces", poll = """"channel":"popup"""")).rate(7)
        assertEquals(
            listOf("review csat {channel=stories, campaign=delivery, rate=1}", "review ces {channel=popup, campaign=delivery, rate=7}"),
            transport.calls
        )
    }

    @Test
    fun `an answered scale from the feed takes no taps`() {
        val card = card(scalePoll("nps", element = """"answer":{"rate":3}"""))
        assertTrue(card.answered)
        card.rate(5)
        assertTrue(transport.calls.isEmpty())
        assertEquals("Спасибо, мы станем лучше", card.poll.bandText(card.answer!!.rate))
    }

    @Test
    fun `an answered choice from the feed takes no taps without allow_reanswer`() {
        val card = card(miniPoll("single_choice", element = ANSWERED))
        assertTrue(card.answered)
        assertEquals(40, card.results!!.percentOf(1))
        card.reanswer()
        card.choose(1)
        assertTrue(card.answered)
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `answer again resets the form with the previous answer highlighted`() {
        val card = card(miniPoll("single_choice", element = "$ANSWERED,\"allow_reanswer\":true"))
        card.reanswer()
        assertFalse(card.answered)
        assertEquals(setOf(2), card.selected)

        card.choose(1)
        assertEquals(setOf(1), card.selected)
        transport.succeed(StoriesPollAnswer(optionIds = listOf(1)), null)
        assertTrue(card.answered)
        assertEquals(StoryPollStore.Entry(StoriesPollAnswer(optionIds = listOf(1)), null), StoryPollStore.get("shop1", "mini_poll", "gift_recipient"))
    }

    @Test
    fun `a failed re-answer restores the previous choice`() {
        val card = card(miniPoll("single_choice", element = "$ANSWERED,\"allow_reanswer\":true"))
        card.reanswer()
        card.choose(3)
        transport.fail(500)
        assertEquals(setOf(2), card.selected)
        assertFalse(card.answered)
    }

    @Test
    fun `multiple choice re-answer starts from the previous options`() {
        val card = card(miniPoll("multiple_choice", element = """"answer":{"option_ids":[1,3]},"allow_reanswer":true"""))
        card.reanswer()
        assertEquals(setOf(1, 3), card.selected)
        assertTrue(card.canSubmit)
        card.toggle(1)
        card.submit()
        assertEquals(listOf("answer gift_recipient {option_ids=[3], story_id=5, slide_id=101}"), transport.calls)
    }

    @Test
    fun `scale re-answer highlights the previous rate and restores it after an error`() {
        val card = card(scalePoll("nps", element = """"answer":{"rate":3},"allow_reanswer":true"""))
        card.reanswer()
        assertFalse(card.answered)
        assertEquals(3.0, card.pressedRate)
        card.rate(8)
        assertEquals(8.0, card.pressedRate)
        transport.fail(402)
        assertEquals(3.0, card.pressedRate)
    }

    @Test
    fun `the answer of the server wins over the sent one, its results replace the feed`() {
        val card = card(miniPoll("single_choice", element = "$ANSWERED,\"allow_reanswer\":true"))
        card.reanswer()
        card.choose(1)
        // Re-answer is off on the server: it returns the previous answer without results.
        transport.succeed(StoriesPollAnswer(optionIds = listOf(2), id = 77), null)
        assertEquals(listOf(2), card.answer!!.optionIds)
        assertNull(card.results)
        assertEquals(StoryPollStore.Entry(StoriesPollAnswer(optionIds = listOf(2), id = 77), null), StoryPollStore.get("shop1", "mini_poll", "gift_recipient"))
    }

    @Test
    fun `any answer object of the server is the answer, the card is answered anyway`() {
        val card = card(miniPoll("single_choice"))
        card.choose(2)
        transport.succeed(StoriesPollAnswer(id = 77), null)
        assertTrue(card.answered)
        assertEquals(StoriesPollAnswer(id = 77), card.answer)
        assertTrue(card.selected.isEmpty())
    }

    @Test
    fun `the card does not log a failure of its transport`() {
        card(miniPoll("single_choice")).choose(1)
        transport.fail(429)
        assertTrue(ShadowLog.getLogs().none { it.msg.orEmpty().contains("stories poll") })
    }

    @Test
    fun `a card from the store is answered even if its answer highlights nothing`() {
        StoryPollStore.put("shop1", "mini_poll", "gift_recipient", StoryPollStore.Entry(StoriesPollAnswer(id = 77), null))
        val card = card(miniPoll("single_choice"))
        assertTrue(card.answered)
        assertTrue(card.selected.isEmpty())
        card.choose(1)
        assertTrue("no form to tap", transport.calls.isEmpty())
    }

    @Test
    fun `a fractional rate of the feed is an answer that highlights no cell`() {
        val card = card(scalePoll("nps", element = """"answer":{"rate":6.5},"allow_reanswer":true""", poll = """"bands":[{"from":6,"to":7,"success":"Ок"}]"""))
        assertTrue(card.answered)
        assertEquals("Ок", card.poll.bandText(card.answer!!.rate))
        card.reanswer()
        assertEquals(6.5, card.pressedRate)
        card.rate(7)
        assertEquals("review nps {channel=stories, category=delivery, rate=7}", transport.calls.single())
        transport.fail(500)
        assertEquals(6.5, card.pressedRate)
    }

    @Test
    fun `a poll without a code is sent to the empty code and shows the error of the server`() {
        val card = card(miniPoll("single_choice", poll = """"code":null"""))
        card.choose(1)
        assertEquals(listOf("answer  {option_ids=[1], story_id=5, slide_id=101}"), transport.calls)
        transport.fail(404)
        assertTrue(card.error)
        card.choose(1)
        transport.succeed(null, null)
        assertTrue(card.answered)
        assertEquals(listOf(1), StoryPollStore.get("shop1", "mini_poll", "")!!.answer!!.optionIds)

        card(scalePoll("csat", poll = """"code":null""")).rate(4)
        assertEquals("review csat {channel=stories, campaign=, rate=4}", transport.calls.last())
    }

    @Test
    fun `text re-answer starts empty and asks for the focus`() {
        val card = card(miniPoll("text", element = """"answer":{"text":"Букварь"},"allow_reanswer":true"""))
        assertEquals("Букварь", card.answer!!.text)
        card.reanswer()
        assertFalse(card.answered)
        assertEquals("", card.text)
        assertTrue(card.focusRequested)
    }

    @Test
    fun `the session store wins over the feed`() {
        StoryPollStore.put("shop1", "mini_poll", "gift_recipient", StoryPollStore.Entry(StoriesPollAnswer(optionIds = listOf(3)), results(3 to 100.0)))
        val card = card(miniPoll("single_choice", element = ANSWERED))
        assertEquals(listOf(3), card.answer!!.optionIds)
        assertEquals(100, card.results!!.percentOf(3))
        // Another shop, another poll type — another key.
        assertFalse(card(miniPoll("single_choice"), shop = "shop2").answered)
        assertFalse(card(scalePoll("nps", poll = """"code":"gift_recipient"""")).answered)
    }

    @Test
    fun `the slide holds while the card is pressed, focused or sending`() {
        val card = card(miniPoll("text"))
        card.setPressed(true)
        card.setPressed(true)
        assertEquals(listOf(true), holds)
        card.setPressed(false)
        assertEquals(listOf(true, false), holds)

        card.setFocused(true)
        card.setPressed(true)
        card.setPressed(false)
        assertEquals(listOf(true, false, true), holds)
        card.setText("Книга")
        card.submit()
        card.setFocused(false)
        assertEquals("still sending", listOf(true, false, true), holds)
        transport.succeed(StoriesPollAnswer(text = "Книга"), null)
        assertEquals(listOf(true, false, true, false), holds)
    }

    @Test
    fun `a failed send recomputes the focus that left while sending`() {
        val card = card(miniPoll("text"))
        card.setFocused(true)
        card.setText("Книга")
        card.submit()
        // The field lost the focus while the answer was on its way.
        card.setFocused(false)
        assertEquals(listOf(true), holds)
        transport.fail(500)
        assertEquals(listOf(true, false), holds)
        assertTrue(card.canSubmit)
    }

    @Test
    fun `an error releases the hold of sending`() {
        val card = card(scalePoll("csat"))
        card.rate(5)
        assertEquals(listOf(true), holds)
        transport.fail(-1)
        assertEquals(listOf(true, false), holds)
        assertTrue(card.error)
    }

    @Test
    fun `an answer after the card left goes only to the store`() {
        val card = card(miniPoll("single_choice"))
        card.choose(2)
        assertEquals(listOf(true), holds)
        card.detach()

        transport.succeed(StoriesPollAnswer(optionIds = listOf(2)), results(2 to 100.0))
        assertEquals(listOf(true), holds)
        assertFalse(card.answered)
        assertEquals(listOf(2), StoryPollStore.get("shop1", "mini_poll", "gift_recipient")!!.answer!!.optionIds)
        // The slide is built again — the card shows the answer.
        assertTrue(card(miniPoll("single_choice")).answered)
    }

    @Test
    fun `a removed card does not resume the slide`() {
        val card = card(miniPoll("text"))
        card.setPressed(true)
        card.detach()
        card.setPressed(false)
        assertEquals(listOf(true), holds)
    }

    @Test
    fun `a second callback of the transport is ignored`() {
        val card = card(miniPoll("single_choice"))
        card.choose(1)
        val callback = transport.pending.single()
        callback(StoriesPollsResult.Failure(500, "fail"))
        callback(StoriesPollsResult.Success(StoriesPollAnswer(optionIds = listOf(1))))
        assertFalse(card.answered)
        assertTrue(card.error)
    }

    @Test
    fun `a transport that throws is an error`() {
        val card = StoryPollController(miniPoll("single_choice"), 5, "101", "shop1", object : StoriesPollsTransport {
            override fun answerMiniPoll(code: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) = error("offline")
            override fun review(kind: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) = Unit
        }, TEXTS, onHold = { holds += it }, post = { it() })
        card.choose(1)
        assertTrue(card.error)
        assertFalse(card.sending)
        assertEquals(listOf(true, false), holds)
    }

    private fun card(poll: StoryPoll, texts: PollTexts = TEXTS, shop: String = "shop1") =
        StoryPollController(poll, 5, "101", shop, transport, texts, onHold = { holds += it }, post = { it() })

    private fun results(vararg options: Pair<Int, Double>) =
        StoriesPollResults(options.size, options.map { StoriesPollResults.Option(it.first, it.second) })

    internal companion object {
        val TEXTS = PollTexts("Answer again", "Failed to send the answer. Please try again", "Send")

        const val ANSWERED = """"answer":{"option_ids":[2],"text":null},"results":{"total":120,"options":[{"id":1,"percent":40},{"id":2,"percent":60}]}"""

        /** Mini-poll of the web SDK tests; [element] and [poll] override fields as JSON members. */
        fun miniPoll(kind: String, element: String = "", poll: String = ""): StoryPoll {
            val text = kind == "text"
            val json = JSONObject(
                """{"type":"poll","poll_type":"mini_poll","y_offset":40,"allow_reanswer":false,"answer":null,"results":null,
                "poll":{"code":"gift_recipient","kind":"$kind","question":"Кому выбираете подарок?",
                "placeholder":${if (text) "\"Название или автор\"" else "null"},"max_length":${if (text) "10" else "null"},
                "submit_label":"Отправить","thanks_text":"Спасибо!","show_results":true,
                "options":[{"id":1,"label":"Себе"},{"id":2,"label":"Маме"},{"id":3,"label":"Папе"}]}}"""
            )
            return build(json, element, poll)
        }

        fun scalePoll(type: String, element: String = "", poll: String = ""): StoryPoll {
            val max = when (type) {
                "nps" -> 10
                "csat" -> 5
                else -> 7
            }
            val json = JSONObject(
                """{"type":"poll","poll_type":"$type","y_offset":50,"allow_reanswer":false,"answer":null,"results":null,
                "poll":{"code":"delivery","channel":"stories","name":"Доставка","rate_min":${if (type == "nps") 0 else 1},
                "rate_max":$max,"question":"Как вам доставка?","bands":[
                {"from":0,"to":6,"success":"Спасибо, мы станем лучше"},{"from":7,"to":8,"success":"Спасибо!"},
                {"from":9,"to":10,"success":"Спасибо, рады стараться!"}]}}"""
            )
            return build(json, element, poll)
        }

        private fun build(json: JSONObject, element: String, poll: String): StoryPoll {
            val extra = JSONObject("{$element}")
            extra.keys().forEach { json.put(it, extra.get(it)) }
            val pollJson = json.getJSONObject("poll")
            val pollExtra = JSONObject("{$poll}")
            pollExtra.keys().forEach { pollJson.put(it, pollExtra.get(it)) }
            return requireNotNull(StoryPoll.parse(json)) { "poll is not parsed: $json" }
        }
    }
}

/** Transport that answers when the test says so. */
internal class FakePollsTransport : StoriesPollsTransport {
    val calls = mutableListOf<String>()
    val pending = mutableListOf<(StoriesPollsResult) -> Unit>()

    override fun answerMiniPoll(code: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) {
        calls += "answer $code $body"
        pending += callback
    }

    override fun review(kind: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) {
        calls += "review $kind $body"
        pending += callback
    }

    fun succeed(answer: StoriesPollAnswer?, results: StoriesPollResults?) =
        pending.removeAt(0)(StoriesPollsResult.Success(answer, results))

    fun fail(code: Int) = pending.removeAt(0)(StoriesPollsResult.Failure(code, "fail"))
}
