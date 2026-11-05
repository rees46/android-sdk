package com.personalization.ui.popups

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.personalization.Fixtures
import com.personalization.R
import com.personalization.popups.PopupV3
import com.personalization.popups.PopupV3Design
import com.personalization.popups.PopupV3Specs
import com.personalization.popups.PopupV3Texts
import com.personalization.popups.PopupsV3List
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationCheckbox
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.widgets.PersonalizationRecommenderBlock
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Рендер документа: дерево вью из фикстур, выравнивание, видимость, нажатия и рамка. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h800dp-mdpi")
class PopupV3RenderTest {

    private lateinit var activity: Activity
    private val actions = RecordingActions()
    private val state = PopupV3State()

    private class RecordingActions : PopupV3Actions {
        val calls = mutableListOf<String>()
        var reviewResult: String? = null
        override fun close() { calls += "close" }
        override fun click() { calls += "click" }
        override fun openLink(url: String?) { calls += "link $url" }
        override fun copy(text: String) { calls += "copy $text" }
        override fun subscribe(spec: PopupV3Specs.Email, email: String, callback: (String?) -> Unit) {
            calls += "subscribe $email"
            callback(null)
        }
        override fun saveProfile(spec: PopupV3Specs.Form, values: Map<String, String>, consent: Boolean, callback: (String?) -> Unit) {
            calls += "profile $values $consent"
            callback(null)
        }
        override fun review(spec: PopupV3Specs.Survey, rate: Int, comment: String?, callback: (String?) -> Unit) {
            calls += "review ${spec.type} $rate $comment"
            callback(reviewResult)
        }
        override fun product(product: PersonalizationProduct) { calls += "product ${product.id}" }
    }

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    private fun ctx(variables: Map<String, String> = emptyMap()) = PopupV3RenderContext(
        context = activity,
        texts = PopupV3Texts(detail = "Подробнее", send = "Отправить"),
        variables = variables,
        state = state,
        actions = actions,
        sdk = null,
        screenWidthPx = 390,
        screenHeightPx = 800
    )

    private fun popup(name: String): PopupV3 = (PopupV3.parse(JSONObject(Fixtures.read("popups-v3/$name"))) as PopupV3.Parsed.Ok).popup

    private fun render(design: PopupV3Design, variables: Map<String, String> = emptyMap()): ViewGroup {
        val view = PopupV3Renderer.content(design, ctx(variables)) as ViewGroup
        val root = android.widget.FrameLayout(activity)
        root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(358, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, 358, root.measuredHeight)
        return view
    }

    private fun ViewGroup.byTag(tag: String): View? = findViewWithTag(tag)

