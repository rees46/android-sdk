package com.personalization.ui.widgets

import android.app.Activity
import android.graphics.Color
import android.util.Log
import com.personalization.Fixtures
import com.personalization.stories.models.Slide
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/** The `poll` element: parsing of the shared feed fixture, scales, bands, texts, request bodies. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoryPollTest {

    private val slides: List<Slide> by lazy {
        val story = StoriesApiV3.parse(Fixtures.read("stories_polls_feed.json")).single()
        List(story.slidesCount) { story.getSlide(it) }
    }

    private fun poll(index: Int): StoryPoll = slides[index].polls.single()

    @Test
    fun `every kind of the fixture is parsed, the unknown one is skipped`() {
        assertEquals(8, slides.size)
        assertEquals(
            listOf(
                StoryPoll.Type.SINGLE_CHOICE, StoryPoll.Type.MULTIPLE_CHOICE, StoryPoll.Type.TEXT,
                StoryPoll.Type.NPS, StoryPoll.Type.CSAT, StoryPoll.Type.CES, StoryPoll.Type.SINGLE_CHOICE
            ),
            slides.take(7).map { it.polls.single().type }
        )
        assertTrue(slides[7].polls.isEmpty())
        // V1 never sees polls: they are not elements.
        assertTrue(slides.all { it.getElements().isEmpty() })
    }

    @Test
    fun `numbers may come as strings`() {
        assertEquals(40f, poll(1).yOffset)
        assertEquals(120, poll(2).maxLength)
        assertEquals(1, poll(5).rateMin)
        assertEquals(7, poll(5).rateMax)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), poll(5).scale)
        assertEquals(listOf(5f, 30f, 40f, 50f, 60f, 90f), (0..5).map { poll(it).yOffset }.sortedBy { it })
    }

    @Test
    fun `fields of a single choice and its labels`() {
        val single = poll(0)
        assertEquals("mini_poll", single.pollType)
        assertEquals("demo_single", single.code)
        assertEquals("Что вам больше нравится?", single.question)
        assertEquals(listOf(1 to "Кроссовки", 2 to "Ботинки", 3 to "Сандалии"), single.options.map { it.id to it.label })
        assertTrue(single.allowReanswer)
        assertNull(single.submitLabel)
        assertNull(single.answer)
        assertEquals(StoryPoll.Labels("Ответить заново", "Не удалось отправить ответ. Попробуйте еще раз", null), single.labels)
        assertEquals("Отправить ответ", poll(1).submitLabel)
        assertEquals("Ваш ответ", poll(2).placeholder)
    }

    @Test
    fun `an answered poll keeps its answer and results`() {
        val answered = poll(6)
        assertEquals(StoriesPollAnswer(optionIds = listOf(2), id = 77), answered.answer)
        assertTrue(answered.answer!!.isAnswered)
        assertEquals(120, answered.results!!.total)
        assertEquals(40, answered.results!!.percentOf(1))
        assertEquals(60, answered.results!!.percentOf(2))
        assertEquals(0, answered.results!!.percentOf(3))
    }

    @Test
    fun `scales take their channel and bands, null fields fall back`() {
        val nps = poll(3)
        assertEquals("test", nps.code)
        assertEquals("test", nps.channel)
        assertEquals(11, nps.scale.size)
        val csat = poll(4)
        assertNull(csat.channel)
        assertEquals(listOf(1, 2, 3, 4, 5), csat.scale)
        assertTrue(poll(5).bands.isEmpty())
        assertFalse(poll(4).allowReanswer)
    }

    @Test
    fun `a broken or unknown element does not break the slide`() {
        val slide = Slide(JSONObject("""{"id":"39_0","type":"","elements":[
            {"type":"poll","poll_type":"nps","poll":"broken"},
            {"type":"poll","poll_type":"mini_poll","poll":{"code":"x","kind":"quiz"}},
            {"type":"poll","poll_type":"emoji","poll":{"code":"x"}},
            {"type":"poll","poll_type":"nps","poll":{"rate_min":"a"}},
            {"type":"poll","poll_type":"mini_poll","y_offset":"abc","allow_reanswer":"true",
             "poll":{"code":12,"kind":"single_choice","options":[{"id":"1","label":"A"},{"label":"no id"},{"id":2.5,"label":"B"}]},
             "answer":{"option_ids":["1",null,"x"]},"results":{"total":"3","options":[{"id":1,"percent":"66.6"}]}},
            {"type":"product","item":{"id":"p1","name":"Sneakers"}}]}"""))

        val (nps, poll) = slide.polls
        // No code is still a poll: the server answers with an error.
        assertEquals("", nps.code)
        assertEquals((0..10).toList(), nps.scale)
        assertEquals("39_0", slide.id)
        assertEquals("12", poll.code)
        assertNull(poll.yOffset)
        assertTrue(poll.allowReanswer)
        assertEquals(listOf(1), poll.options.map { it.id })
        assertEquals(listOf(1), poll.answer!!.optionIds)
        assertEquals(67, poll.results!!.percentOf(1))
        assertEquals(1, slide.getElements().size)
    }

    @Test
    fun `null fields of every kind fall back`() {
        val nulls = """"y_offset":null,"allow_reanswer":null,"labels":null,"answer":null,"results":null"""
        val pollNulls = """"code":null,"question":null,"placeholder":null,"max_length":null,"submit_label":null,
            "thanks_text":null,"show_results":null,"options":null,"channel":null,"rate_min":null,"rate_max":null,"bands":null"""
        val kinds = listOf("single_choice", "multiple_choice", "text").map { """"poll_type":"mini_poll","poll":{"kind":"$it",$pollNulls}""" } +
            listOf("nps", "csat", "ces").map { """"poll_type":"$it","poll":{$pollNulls}""" }
        val polls = kinds.map { requireNotNull(StoryPoll.parse(JSONObject("""{"type":"poll",$nulls,$it}"""))) { it } }

        assertEquals(StoryPoll.Type.values().toList(), polls.map { it.type })
        polls.forEach { poll ->
            assertEquals("", poll.code)
            assertNull(poll.yOffset)
            assertFalse(poll.allowReanswer)
            assertEquals(StoryPoll.Labels(), poll.labels)
            assertNull(poll.answer)
            assertNull(poll.results)
            assertNull(poll.question)
            assertNull(poll.placeholder)
            assertNull(poll.maxLength)
            assertNull(poll.submitLabel)
            assertNull(poll.thanksText)
            assertNull(poll.channel)
            assertTrue(poll.options.isEmpty())
            assertTrue(poll.bands.isEmpty())
        }
        assertEquals(listOf(11, 5, 7), polls.drop(3).map { it.scale.size })
        // The card goes to the empty code, like the web SDK.
        assertEquals(mapOf("channel" to "stories", "category" to "", "rate" to 9), StoryPollBodies.review(polls[3], 9))
        assertEquals("https://app.test/api/v3/polls/mini_polls//answers", StoriesPollsApi.miniPollUrl("app.test", polls[0].code))
    }

    @Test
    fun `a string of spaces is not empty`() {
        val scale = StoryPoll.parse(JSONObject("""{"type":"poll","poll_type":"csat","labels":{"reanswer":" ","error":"","submit":"  "},
            "poll":{"code":"c","channel":" ","question":" ","bands":[{"from":1,"to":5,"success":" "}]}}"""))!!
        assertEquals(StoryPoll.Labels(reanswer = " ", submit = "  "), scale.labels)
        assertEquals(" ", scale.question)
        assertEquals(" ", scale.bandText(3.0))
        assertEquals(mapOf("channel" to " ", "campaign" to "c", "rate" to 3), StoryPollBodies.review(scale, 3))
        val text = StoryPoll.parse(JSONObject("""{"type":"poll","poll_type":"mini_poll",
            "poll":{"code":"t","kind":"text","placeholder":" ","submit_label":" ","thanks_text":" "}}"""))!!
        assertEquals(listOf(" ", " ", " "), listOf(text.placeholder, text.submitLabel, text.thanksText))

        // Texts of the host: an empty one is no text, a blank one is.
        val defaults = PollTexts("Answer again", "Failed", "Send")
        assertEquals(PollTexts("Answer again", " ", "Go"), PollTexts("", " ", "Go").or(defaults))
        assertEquals(PollTexts(" ", "Failed", "  "), PollTexts("Answer again", "Failed", "Send").with(scale.labels))
    }

    @Test
    fun `any number of rate is an answer, a fractional one finds its band`() {
        fun answer(rate: String) = StoryPoll.answer(JSONObject("""{"rate":$rate}"""))
        assertEquals(6.5, answer("6.5").rate)
        assertEquals(3.0, answer("\"3\"").rate)
        assertTrue(answer("6.5").isAnswered)
        assertFalse(answer("\"x\"").isAnswered)
        val bands = listOf(StoryPoll.Band(0.0, 6.0, "A"), StoryPoll.Band(6.0, 7.0, "B"))
        assertEquals("B", StoryPoll.bandText(bands, 6.5))
        assertNull(StoryPoll.bandText(bands.take(1), 6.5))
    }

    @Test
    fun `allow_reanswer is true only for true, 1 and the string true`() {
        fun reanswer(value: String) =
            StoryPoll.parse(JSONObject("""{"type":"poll","poll_type":"nps","allow_reanswer":$value,"poll":{"code":"x"}}"""))!!.allowReanswer
        listOf("true", "1", "1.0", "\"true\"").forEach { assertTrue(it, reanswer(it)) }
        listOf("false", "0", "2", "\"1\"", "\"TRUE\"", "\" true\"", "\"yes\"", "{}", "[]").forEach { assertFalse(it, reanswer(it)) }
    }

    @Test
    fun `poll_type and kind are compared as they are`() {
        fun parse(type: String, kind: String = "single_choice") =
            StoryPoll.parse(JSONObject("""{"type":"poll","poll_type":"$type","poll":{"code":"x","kind":"$kind"}}"""))
        assertEquals(StoryPoll.Type.NPS, parse("nps")!!.type)
        assertEquals(StoryPoll.Type.TEXT, parse("mini_poll", "text")!!.type)
        listOf("NPS", " nps", "nps ", "Mini_poll").forEach { assertNull(it, parse(it)) }
        listOf("Single_choice", " text", "TEXT").forEach { assertNull(it, parse("mini_poll", it)) }
    }

    @Test
    fun `an option without a label stays, without an integer id it goes`() {
        val poll = StoryPoll.parse(JSONObject("""{"type":"poll","poll_type":"mini_poll","poll":{"code":"x","kind":"single_choice",
            "options":[{"id":1},{"id":"2","label":null},{"id":"3","label":"C"},{"id":"x","label":"D"},{"id":4.5,"label":"E"},{"label":"F"},"G"]}}"""))!!
        assertEquals(listOf(1 to "", 2 to "", 3 to "C"), poll.options.map { it.id to it.label })
    }

    @Test
    fun `labels take only non-empty strings`() {
        fun labels(value: String) =
            StoryPoll.parse(JSONObject("""{"type":"poll","poll_type":"nps","labels":$value,"poll":{"code":"x"}}"""))!!.labels
        assertEquals(StoryPoll.Labels(submit = "Go"), labels("""{"reanswer":5,"error":"","submit":"Go"}"""))
        assertEquals(StoryPoll.Labels(), labels("\"Ещё раз\""))
        assertEquals(StoryPoll.Labels(), labels("[]"))
    }

    @Test
    fun `rate_min and rate_max that are not integers fall back to the defaults`() {
        fun scale(type: String, min: String, max: String) =
            StoryPoll.parse(JSONObject("""{"type":"poll","poll_type":"$type","poll":{"code":"x","rate_min":$min,"rate_max":$max}}"""))!!.scale
        assertEquals((1..5).toList(), scale("csat", "1.5", "\"4.2\""))
        assertEquals((2..5).toList(), scale("csat", "\"2\"", "5.0"))
        assertEquals((5..10).toList(), scale("nps", "5", "\"x\""))
        assertTrue(scale("ces", "7", "1").isEmpty())
        // The 21 values do not overflow at the edge of Int.
        assertEquals(6, StoryPoll.scaleRange(StoryPoll.Type.NPS, Int.MAX_VALUE - 5, Int.MAX_VALUE).size)
    }

    @Test
    fun `percents are rounded and clamped, an option out of the results has 0`() {
        val results = StoryPoll.results(JSONObject(
            """{"total":"4","options":[{"id":1,"percent":-3},{"id":2,"percent":150},{"id":3,"percent":12.5},{"id":4},{"id":5,"percent":"33.4"}]}"""
        ))!!
        assertEquals(listOf(0, 100, 13, 0, 33, 0), (1..6).map(results::percentOf))
        // Without the options array there are no results to show.
        assertNull(StoryPoll.results(JSONObject("""{"total":3}""")))
        assertNull(StoryPoll.results(JSONObject("""{"total":3,"options":{"1":40}}""")))
        assertEquals(0, StoryPoll.results(JSONObject("""{"options":[]}"""))!!.percentOf(1))
    }

    @Test
    fun `text is cut in UTF-16 units and a surrogate pair stays whole`() {
        assertEquals("ab", StoryPoll.limitText("ab😀c", 3))
        assertEquals("ab😀", StoryPoll.limitText("ab😀c", 4))
        assertEquals("abc", StoryPoll.limitText("abc", 3))
        assertEquals("abc", StoryPoll.limitText("abc", null))
        assertEquals("", StoryPoll.limitText("😀", 1))
    }

    @Test
    fun `unknown kinds go to the debug log`() {
        assertNull(StoryPoll.parse(JSONObject("""{"type":"poll","poll_type":"emoji","poll":{"code":"x"}}""")))
        assertNull(StoryPoll.parse(JSONObject("""{"type":"poll","poll_type":"mini_poll","poll":{"code":"x","kind":"quiz"}}""")))
        val logs = ShadowLog.getLogs().filter { it.type == Log.DEBUG }.map { it.msg }
        assertTrue(logs.toString(), logs.any { it.contains("emoji is not supported") })
        assertTrue(logs.toString(), logs.any { it.contains("mini_poll quiz is not supported") })
    }

    @Test
    fun `answered means options, text or a rate`() {
        assertFalse(StoriesPollAnswer().isAnswered)
        assertFalse(StoriesPollAnswer(optionIds = emptyList(), text = "").isAnswered)
        assertTrue(StoriesPollAnswer(text = "a").isAnswered)
        assertTrue(StoriesPollAnswer(rate = 0.0).isAnswered)
    }

    @Test
    fun `scale ranges fall back to defaults and stop at 21 values`() {
        assertEquals(listOf(1, 2, 3, 4, 5), StoryPoll.scaleRange(StoryPoll.Type.CSAT, null, null))
        assertEquals(11, StoryPoll.scaleRange(StoryPoll.Type.NPS, null, null).size)
        assertEquals((1..7).toList(), StoryPoll.scaleRange(StoryPoll.Type.CES, null, null))
        assertEquals((0..20).toList(), StoryPoll.scaleRange(StoryPoll.Type.NPS, 0, 100))
        assertEquals((3..4).toList(), StoryPoll.scaleRange(StoryPoll.Type.CSAT, 3, 4))
        assertTrue(StoryPoll.scaleRange(StoryPoll.Type.CSAT, 5, 1).isEmpty())
        assertTrue(StoryPoll.scaleRange(StoryPoll.Type.TEXT, 1, 5).isEmpty())
    }

    @Test
    fun `nps cells are red, orange, yellow and green, other scales split in thirds`() {
        fun nps(value: Int) = StoryPoll.scaleColor(StoryPoll.Type.NPS, value, 0, 10)
        assertEquals(Color.parseColor("#FCA5A5"), nps(0))
        assertEquals(Color.parseColor("#FCA5A5"), nps(2))
        assertEquals(Color.parseColor("#FDBA74"), nps(3))
        assertEquals(Color.parseColor("#FDBA74"), nps(5))
        assertEquals(Color.parseColor("#FCD34D"), nps(6))
        assertEquals(Color.parseColor("#FDE68A"), nps(8))
        assertEquals(Color.parseColor("#86EFAC"), nps(9))
        fun csat(value: Int) = StoryPoll.scaleColor(StoryPoll.Type.CSAT, value, 1, 5)
        assertEquals(Color.parseColor("#FCA5A5"), csat(2))
        assertEquals(Color.parseColor("#FDE68A"), csat(3))
        assertEquals(Color.parseColor("#86EFAC"), csat(4))
        // NPS with its own range is split in thirds too.
        assertEquals(Color.parseColor("#86EFAC"), StoryPoll.scaleColor(StoryPoll.Type.NPS, 5, 1, 5))
        assertEquals(Color.parseColor("#86EFAC"), StoryPoll.scaleColor(StoryPoll.Type.CES, 1, 1, 1))
    }

    @Test
    fun `band text is the success of the band with the rate`() {
        val bands = listOf(StoryPoll.Band(0.0, 6.0, "A"), StoryPoll.Band(7.0, 8.0, ""), StoryPoll.Band(9.0, 10.0, "C"))
        assertEquals("A", StoryPoll.bandText(bands, 0.0))
        assertEquals("A", StoryPoll.bandText(bands, 6.0))
        assertNull(StoryPoll.bandText(bands, 7.0))
        assertEquals("C", StoryPoll.bandText(bands, 10.0))
        assertNull(StoryPoll.bandText(bands, 11.0))
        assertNull(StoryPoll.bandText(bands, null))
        assertEquals("Спасибо, мы рады!", poll(3).bandText(9.0))
    }

    @Test
    fun `texts are defaults, then the host, then labels of the element`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val defaults = StoriesTexts.defaults(activity).pollTexts
        assertEquals(PollTexts("Answer again", "Failed to send the answer. Please try again", "Send"), defaults)

        val host = defaults.copy(reanswer = "Again")
        assertEquals("Again", host.with(StoryPoll.Labels()).reanswer)
        val labelled = host.with(StoryPoll.Labels(reanswer = "Ещё раз", submit = "Отправить"))
        assertEquals(PollTexts("Ещё раз", "Failed to send the answer. Please try again", "Отправить"), labelled)
    }

    @Test
    @Config(qualifiers = "ru")
    fun `russian defaults come from resources`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertEquals(
            PollTexts("Ответить заново", "Не удалось отправить ответ. Попробуйте еще раз", "Отправить"),
            StoriesTexts.defaults(activity).pollTexts
        )
    }

    @Test
    fun `request bodies keep slide_id a string and pick category or campaign`() {
        assertEquals(
            mapOf("option_ids" to listOf(2), "story_id" to 5, "slide_id" to "101"),
            StoryPollBodies.answer(5, "101", optionIds = listOf(2))
        )
        assertEquals(mapOf("text" to "a", "story_id" to 39, "slide_id" to "39_0"), StoryPollBodies.answer(39, "39_0", text = "a"))
        assertEquals(mapOf("channel" to "test", "category" to "test", "rate" to 9), StoryPollBodies.review(poll(3), 9))
        assertEquals(mapOf("channel" to "stories", "campaign" to "demo_csat", "rate" to 1), StoryPollBodies.review(poll(4), 1))
        assertEquals(mapOf("channel" to "stories", "campaign" to "demo_ces", "rate" to 7), StoryPollBodies.review(poll(5), 7))
    }

    @Test
    fun `a slide with a poll survives java serialization, a slide without one too`() {
        slides.forEach { slide ->
            val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(slide) } }
            val copy = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() } as Slide
            assertEquals(slide.polls.map { it.type to it.code }, copy.polls.map { it.type to it.code })
            assertEquals(slide.polls.map { it.answer to it.results }, copy.polls.map { it.answer to it.results })
        }
        assertTrue(slides[6].polls.single().answer!!.isAnswered)
    }
}
