package com.personalization.popups

import com.personalization.Fixtures
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Общие фикстуры `parsing.json`, `markup.json` и `variables.json`: цвета, числа, булевы, разметка, переменные. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PopupV3ParsingTest {

    private fun fixture(name: String) = JSONObject(Fixtures.read("popups-v3/$name"))

    private fun JSONObject.items(key: String): List<JSONObject> = getJSONArray(key).let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }
    }

    @Test
    fun `colors come out as rrggbbaa or nothing`() {
        fixture("parsing.json").items("colors").forEach { vector ->
            val input = vector.opt("in").takeIf { it != JSONObject.NULL }
            val expected = vector.opt("out").takeIf { it != JSONObject.NULL } as String?
            assertEquals("color $input", expected, CssColor.parse(input)?.let(CssColor::format))
        }
    }

    @Test
    fun `all css named colors are known`() {
        assertEquals(148, CssColor.namedCount)
    }

    @Test
    fun `numbers take strings, booleans and fall back like the compiler`() {
        fixture("parsing.json").items("numbers").forEach { vector ->
            val input = vector.opt("in").takeIf { it != JSONObject.NULL }
            assertEquals("number $input", vector.getDouble("out"), PopupV3Values.num(input, vector.getDouble("fallback")), 0.0001)
        }
    }

    @Test
    fun `booleans take strings and numbers`() {
        fixture("parsing.json").items("booleans").forEach { vector ->
            val input = vector.opt("in").takeIf { it != JSONObject.NULL }
            val expected = vector.opt("out").takeIf { it != JSONObject.NULL } as Boolean?
            assertEquals("boolean $input", expected, PopupV3Values.bool(input))
        }
    }

    @Test
    fun `markup is scanned left to right with the compiler regex, without nesting`() {
        val markup = fixture("markup.json")
        assertEquals(markup.getJSONObject("_fixture").getString("regex"), MARKUP_REGEX)
        markup.getJSONObject("_fixture").items("expect").forEach { case ->
            val input = case.getString("input")
            val expected = case.items("spans").map { span ->
                PopupMarkup.Span(
                    text = span.getString("text"),
                    bold = span.optBoolean("bold"),
                    italic = span.optBoolean("italic"),
                    underline = span.optBoolean("underline"),
                    strike = span.optBoolean("strike"),
                    overline = span.optBoolean("overline")
                )
            }
            assertEquals("markup of «$input»", expected, PopupMarkup.parse(input))
        }
    }

    @Test
    fun `static variables come from an object or the old array, showcase ones stay tokens`() {
        val popup = fixture("variables.json")
        val expect = popup.getJSONObject("_fixture").getJSONObject("expect")
        val design = PopupV3Design.parse(popup.getJSONObject("design"))

        val variables = expect.getJSONObject("static_variables")
        assertEquals(variables.keys().asSequence().associateWith { variables.getString(it) }, design.variables)

        val elements = design.elements.associateBy { it.id }
        val text = PopupV3Specs.text(elements.getValue("t")).message!!
        assertEquals(expect.getString("t_after_static"), text)
        assertEquals(expect.getString("t_at_show_with_code_ABC123"), PopupV3Variables.applyRuntime(text, mapOf("promo_code" to "ABC123")))

        assertEquals(expect.getString("b_href"), PopupV3Specs.button(elements.getValue("b"), PopupV3Texts()).href)
        assertEquals(expect.getString("i_url"), PopupV3Specs.image(elements.getValue("i"), PopupV3Texts()).url)
        assertEquals(expect.getString("e_placeholder"), PopupV3Specs.email(elements.getValue("e"), PopupV3Texts()).placeholder)
    }

    @Test
    fun `showcase values are not markup`() {
        val spans = PopupMarkup.parse("Код **{{promo_code}}** и {{promo_code}}")
            .map { it.copy(text = PopupV3Variables.applyRuntime(it.text, mapOf("promo_code" to "*A*"))) }
        assertEquals(listOf(PopupMarkup.Span("Код "), PopupMarkup.Span("*A*", bold = true), PopupMarkup.Span(" и *A*")), spans)
    }

    @Test
    fun `links drop javascript and data, keep app deep links`() {
        assertEquals("rees46demo://sale", PopupV3Links.safe(" rees46demo://sale "))
        assertEquals("https://demo.rees46.ru/", PopupV3Links.safe("https://demo.rees46.ru/"))
        assertNull(PopupV3Links.safe("javascript:alert(1)"))
        assertNull(PopupV3Links.safe("DATA:text/html,x"))
        assertNull(PopupV3Links.safe(""))
        assertNull(PopupV3Links.safe("{{unknown}}"))
        assertNull(PopupV3Links.image("ftp://x/y.png"))
        assertTrue(PopupV3Links.image("https://x/y.png") != null)
    }

    @Test
    fun `variables keep unknown tokens as they are`() {
        val variables = PopupV3Variables.parse(JSONArray("""[{"key":"a","value":1},{"key":"","value":"no"},"junk"]"""))
        assertEquals(mapOf("a" to "1"), variables)
        assertEquals("1 {{ b }}", PopupV3Variables.applyStatic("{{a}} {{ b }}", variables))
    }

    private companion object {
        const val MARKUP_REGEX = "\\*\\*\\*([^*]+)\\*\\*\\*|\\*\\*([^*]+)\\*\\*|~~([^~]+)~~|__([^_]+)__|\\^\\^([^^]+)\\^\\^|\\*([^*]+)\\*"
    }
}
