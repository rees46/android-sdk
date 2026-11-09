package com.personalization.popups

import com.personalization.Fixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Нормализация `design` на общих фикстурах: сетка, рамка, breakpoints, элементы и их умолчания. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PopupV3DesignTest {

    private val texts = PopupV3Texts(detail = "Подробнее", email = "Email", subscribe = "Подписаться", send = "Отправить", thanks = "Спасибо!")

    private fun fixture(name: String) = JSONObject(Fixtures.read("popups-v3/$name"))

    private fun expect(popup: JSONObject): JSONObject = popup.getJSONObject("_fixture").getJSONObject("expect")

    private fun design(popup: JSONObject, logs: MutableList<String> = mutableListOf()) =
        PopupV3Design.parse(popup.getJSONObject("design")) { logs += it }

    private fun elements(name: String): Map<String, PopupV3Element> = design(fixture(name)).elements.associateBy { it.id }

    private fun color(value: String) = CssColor.parse(value)!!

    @Test
    fun `grid takes fractions, parts, alignment and drops what has no cell`() {
        val popup = fixture("grid.json")
        val expect = expect(popup)
        val logs = mutableListOf<String>()
        val design = design(popup, logs)
        val rows = design.rows.associateBy { it.id }
        assertEquals(PopupV3Design.ROWS_GAP, expect.getDouble("rows_gap"), 0.0)

        val expectedRows = expect.getJSONObject("rows")
        expectedRows.keys().forEach { id ->
            val want = expectedRows.getJSONObject(id)
            val row = rows.getValue(id)
            val fractions = want.getJSONArray("fractions")
            assertEquals(fractions.length(), row.fractions.size)
            row.fractions.forEachIndexed { index, value -> assertEquals("$id fraction $index", fractions.getDouble(index), value, 0.001) }
            assertEquals("$id gap", want.getDouble("gap"), row.gap, 0.0)
            assertEquals("$id minHeight", want.getDouble("minHeight"), row.minHeight, 0.0)
            val cells = want.optJSONArray("cells") ?: return@forEach
            for (index in 0 until cells.length()) {
                val cell = cells.getJSONObject(index)
                assertEquals("$id cell $index align", cell.getString("align"), row.cells[index].align)
                assertEquals("$id cell $index vertical", cell.getString("verticalAlign"), row.cells[index].verticalAlign)
                val ids = cell.getJSONArray("elements").let { array -> (0 until array.length()).map(array::getString) }
                assertEquals("$id cell $index elements", ids, row.cells[index].elements.map { it.id })
            }
        }
        assertEquals(listOf("d5"), rows.getValue("row-d").cells[5].elements.map { it.id })

        val background = expectedRows.getJSONObject("row-c").getJSONObject("background")
        val rowC = rows.getValue("row-c")
        assertEquals(background.getString("color"), CssColor.format(rowC.background!!))
        assertEquals(background.getString("image"), rowC.backgroundImage)
        assertEquals(background.getString("size"), rowC.backgroundSize)
        assertEquals(background.getString("position"), rowC.backgroundPosition)
        assertEquals(background.getString("repeat"), rowC.backgroundRepeat)
        assertEquals("right", rowC.cells[1].elements.single().cellAlign)

        val dropped = expect.getJSONArray("dropped").let { array -> (0 until array.length()).map(array::getString) }
        dropped.forEach { id -> assertTrue("$id logged: $logs", logs.any { it.contains("element $id dropped") }) }
        assertFalse(design.elements.any { it.id in dropped })
    }

    @Test
    fun `frame of each layout`() {
        val popups = fixture("layouts.json").getJSONArray("popups")
        val parsed = (0 until popups.length()).associate { index ->
            val json = popups.getJSONObject(index)
            json.getInt("id") to (PopupV3.parse(json) as PopupV3.Parsed.Ok).popup
        }
        val top = parsed.getValue(9201)
        assertEquals(PopupV3.Layout.BANNER, top.layout)
        assertEquals(PopupV3.Placement.TOP, top.placement)
        top.design.frame.resolve(390.0).let { frame ->
            assertNull(frame.overlayEnabled)
            assertFalse(frame.lockBackgroundScroll)
            assertEquals(12.0, frame.offsetX, 0.0)
            assertEquals(16.0, frame.offsetY, 0.0)
            assertEquals(390.0, frame.frameWidth(390.0), 0.0)
        }
        val bottom = parsed.getValue(9202)
        assertEquals(PopupV3.Placement.BOTTOM, bottom.placement)
        bottom.design.frame.resolve(390.0).let { frame ->
            assertEquals(true, frame.overlayEnabled)
            assertTrue(frame.lockBackgroundScroll)
            assertEquals("#000000ff", CssColor.format(frame.overlayColor))
            assertEquals(0.3, frame.overlayOpacity, 0.0)
        }
        parsed.getValue(9203).design.frame.resolve(390.0).let { frame ->
            assertNull("overlay on by default for modal", frame.overlayEnabled)
            assertEquals(0.5, frame.overlayOpacity, 0.0)
            assertEquals(358.8, frame.frameWidth(390.0), 0.001)
            assertEquals(480.0, frame.frameWidth(800.0), 0.0)
            assertEquals(200.0, frame.frameMinHeight(800.0), 0.0)
            assertEquals(16.0, frame.radius, 0.0)
            assertEquals(20.0, frame.padding, 0.0)
            assertEquals(1.0, frame.borderWidth, 0.0)
            assertEquals("#e5e7ebff", CssColor.format(frame.borderColor))
        }
        val fullscreen = parsed.getValue(9204)
        assertEquals(PopupV3.Layout.FULLSCREEN, fullscreen.layout)
        assertEquals(false, fullscreen.design.frame.resolve(390.0).overlayEnabled)
    }

    @Test
    fun `breakpoints apply from the widest to the narrowest`() {
        val popup = fixture("breakpoints.json")
        val expect = expect(popup)
        val frame = design(popup).frame
        listOf(390, 414, 800).forEach { width ->
            val want = expect.getJSONObject("screen_$width")
            val resolved = frame.resolve(width.toDouble())
            assertEquals("width at $width", want.getDouble("width"), resolved.frameWidth(width.toDouble()), 0.001)
            assertEquals("padding at $width", want.getDouble("padding"), resolved.padding, 0.0)
            assertEquals("radius at $width", want.getDouble("borderRadius"), resolved.radius, 0.0)
            if (want.has("maxWidth")) {
                if (want.isNull("maxWidth")) assertNull(resolved.maxWidth) else assertEquals(want.getDouble("maxWidth"), resolved.maxWidth!!, 0.0)
            }
            if (want.has("minHeight")) assertEquals(want.getDouble("minHeight"), resolved.minHeight, 0.0)
        }
    }

    @Test
    fun `an empty design is not shown and keeps the frame defaults`() {
        val popup = fixture("empty-design.json")
        val design = design(popup)
        assertTrue(design.isEmpty)
        val frame = design.frame.resolve(390.0)
        assertEquals("#ffffffff", CssColor.format(frame.background))
        assertEquals(480.0, frame.maxWidth!!, 0.0)
        assertEquals("auto", frame.heightMode)
    }

    @Test
    fun `unknown elements are skipped and logged, a broken style is an empty one`() {
        val popup = fixture("unknown-element.json")
        val logs = mutableListOf<String>()
        val design = design(popup, logs)
        assertEquals(listOf("ok", "bad-style"), design.elements.map { it.id })
        listOf("wheel", "countdown", "no-type").forEach { id ->
            assertTrue("$id: $logs", logs.any { it.contains("element $id skipped") })
        }
        assertEquals(16.0, PopupV3Specs.text(design.elements[1]).fontSize, 0.0)
    }

    @Test
    fun `a design without rows flows by y, then by x`() {
        val design = design(fixture("legacy-free-elements.json"))
        assertEquals(listOf("first", "third", "second"), design.free!!.map { it.id })
    }

    @Test
    fun `text and box`() {
        val text = elements("element-text.json")
        val full = text.getValue("el-text-full")
        PopupV3Specs.text(full).let {
            assertEquals("**Скидка 10%** на всё\nдо конца недели", it.message)
            assertEquals("center", it.align)
            assertEquals(20.0, it.fontSize, 0.0)
            assertEquals(700.0, it.fontWeight, 0.0)
        }
        PopupV3Specs.box(full).let {
            assertEquals(1.4, it.lineHeight, 0.0)
            assertEquals(0.5, it.letterSpacing, 0.0)
            assertEquals(0.9, it.opacity, 0.0)
            assertEquals(8.0, it.paddingX, 0.0)
            assertEquals(0.0, it.paddingY, 0.0)
            assertEquals("#111827ff", CssColor.format(it.textColor))
            assertEquals("#00000000", CssColor.format(it.background))
            assertEquals("#1118271a", CssColor.format(it.borderColor))
            assertEquals(2.0, it.shadowY, 0.0)
            assertEquals(8.0, it.shadowBlur, 0.0)
            assertEquals("#00000055", CssColor.format(it.shadowColor))
        }
        assertEquals("https://demo.rees46.ru/sale", PopupV3Specs.elementLink(full))
        assertEquals("center", full.cellAlign)

        val bare = text.getValue("el-text-bare")
        PopupV3Specs.text(bare).let {
            assertEquals(16.0, it.fontSize, 0.0)
            assertEquals("left", it.align)
            assertEquals(500.0, it.fontWeight, 0.0)
        }
        PopupV3Specs.box(bare).let {
            assertEquals("#00000000", CssColor.format(it.background))
            assertEquals("#111827ff", CssColor.format(it.textColor))
            assertEquals(1.35, it.lineHeight, 0.0)
            assertEquals(0.0, it.borderWidth, 0.0)
        }
        assertEquals("CSS-позиционирование игнорируется", PopupV3Specs.text(text.getValue("el-text-css-position")).message)

        val box = elements("element-box.json")
        PopupV3Specs.box(box.getValue("el-box-defaults")).let {
            assertEquals("#f8fafcff", CssColor.format(it.background))
            assertEquals(12.0, it.radius, 0.0)
            assertEquals(16.0, it.paddingX, 0.0)
            assertEquals(16.0, it.paddingY, 0.0)
        }
        assertEquals(12.0, PopupV3Specs.text(box.getValue("el-box-defaults")).fontSize, 0.0)
        val empty = box.getValue("el-box-empty")
        assertNull(PopupV3Specs.text(empty).message)
        PopupV3Specs.box(empty).let {
            assertEquals("#eef2ffff", CssColor.format(it.background))
            assertEquals(20.0, it.radius, 0.0)
            assertEquals(24.0, it.paddingX, 0.0)
        }
    }

    @Test
    fun `buttons, their actions and the platform link`() {
        val buttons = elements("element-button.json").mapValues { PopupV3Specs.button(it.value, texts) }
        buttons.getValue("el-btn-link").let {
            assertEquals("link", it.action)
            assertEquals("left", it.align)
            assertEquals("rees46demo://sale?from=android", it.href)
            assertEquals("#16a34aff", CssColor.format(it.background))
            assertEquals("#ffffffff", CssColor.format(it.textColor))
            assertEquals(16.0, it.fontSize, 0.0)
            assertEquals(12.0, it.radius, 0.0)
            assertEquals(24.0, it.paddingX, 0.0)
            assertEquals(12.0, it.paddingY, 0.0)
        }
        assertEquals("rees46demo://catalog?from=ios_var", buttons.getValue("el-btn-ios-var").href)
        assertEquals("close", buttons.getValue("el-btn-close").action)
        buttons.getValue("el-btn-copy").let {
            assertEquals("copy", it.action)
            assertEquals("SALE10", it.copyText)
        }
        assertEquals("SALE20", buttons.getValue("el-btn-copy-fallback").copyText)
        buttons.getValue("el-btn-legacy-href").let {
            assertEquals("link", it.action)
            assertEquals("https://demo.rees46.ru", it.href)
        }
        buttons.getValue("el-btn-defaults").let {
            assertEquals("link", it.action)
            assertEquals("Подробнее", it.label)
            assertEquals("center", it.align)
            assertEquals("#2563ebff", CssColor.format(it.background))
            assertEquals(14.0, it.fontSize, 0.0)
            assertEquals(20.0, it.paddingX, 0.0)
            assertEquals(10.0, it.paddingY, 0.0)
            assertEquals(10.0, it.radius, 0.0)
            assertNull(it.href)
        }
        buttons.getValue("el-btn-unknown-action").let {
            assertEquals("link", it.action)
            assertEquals("https://demo.rees46.ru", it.href)
        }
    }

    @Test
    fun `close in text, image and svg modes, icon size 8 to 128`() {
        val closes = elements("element-close.json")
        val default = closes.getValue("el-close-default")
        PopupV3Specs.button(default, texts).let {
            assertEquals("close", it.action)
            assertEquals("×", it.label)
            assertEquals("right", it.align)
            assertEquals("#00000000", CssColor.format(it.background))
            assertEquals("#71717aff", CssColor.format(it.textColor))
            assertEquals(22.0, it.fontSize, 0.0)
            assertEquals(8.0, it.paddingX, 0.0)
            assertEquals(2.0, it.paddingY, 0.0)
            assertEquals(999.0, it.radius, 0.0)
        }
        assertEquals("text", PopupV3Specs.close(default, texts).mode)
        PopupV3Specs.button(closes.getValue("el-close-text"), texts).let {
            assertEquals("Закрыть ×", it.label)
            assertEquals("left", it.align)
            assertEquals(16.0, it.fontSize, 0.0)
        }
        PopupV3Specs.close(closes.getValue("el-close-image"), texts).let {
            assertEquals("image", it.mode)
            assertEquals(128.0, it.size, 0.0)
            assertEquals("Закрыть", it.alt)
        }
        PopupV3Specs.close(closes.getValue("el-close-svg"), texts).let {
            assertEquals("svg", it.mode)
            assertEquals(8.0, it.size, 0.0)
            assertFalse(it.svg!!.contains("script"))
            assertFalse(it.svg!!.contains("onload"))
        }
    }

    @Test
    fun `image heights, fit, ratio and hiding`() {
        val images = elements("element-image.json").mapValues { PopupV3Specs.image(it.value, texts) }
        images.getValue("el-img-auto").let {
            assertEquals("https://pictures.rees46.ru/resize-images/180/c1140c8254976de297c3caf971701a/141206.jpg", it.url)
            assertEquals("auto", it.heightMode)
            assertEquals("cover", it.fit)
            assertEquals(12.0, it.radius, 0.0)
            assertEquals(8.0, it.marginBottom, 0.0)
        }
        assertEquals("https://demo.rees46.ru/product/141206", PopupV3Specs.elementLink(elements("element-image.json").getValue("el-img-auto")))
        images.getValue("el-img-px").let {
            assertEquals("px", it.heightMode)
            assertEquals(180.0, it.height, 0.0)
            assertEquals("contain", it.fit)
        }
        images.getValue("el-img-percent").let {
            assertEquals("%", it.heightMode)
            assertEquals(100.0, it.height, 0.0)
        }
        images.getValue("el-img-vh").let {
            assertEquals("vh", it.heightMode)
            assertEquals(40.0, it.height, 0.0)
        }
        assertEquals(1.7778, images.getValue("el-img-ratio").aspectRatio!!, 0.0001)
        images.getValue("el-img-bad-ratio").let {
            assertNull(it.aspectRatio)
            assertEquals("cover", it.fit)
        }
        assertFalse(images.getValue("el-img-hidden").show)
        assertTrue(elements("element-image.json").getValue("el-img-auto").stretch)
    }

    @Test
    fun `email collector layouts, consent fields and success`() {
        val emails = elements("element-email-collector.json").mapValues { PopupV3Specs.email(it.value, texts) }
        emails.getValue("el-email-inline").let {
            assertTrue(it.inline)
            assertEquals(listOf("email_bulk", "email_chain"), it.consentFields)
            assertEquals(
                listOf(PopupMarkup.Span("Согласен получать "), PopupMarkup.Span("рассылку", italic = true)),
                PopupMarkup.parse(it.consentLabel)
            )
            assertEquals("123", it.segmentId)
            assertEquals("Проверьте email", it.errorMessage)
            assertEquals("Готово!", it.successTitle)
            assertEquals("Проверьте почту", it.successMessage)
            assertEquals("#ecfdf5ff", CssColor.format(it.success.background))
            assertEquals("left", it.success.align)
        }
        emails.getValue("el-email-stacked-legacy").let {
            assertFalse(it.inline)
            assertEquals(listOf("email_transactional"), it.consentFields)
            assertEquals("Согласен на письма о заказах", it.consentLabel)
        }
        emails.getValue("el-email-bare").let {
            assertFalse(it.inline)
            assertEquals(PopupV3Specs.CONSENT_FIELDS, it.consentFields)
            assertEquals("Email", it.placeholder)
            assertEquals("Подписаться", it.submitText)
            assertNull(it.segmentId)
        }
    }

    @Test
    fun `form fields are normalized like the compiler`() {
        val popup = fixture("element-form.json")
        val expect = expect(popup)
        val forms = design(popup).elements.associateBy { it.id }.mapValues { PopupV3Specs.form(it.value, texts) }
        val form = forms.getValue("el-form")
        val fields = expect.getJSONObject("el-form").getJSONArray("fields")
        assertEquals(fields.length(), form.fields.size)
        for (index in 0 until fields.length()) {
            val want = fields.getJSONObject(index)
            val field = form.fields[index]
            assertEquals(want.getString("id"), field.id)
            assertEquals(want.getString("kind"), field.kind)
            assertEquals(want.getBoolean("required"), field.required)
            assertEquals(want.getString("propertyKey"), field.propertyKey)
        }
        assertEquals("Любимый **цвет**", form.fields[6].label)
        assertEquals("fav_color", form.fields[6].profileKey)
        assertEquals("birthday", form.fields[4].profileKey)
        assertEquals(listOf("email_bulk"), form.consentFields)
        assertEquals("456", form.segmentId)
        assertEquals("Сохранить", form.submitText)

        val bare = forms.getValue("el-form-bare")
        assertTrue(bare.fields.isEmpty())
        assertTrue(bare.consentFields.isEmpty())
        assertEquals("Отправить", bare.submitText)
    }

    @Test
    fun `promo code, personal and static`() {
        val popup = fixture("element-promo-code.json")
        val promos = design(popup).elements.associateBy { it.id }
        PopupV3Specs.promo(promos.getValue("el-promo-personal"), texts).let {
            assertFalse(it.static)
            assertEquals("{{promo_code}}", it.code)
            assertEquals("left", it.align)
            assertEquals(22.0, it.codeFontSize, 0.0)
            assertEquals("Скопировано!", it.copiedText)
            assertEquals("rees46demo://cart?promo=ABC", PopupV3Variables.applyRuntime(it.applyHref!!, mapOf("promo_code" to "ABC")))
        }
        PopupV3Specs.promo(promos.getValue("el-promo-static"), texts).let {
            assertTrue(it.static)
            assertEquals("WELCOME5", it.code)
            assertFalse(it.showCopy)
            assertEquals("center", it.align)
        }
        val parsed = (PopupV3.parse(popup) as PopupV3.Parsed.Ok).popup
        assertEquals("7", parsed.promoCodeListId)
        assertTrue(parsed.needsPromoCode)
    }

    @Test
    fun `survey scales and review bodies`() {
        val surveys = elements("element-survey.json").mapValues { PopupV3Specs.survey(it.value, texts) }
        surveys.getValue("el-survey-nps").let {
            assertEquals(0..10, it.scale)
            assertEquals(
                """{"channel":"popup","category":"test","rate":0,"comment":"Долгая доставка"}""",
                JSONObject(it.reviewBody(0, " Долгая доставка ") as Map<*, *>).toString()
            )
        }
        surveys.getValue("el-survey-csat").let {
            assertEquals(1..5, it.scale)
            assertEquals("""{"channel":"popup","campaign":"MNTEST","rate":5}""", JSONObject(it.reviewBody(5, "  ") as Map<*, *>).toString())
            assertEquals("Отправить", it.submitText)
            assertEquals("Спасибо!", it.thanks)
            assertEquals("#f3f4f6ff", CssColor.format(it.scaleBackground))
            assertEquals("#2563ebff", CssColor.format(it.activeBackground))
        }
        surveys.getValue("el-survey-ces").let {
            assertEquals(1..7, it.scale)
            assertEquals("MN TEST", it.reviewBody(3, null)["campaign"])
        }
        surveys.getValue("el-survey-unknown").let {
            assertEquals("nps", it.type)
            assertEquals(0..10, it.scale)
        }
    }

    @Test
    fun `recommendation needs a block code`() {
        val recommendations = elements("element-recommendation.json")
        val block = recommendations.getValue("el-rec")
        assertEquals("e6249bb15043644bf25b135006149962", PopupV3Specs.recommenderCode(block))
        PopupV3Specs.box(block).let {
            assertEquals(0, it.background)
            assertEquals(0.0, it.paddingX, 0.0)
            assertEquals(0.0, it.borderWidth, 0.0)
        }
        assertNull(PopupV3Specs.recommenderCode(recommendations.getValue("el-rec-empty")))
    }

    @Test
    fun `live popups 181 and 182 of the demo shop`() {
        val list = PopupsV3List.parse(Fixtures.read("popups-v3/live-list-181-182.json"))
        assertEquals(listOf(181, 182), list.popups.map { it.id })
        val banner = list.popups[0]
        assertEquals(PopupV3.Layout.BANNER, banner.layout)
        assertEquals(PopupV3.Placement.TOP, banner.placement)
        assertEquals(PopupV3.Trigger.APP_START, banner.trigger)
        assertEquals(
            listOf("close", "text", "button", "image", "emailCollector", "survey"),
            banner.design.elements.map { it.type }
        )
        val modal = list.popups[1]
        assertEquals(PopupV3.Layout.MODAL, modal.layout)
        assertFalse(modal.needsPromoCode)
        assertEquals(listOf("promoCode", "survey", "recommendation"), modal.design.elements.map { it.type })
        assertEquals("Fix Test", PopupV3Specs.promo(modal.design.elements[0], texts).code)
    }
}