    private fun View.texts(): List<String> = when (this) {
        is TextView -> listOf(text.toString())
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).texts() }
        else -> emptyList()
    }

    private fun <T : View> View.find(type: Class<T>): List<T> = when {
        type.isInstance(this) -> listOf(type.cast(this)!!) + ((this as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { group.getChildAt(it).find(type) } } ?: emptyList())
        this is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).find(type) }
        else -> emptyList()
    }

    @Test
    fun `live banner 181 renders all six elements in one cell`() {
        val banner = PopupsV3List.parse(Fixtures.read("popups-v3/live-list-181-182.json")).popups[0]
        val view = render(banner.design)
        listOf("el-mw0k0egu-h", "el-mw0jzwms-d", "el-mw0k0bwb-g", "el-mw0k0ny2-i", "el-mw0k1sro-k", "el-mw0k3jw7-o").forEach { id ->
            assertNotNull(id, view.byTag(id))
        }
        val texts = view.texts()
        assertTrue(texts.toString(), "×" in texts)
        assertTrue("Test !@#%\$%^&*" in texts)
        assertTrue("Подробнее" in texts)
        assertTrue("Насколько вероятно, что вы порекомендуете нас друзьям?" in texts)
        // Крестик справа во всю ширину ячейки, кнопка — по центру.
        val close = view.byTag("el-mw0k0egu-h")!!
        assertEquals(358, close.width)
        val closeButton = close.find(TextView::class.java).single()
        assertEquals(358 - closeButton.width, closeButton.left + (close as ViewGroup).getChildAt(0).left)
        val email = view.byTag("el-mw0k1sro-k")!!
        assertEquals(358, email.width)
        assertTrue(email.find(EditText::class.java).isNotEmpty())
    }

    @Test
    fun `live modal 182 renders the promo code, survey and recommendations`() {
        val modal = PopupsV3List.parse(Fixtures.read("popups-v3/live-list-181-182.json")).popups[1]
        val view = render(modal.design)
        val texts = view.texts()
        assertTrue(texts.toString(), "Fix Test" in texts)
        assertTrue("Скопировать" in texts)
        assertEquals((1..5).map(Int::toString), texts.filter { it.length == 1 && it[0].isDigit() })
        val block = view.find(PersonalizationRecommenderBlock::class.java).single()
        assertEquals("e4bceaa2a37cdc9f1d995b8862b4fe2a", block.code)
        assertTrue((block.parent as View).minimumHeight >= 40)
    }

    @Test
    fun `a hidden image, an empty recommendation and unknown types are not drawn`() {
        val images = render(popup("element-image.json").design)
        assertNull(images.byTag("el-img-hidden"))
        assertNotNull(images.byTag("el-img-px"))
        assertEquals(180, images.byTag("el-img-px")!!.height)
        assertEquals(800 * 40 / 100, images.byTag("el-img-vh")!!.height)

        val recommendations = render(popup("element-recommendation.json").design)
        assertNotNull(recommendations.byTag("el-rec"))
        assertNull(recommendations.byTag("el-rec-empty"))

        val unknown = render(popup("element-text.json").design.let { it })
        assertNotNull(unknown.byTag("el-text-css-position"))
    }

    @Test
    fun `cells place elements by fractions and alignment`() {
        val view = render(popup("grid.json").design)
        val a1 = view.byTag("a1")!!
        val rowA = a1.parent.parent as ViewGroup
        assertEquals(2, rowA.childCount)
        val cell0 = rowA.getChildAt(0)
        val cell1 = rowA.getChildAt(1)
        assertEquals((358 - 12) / 2, cell0.width)
        assertEquals(cell0.width + 12, cell1.left)
        assertTrue("row A at least 80 high", rowA.height >= 80)
        val c1 = view.byTag("c1")!!
        assertEquals(c1.parent.let { (it as View).width } - c1.width, c1.left)
        assertNull(view.byTag("orphan"))
        assertNull(view.byTag("out-of-range"))
    }

    @Test
    fun `button actions`() {
        val view = render(popup("element-button.json").design)
        view.byTag("el-btn-link")!!.find(TextView::class.java).single().performClick()
        view.byTag("el-btn-close")!!.find(TextView::class.java).single().performClick()
        view.byTag("el-btn-copy")!!.find(TextView::class.java).single().performClick()
        assertEquals(listOf("click", "link rees46demo://sale?from=android", "close", "click", "copy SALE10"), actions.calls)
    }

    @Test
    fun `email collector checks the consent and the address, then subscribes and shows success`() {
        val view = render(popup("element-email-collector.json").design)
        val inline = view.byTag("el-email-inline")!!
        val row = inline.find(EditText::class.java).single().parent as LinearLayout
        assertEquals(LinearLayout.HORIZONTAL, row.orientation)
        val stacked = view.byTag("el-email-stacked-legacy")!!
        assertEquals(LinearLayout.VERTICAL, (stacked.find(EditText::class.java).single().parent as LinearLayout).orientation)

        val field = inline.find(EditText::class.java).single()
        val submit = inline.find(TextView::class.java).first { it.text.toString() == "Подписаться" }
        field.setText("demo+android@rees46.test")
        submit.performClick()
        assertTrue(inline.texts().contains("Проверьте email"))
        assertTrue(actions.calls.isEmpty())

        inline.find(PersonalizationCheckbox::class.java).single().performClick()
        submit.performClick()
        assertEquals(listOf("subscribe demo+android@rees46.test"), actions.calls)
        val success = inline.find(TextView::class.java).first { it.text.toString() == "Готово!" }
        assertEquals(View.VISIBLE, (success.parent as View).visibility)
    }

    private fun assertConsentColors(element: View, checked: Int, checkmark: Int, unchecked: Int, border: Int) {
        val checkbox = element.find(PersonalizationCheckbox::class.java).single()
        assertEquals("checked", checked, checkbox.checkedColor)
        assertEquals("checkmark", checkmark, checkbox.checkmarkColor)
        assertEquals("unchecked", unchecked, checkbox.uncheckedColor)
        assertEquals("border", border, checkbox.uncheckedBorderColor)
        // Размер — как у чекбокса кита: 20dp.
        assertEquals(20, checkbox.width)
    }

    @Test
    fun `the consent checkbox takes the colors of its form, the defaults included`() {
        val emails = render(popup("element-email-collector.json").design)
        // Поле и кнопка из дизайна; фон поля #ffffff — не значение по умолчанию.
        assertConsentColors(emails.byTag("el-email-inline")!!, 0xFF2563EB.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBD5E1.toInt())
        // Стиль пуст — значения поля и кнопки по умолчанию, а не токены кита.
        assertConsentColors(emails.byTag("el-email-bare")!!, 0xFF2563EB.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8FAFC.toInt(), 0xFFCBD5E1.toInt())

        val forms = render(popup("element-form.json").design)
        assertConsentColors(forms.byTag("el-form")!!, 0xFF2563EB.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8FAFC.toInt(), 0xFFCBD5E1.toInt())
    }

    @Test
    @Config(qualifiers = "night")
    fun `the consent checkbox takes the colors set in the design, in the dark theme too`() {
        fun designed(name: String, id: String, style: Map<String, String>): PopupV3Design {
            val json = JSONObject(Fixtures.read("popups-v3/$name"))
            val elements = json.getJSONObject("design").getJSONArray("elements")
            val element = (0 until elements.length()).map { elements.getJSONObject(it) }.single { it.getString("id") == id }
            style.forEach { (key, value) -> element.getJSONObject("style").put(key, value) }
            return (PopupV3.parse(json) as PopupV3.Parsed.Ok).popup.design
        }

        val email = render(designed("element-email-collector.json", "el-email-stacked-legacy", mapOf(
            "buttonBgColor" to "#16a34a",
            "buttonTextColor" to "#111827",
            "emailInputBg" to "#fef3c7",
            "emailInputBorderColor" to "#d97706"
        )))
        assertConsentColors(email.byTag("el-email-stacked-legacy")!!, 0xFF16A34A.toInt(), 0xFF111827.toInt(), 0xFFFEF3C7.toInt(), 0xFFD97706.toInt())

        val form = render(designed("element-form.json", "el-form", mapOf(
            "buttonBgColor" to "#7c3aed",
            "buttonTextColor" to "#fde68a",
            "formInputBg" to "#ecfeff",
            "formInputBorderColor" to "#0891b2"
        )))
        assertConsentColors(form.byTag("el-form")!!, 0xFF7C3AED.toInt(), 0xFFFDE68A.toInt(), 0xFFECFEFF.toInt(), 0xFF0891B2.toInt())
    }

    private fun night(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun kitText(view: View): Int = PersonalizationTheme.color(view.context, R.color.personalization_text_primary)

    /** Модалка 182 в своём корне — с фоном рамки из дизайна или [frame]. */
    private fun modal182(frame: String? = null): PopupV3RootView {
        val json = JSONObject(Fixtures.read("popups-v3/live-list-181-182.json"))
        val design = json.getJSONObject("payload").getJSONArray("popups").getJSONObject(1).getJSONObject("design")
        frame?.let { design.getJSONObject("frameStyle").put("bgColor", it) }
        val modal = PopupsV3List.parse(json.toString()).popups[1]
        return PopupV3RootView(activity, modal, modal.design, emptyMap(), state, actions, null, blocking = true) {}
    }

    @Test
    @Config(qualifiers = "+night")
    fun `kit components in a white popup take the light theme while the system is dark`() {
        assertTrue(night(activity))
        val block = modal182().find(PersonalizationRecommenderBlock::class.java).single()
        assertFalse(night(block.context))
        assertEquals(0xFF000000.toInt(), kitText(block))
        // Без попапа кит по-прежнему следует системе.
        assertEquals(0xFFFFFFFF.toInt(), kitText(PersonalizationRecommenderBlock(activity)))
    }

    @Test
    fun `kit components on a dark background take the dark theme while the system is light`() {
        assertFalse(night(activity))
        val block = modal182(frame = "#111827").find(PersonalizationRecommenderBlock::class.java).single()
        assertTrue(night(block.context))
        assertEquals(0xFFFFFFFF.toInt(), kitText(block))
        // Белый попап в светлой системе — контекст активити как есть.
        assertSame(activity, modal182().find(PersonalizationRecommenderBlock::class.java).single().context)
        assertEquals(0xFF000000.toInt(), kitText(PersonalizationRecommenderBlock(activity)))
    }

    @Test
    fun `the background under an element is its own, else the frame's, else white`() {
        val dark = 0xFF111827.toInt()
        assertEquals(dark, PopupV3KitTheme.background(dark, Color.WHITE))
        assertEquals(dark, PopupV3KitTheme.background(Color.TRANSPARENT, dark))
        assertEquals(Color.WHITE, PopupV3KitTheme.background(Color.TRANSPARENT, Color.TRANSPARENT))
        // Полупрозрачный ложится на то, что под ним.
        assertEquals(0xFF7F7F7F.toInt(), PopupV3KitTheme.background(0x80000000.toInt(), Color.WHITE))
        assertTrue(PopupV3KitTheme.night(activity, dark))
        assertFalse(PopupV3KitTheme.night(activity, Color.WHITE))
        assertFalse(PopupV3KitTheme.night(activity, 0xFFFEF3C7.toInt()))

        // Свой тёмный фон элемента — тёмная тема кита на белой рамке.
        val json = JSONObject(Fixtures.read("popups-v3/element-text.json"))
        val elements = json.getJSONObject("design").getJSONArray("elements")
        (0 until elements.length()).map { elements.getJSONObject(it) }.single { it.getString("id") == "el-text-full" }
            .getJSONObject("style").put("bgColor", "#111827")
        val element = (PopupV3.parse(json) as PopupV3.Parsed.Ok).popup.design.elements.single { it.id == "el-text-full" }
        assertTrue(night(ctx().kit(element)))
        assertSame(activity, ctx().kit(popup("element-text.json").design.elements.single { it.id == "el-text-full" }))
    }

    @Test
    fun `form validates required fields and sends filled values only`() {
        val view = render(popup("element-form.json").design)
        val form = view.byTag("el-form")!!
        val inputs = form.find(EditText::class.java)
        assertEquals(8, inputs.size)
        val submit = form.find(TextView::class.java).first { it.text.toString() == "Сохранить" }
        submit.performClick()
        assertTrue(form.texts().contains("Заполните обязательные поля"))
        inputs[0].setText("anna@example.com")
        inputs[6].setText("зелёный")
        submit.performClick()
        assertEquals(listOf("profile {email=anna@example.com, fav_color=зелёный} false"), actions.calls)
    }

    @Test
    fun `survey shows the comment after a score and thanks after sending`() {
        val view = render(popup("element-survey.json").design)
        val nps = view.byTag("el-survey-nps")!!
        val comment = nps.find(EditText::class.java).single()
        assertFalse(comment.isShown)
        nps.find(TextView::class.java).first { it.text.toString() == "0" }.performClick()
        assertTrue(comment.isShown)
        comment.setText("Долгая доставка")
        nps.find(TextView::class.java).first { it.text.toString() == "Отправить" }.performClick()
        assertEquals(listOf("review nps 0 Долгая доставка"), actions.calls)
        assertTrue(nps.find(TextView::class.java).first { it.text.toString() == "Спасибо за оценку!" }.isShown)
    }

    @Test
    fun `survey shows the send error and can be sent again`() {
        actions.reviewResult = "Channel not found"
        val view = render(popup("element-survey.json").design)
        val csat = view.byTag("el-survey-csat")!!
        csat.find(TextView::class.java).first { it.text.toString() == "5" }.performClick()
        val send = csat.find(TextView::class.java).first { it.text.toString() == "Отправить" }
        send.performClick()
        assertTrue(csat.find(TextView::class.java).first { it.text.toString() == "Channel not found" }.isShown)
        actions.reviewResult = null
        send.performClick()
        assertEquals(2, actions.calls.size)
    }

    @Test
    fun `personal promo code shows the code of the show and copies it`() {
        val view = render(popup("element-promo-code.json").design, mapOf("promo_code" to "ABC123"))
        val promo = view.byTag("el-promo-personal")!!
        assertTrue(promo.texts().contains("ABC123"))
        promo.find(TextView::class.java).first { it.text.toString() == "Копировать" }.let { (it.parent as View).performClick() }
        assertEquals(listOf("click", "copy ABC123", "link rees46demo://cart?promo=ABC123"), actions.calls)
        assertTrue(promo.texts().contains("Скопировано!"))
        val text = view.byTag("el-text-with-code")!!.find(TextView::class.java).single()
        assertEquals("Код ABC123 действует 7 дней", text.text.toString())
        assertTrue(view.byTag("el-promo-static")!!.texts().contains("WELCOME5"))
    }

    @Test
    fun `the frame of a modal is centered and never taller than the screen`() {
        val modal = PopupsV3List.parse(Fixtures.read("popups-v3/live-list-181-182.json")).popups[1]
        val root = PopupV3RootView(activity, modal, modal.design, emptyMap(), state, actions, null, blocking = true) {}
        root.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 390, 800)
        assertEquals(359, root.frame.width)
        assertEquals((390 - 359) / 2, root.frame.left)
        assertTrue(root.frame.height <= 800 - 2 * 16 - 25)
    }

    @Test
    fun `a banner sits at its edge with its offsets`() {
        val popups = JSONObject(Fixtures.read("popups-v3/layouts.json")).getJSONArray("popups")
        val top = (PopupV3.parse(popups.getJSONObject(0)) as PopupV3.Parsed.Ok).popup
        val root = PopupV3RootView(activity, top, top.design, emptyMap(), state, actions, null, blocking = false) {}
        root.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 390, 800)
        assertEquals(390 - 2 * 12, root.frame.width)
        assertEquals(12, root.frame.left)
        assertTrue("16 below the safe area", root.frame.top >= 16)
        assertFalse(root.isClickable)
    }
}
