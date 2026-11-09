package com.personalization.ui.popups

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.personalization.SDK
import com.personalization.popups.PopupV3Design
import com.personalization.popups.PopupV3Element
import com.personalization.popups.PopupV3Links
import com.personalization.popups.PopupV3Specs
import com.personalization.popups.PopupV3Texts
import com.personalization.popups.PopupV3Variables
import com.personalization.ui.components.PersonalizationCheckbox
import com.personalization.ui.components.PersonalizationFlowLayout
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.components.PersonalizationProductsBlock
import com.personalization.ui.widgets.PersonalizationRecommenderBlock
import java.util.Calendar
import java.util.Locale

/**
 * Что элементы попапа делают наружу: закрыть, события, ссылки, буфер, отправки форм и
 * опроса, клик по товару. Реализация — [PopupV3ActionHandler]; в тестах — подмена.
 * Колбэки отправок — на главном потоке: `null` — успех, иначе текст ошибки.
 */
internal interface PopupV3Actions {
    fun close()
    fun click()
    fun openLink(url: String?)
    fun copy(text: String)
    fun subscribe(spec: PopupV3Specs.Email, email: String, callback: (String?) -> Unit)
    fun saveProfile(spec: PopupV3Specs.Form, values: Map<String, String>, consent: Boolean, callback: (String?) -> Unit)
    fun review(spec: PopupV3Specs.Survey, rate: Int, comment: String?, callback: (String?) -> Unit)
    fun product(product: PersonalizationProduct)
}

/** Состояние элементов, которое переживает пересоздание окна (поворот): поля, галочки, оценки. */
internal class PopupV3State {
    private val values = HashMap<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(element: PopupV3Element, key: String): T? = values["${element.id}.$key"] as? T

    operator fun set(element: PopupV3Element, key: String, value: Any?) {
        values["${element.id}.$key"] = value
    }
}

/** Всё, что нужно рендеру одного попапа. */
internal class PopupV3RenderContext(
    val context: Context,
    val texts: PopupV3Texts,
    /** Витринные переменные показа (`promo_code`). */
    val variables: Map<String, String>,
    val state: PopupV3State,
    val actions: PopupV3Actions,
    val sdk: SDK?,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    /** Фон рамки из дизайна. */
    val frameBackground: Int = Color.WHITE,
    val log: (String) -> Unit = SDK::debug
) {
    fun dp(value: Double): Int = context.popupDp(value)
    fun dpF(value: Double): Float = context.popupDpF(value)
    fun spans(text: String?): CharSequence = PopupV3Text.spans(text, variables)
    fun runtime(text: String?): String? = text?.let { PopupV3Variables.applyRuntime(it, variables) }

    /** Контекст для компонентов кита в [element]: тема кита — по фону попапа под ним ([PopupV3KitTheme]). */
    fun kit(element: PopupV3Element): Context =
        PopupV3KitTheme.context(context, PopupV3KitTheme.background(PopupV3Specs.box(element).background, frameBackground))
}

/**
 * Рендер документа: сетка строк и ячеек, реестр элементов по `type`. Неизвестный тип
 * (если его не отсеял разбор) пропускается с записью в лог; новый тип добавляется одной
 * регистрацией в [renderers].
 */
internal object PopupV3Renderer {

    fun interface ElementRenderer {
        /** Содержимое коробки элемента; `null` — элемент не рисуется. */
        fun render(element: PopupV3Element, ctx: PopupV3RenderContext): View?
    }

    val renderers: MutableMap<String, ElementRenderer> = linkedMapOf(
        PopupV3Element.TEXT to ElementRenderer(::text),
        PopupV3Element.BOX to ElementRenderer(::text),
        PopupV3Element.BUTTON to ElementRenderer(::button),
        PopupV3Element.CLOSE to ElementRenderer(::close),
        PopupV3Element.IMAGE to ElementRenderer(::image),
        PopupV3Element.EMAIL_COLLECTOR to ElementRenderer(::email),
        PopupV3Element.FORM to ElementRenderer(::form),
        PopupV3Element.PROMO_CODE to ElementRenderer(::promo),
        PopupV3Element.SURVEY to ElementRenderer(::survey),
        PopupV3Element.RECOMMENDATION to ElementRenderer(::recommendation)
    )

    /** Содержимое рамки: строки сетки или, у старого дизайна, элементы в потоке. */
    fun content(design: PopupV3Design, ctx: PopupV3RenderContext): View {
        val stack = PopupV3StackLayout(ctx.context, ctx.dp(PopupV3Design.ROWS_GAP))
        val free = design.free
        if (free != null) {
            val cell = PopupV3CellLayout(ctx.context, "left", "top", ctx.dp(PopupV3Design.ROWS_GAP))
            free.forEach { element -> element(element, ctx)?.let(cell::addView) }
            stack.addView(cell)
            return stack
        }
        design.rows.forEach { row ->
            val gap = ctx.dp(row.gap)
            val background = rowBackground(row, ctx)
            val rowView = PopupV3RowLayout(ctx.context, row.fractions, gap, ctx.dp(row.minHeight), background)
            row.background?.let { rowView.setBackgroundColor(it) }
            row.cells.forEach { cell ->
                val cellView = PopupV3CellLayout(ctx.context, cell.align, cell.verticalAlign, gap)
                cell.elements.forEach { element -> element(element, ctx)?.let(cellView::addView) }
                rowView.addView(cellView)
            }
            stack.addView(rowView)
        }
        return stack
    }

    /** Фон строки картинкой: `contain` — целиком, остальное — с заполнением; плитки нет. */
    private fun rowBackground(row: PopupV3Design.Row, ctx: PopupV3RenderContext): View? {
        val url = row.backgroundImage ?: return null
        return ImageView(ctx.context).apply {
            scaleType = when (row.backgroundSize) {
                "contain" -> ImageView.ScaleType.FIT_CENTER
                "auto" -> ImageView.ScaleType.CENTER
                else -> ImageView.ScaleType.CENTER_CROP
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            Glide.with(ctx.context).load(url).into(this)
        }
    }

    /** Элемент в коробке стилей с параметрами ячейки; `null` — не рисуется. */
    fun element(element: PopupV3Element, ctx: PopupV3RenderContext): View? {
        val renderer = renderers[element.type]
        if (renderer == null) {
            ctx.log("popups v3: element ${element.id} skipped: no renderer for ${element.type}")
            return null
        }
        val content = try {
            renderer.render(element, ctx)
        } catch (throwable: Throwable) {
            ctx.log("popups v3: element ${element.id} (${element.type}) failed: ${throwable.message}")
            null
        } ?: return null
        val box = PopupV3Specs.box(element)
        val buttonLike = element.type == PopupV3Element.BUTTON || element.type == PopupV3Element.CLOSE
        // Кнопка без своего cellAlign встаёт по buttonAlign во всю ширину ячейки.
        val stretch = element.stretch || (buttonLike && element.cellAlign == null)
        val wrapper = PopupV3ElementBox(ctx.context, box)
        val width = if (stretch) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        wrapper.addView(content, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
        PopupV3Specs.elementLink(element)?.let { link ->
            wrapper.isClickable = true
            wrapper.setOnClickListener {
                ctx.actions.click()
                ctx.actions.openLink(ctx.runtime(link))
            }
            wrapper.popupPressFeedback()
        }
        wrapper.layoutParams = PopupV3CellLayout.LayoutParams(stretch, element.cellAlign, element.cellVerticalAlign)
        wrapper.tag = element.id
        return wrapper
    }

    private fun text(element: PopupV3Element, ctx: PopupV3RenderContext): View {
        val spec = PopupV3Specs.text(element)
        val box = PopupV3Specs.box(element)
        val message = spec.message ?: return View(ctx.context)
        return PopupV3TextView(ctx.context).apply {
            PopupV3Text.style(this, box.fontFamily, spec.fontSize, spec.fontWeight, box.textColor, box.lineHeight, box.letterSpacing)
            gravity = PopupV3Text.gravity(spec.align)
            text = ctx.spans(message)
        }
    }

    /** Кнопка дизайна: свой фон, радиус, отступы и шрифт; встаёт по `buttonAlign`. */
    private fun buttonView(ctx: PopupV3RenderContext, spec: PopupV3Specs.Button): TextView =
        PopupV3TextView(ctx.context).apply {
            PopupV3Text.style(this, spec.fontFamily, spec.fontSize, 500.0, spec.textColor, 1.0)
            gravity = Gravity.CENTER
            background = popupBackground(ctx.context, spec.background, spec.radius)
            setPadding(ctx.dp(spec.paddingX), ctx.dp(spec.paddingY), ctx.dp(spec.paddingX), ctx.dp(spec.paddingY))
            text = ctx.spans(spec.label)
            isClickable = true
            isFocusable = true
            popupPressFeedback()
        }

    private fun aligned(ctx: PopupV3RenderContext, child: View, align: String): View =
        FrameLayout(ctx.context).apply {
            addView(child, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, PopupV3Text.gravity(align)))
        }

    private fun button(element: PopupV3Element, ctx: PopupV3RenderContext): View {
        val spec = PopupV3Specs.button(element, ctx.texts)
        val view = buttonView(ctx, spec)
        view.setOnClickListener {
            when (spec.action) {
                "close" -> ctx.actions.close()
                "copy" -> {
                    ctx.actions.click()
                    ctx.actions.copy(ctx.runtime(spec.copyText).orEmpty())
                    com.personalization.ui.components.PersonalizationToast.show(view, ctx.texts.copied)
                }
                else -> {
                    ctx.actions.click()
                    ctx.actions.openLink(ctx.runtime(spec.href))
                }
            }
        }
        return aligned(ctx, view, spec.align)
    }

    private fun close(element: PopupV3Element, ctx: PopupV3RenderContext): View {
        val spec = PopupV3Specs.button(element, ctx.texts)
        val icon = PopupV3Specs.close(element, ctx.texts)
        val size = ctx.dp(icon.size)
        val view: View = when (icon.mode) {
            "image" -> FrameLayout(ctx.context).apply {
                background = popupBackground(ctx.context, spec.background, spec.radius)
                setPadding(ctx.dp(spec.paddingX), ctx.dp(spec.paddingY), ctx.dp(spec.paddingX), ctx.dp(spec.paddingY))
                addView(ImageView(ctx.context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    Glide.with(ctx.context).load(icon.imageUrl).into(this)
                }, FrameLayout.LayoutParams(size, size))
            }
            "svg" -> FrameLayout(ctx.context).apply {
                background = popupBackground(ctx.context, spec.background, spec.radius)
                setPadding(ctx.dp(spec.paddingX), ctx.dp(spec.paddingY), ctx.dp(spec.paddingX), ctx.dp(spec.paddingY))
                addView(PopupV3CrossView(ctx.context, size, spec.textColor))
            }
            else -> buttonView(ctx, spec)
        }
        view.contentDescription = icon.alt
        view.isClickable = true
        view.popupPressFeedback()
        view.setOnClickListener { ctx.actions.close() }
        return aligned(ctx, view, spec.align)
    }

    private fun image(element: PopupV3Element, ctx: PopupV3RenderContext): View? {
        val spec = PopupV3Specs.image(element, ctx.texts)
        if (!spec.show) return null
        val url = PopupV3Links.image(ctx.runtime(spec.url))
        val view = PopupV3ImageView(ctx.context, spec, ctx.screenHeightPx)
        view.contentDescription = spec.alt
        view.scaleType = when (spec.fit) {
            "contain" -> ImageView.ScaleType.FIT_CENTER
            "fill" -> ImageView.ScaleType.FIT_XY
            "none" -> ImageView.ScaleType.CENTER
            else -> ImageView.ScaleType.CENTER_CROP
        }
        view.popupClipRound(ctx.dpF(spec.radius))
        if (url != null) view.load(url, ctx.screenWidthPx, ctx.screenHeightPx) else ctx.log("popups v3: image ${element.id} has no picture")
        if (spec.marginBottom <= 0) return view
        return LinearLayout(ctx.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = ctx.dp(spec.marginBottom)
            })
        }
    }

    // region Формы

    private fun input(ctx: PopupV3RenderContext, input: PopupV3Specs.Input, family: String?): AppCompatEditText =
        AppCompatEditText(ctx.context).apply {
            PopupV3Text.style(this, family, input.fontSize, 400.0, input.color, 1.25)
            setHintTextColor((input.color and 0x00FFFFFF) or (0x80 shl 24))
            background = popupBackground(ctx.context, input.background, input.radius, 1.0, input.borderColor)
            setPadding(ctx.dp(12.0), ctx.dp(input.paddingY), ctx.dp(12.0), ctx.dp(input.paddingY))
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

    private fun submitButton(ctx: PopupV3RenderContext, spec: PopupV3Specs.FormButton, family: String?, label: String): TextView =
        PopupV3TextView(ctx.context).apply {
            PopupV3Text.style(this, family, spec.fontSize, 500.0, spec.textColor, 1.25)
            gravity = Gravity.CENTER
            background = popupBackground(ctx.context, spec.background, spec.radius)
            setPadding(ctx.dp(spec.paddingX), ctx.dp(spec.paddingY), ctx.dp(spec.paddingX), ctx.dp(spec.paddingY))
            text = ctx.spans(label)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            isClickable = true
            isFocusable = true
            popupPressFeedback()
        }

    private fun statusView(ctx: PopupV3RenderContext, family: String?): TextView = TextView(ctx.context).apply {
        PopupV3Text.style(this, family, 12.0, 400.0, ERROR_COLOR, 1.35)
        visibility = View.GONE
    }

    /**
     * Согласие: чекбокс кита в цветах формы — отмеченный как кнопка, пустой как поле, —
     * чтобы он не зависел от темы системы, как и весь попап.
     */
    private fun consentRow(
        ctx: PopupV3RenderContext,
        label: String,
        family: String?,
        color: Int,
        input: PopupV3Specs.Input,
        button: PopupV3Specs.FormButton,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): View {
        val checkbox = PersonalizationCheckbox(ctx.context).apply {
            checkedColor = button.background
            checkmarkColor = button.textColor
            uncheckedColor = input.background
            uncheckedBorderColor = input.borderColor
            checkState = if (checked) PersonalizationCheckbox.CheckState.CHECKED else PersonalizationCheckbox.CheckState.UNCHECKED
            onCheckedChange = { onChange(it == PersonalizationCheckbox.CheckState.CHECKED) }
        }
        val text = PopupV3TextView(ctx.context).apply {
            PopupV3Text.style(this, family, 11.0, 400.0, color, 1.35)
            text = ctx.spans(label)
            setOnClickListener { checkbox.performClick() }
        }
        return LinearLayout(ctx.context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(checkbox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(8.0) })
        }
    }

    private fun successView(
        ctx: PopupV3RenderContext,
        title: String?,
        message: String?,
        family: String?,
        success: PopupV3Specs.Success?
    ): View = LinearLayout(ctx.context).apply {
        orientation = LinearLayout.VERTICAL
        val align = success?.align ?: "center"
        gravity = PopupV3Text.gravity(align)
        if (success != null) {
            background = popupBackground(ctx.context, success.background, success.radius)
            setPadding(ctx.dp(success.paddingX), ctx.dp(success.paddingY), ctx.dp(success.paddingX), ctx.dp(success.paddingY))
        }
        title?.let {
            addView(PopupV3TextView(ctx.context).apply {
                PopupV3Text.style(this, family, success?.titleFontSize ?: 16.0, 600.0, success?.titleColor ?: 0xFF0F172A.toInt(), 1.3)
                gravity = PopupV3Text.gravity(align)
                text = ctx.spans(it)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        message?.let {
            addView(PopupV3TextView(ctx.context).apply {
                PopupV3Text.style(this, family, success?.fontSize ?: 13.0, 400.0, success?.color ?: 0xFF0F172A.toInt(), 1.4)
                gravity = PopupV3Text.gravity(align)
                text = ctx.spans(it)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (title != null) topMargin = ctx.dp(6.0)
            })
        }
    }

    private fun column(ctx: PopupV3RenderContext, gap: Double): LinearLayout = LinearLayout(ctx.context).apply {
        orientation = LinearLayout.VERTICAL
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = android.graphics.drawable.ShapeDrawable().apply {
            intrinsicHeight = ctx.dp(gap)
            intrinsicWidth = ctx.dp(gap)
            paint.color = 0
        }
    }

    private fun EditText.onText(change: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = change(s?.toString().orEmpty())
    })

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    /**
     * Подписка: поле email, кнопка, обязательное согласие, ошибка и экран успеха. Проверка —
     * согласие и формат email; отправка — подписка с флагами и сегмент ([PopupV3Actions.subscribe]).
     */
    private fun email(element: PopupV3Element, ctx: PopupV3RenderContext): View {
        val spec = PopupV3Specs.email(element, ctx.texts)
        val box = PopupV3Specs.box(element)
        val family = box.fontFamily
        val state = ctx.state
        val container = FrameLayout(ctx.context)
        val form = column(ctx, 8.0)
        val success = successView(ctx, spec.successTitle, spec.successMessage, family, spec.success)

        val field = input(ctx, spec.input, family).apply {
            hint = ctx.runtime(spec.placeholder)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(state.get<String>(element, "email").orEmpty())
            onText { state[element, "email"] = it }
        }
        val submit = submitButton(ctx, spec.button, family, spec.submitText)
        val row = LinearLayout(ctx.context).apply {
            orientation = if (spec.inline) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (spec.inline) {
                addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                addView(submit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginStart = ctx.dp(8.0)
                })
            } else {
                addView(field, matchWidth())
                addView(submit, matchWidth().apply { topMargin = ctx.dp(8.0) })
            }
        }
        val status = statusView(ctx, family)
        form.addView(row, matchWidth())
        form.addView(consentRow(ctx, spec.consentLabel, family, box.textColor, spec.input, spec.button, state.get<Boolean>(element, "consent") == true) {
            state[element, "consent"] = it
        }, matchWidth())
        form.addView(status, matchWidth())

        fun showDone() {
            if (spec.successTitle == null && spec.successMessage == null) {
                field.setText("")
                return
            }
            form.visibility = View.GONE
            success.visibility = View.VISIBLE
        }
        submit.setOnClickListener {
            if (state.get<Boolean>(element, "sending") == true) return@setOnClickListener
            val email = field.text?.toString()?.trim().orEmpty()
            if (state.get<Boolean>(element, "consent") != true || !isEmail(email)) {
                status.text = ctx.spans(spec.errorMessage)
                status.visibility = View.VISIBLE
                return@setOnClickListener
            }
            status.visibility = View.GONE
            state[element, "sending"] = true
            submit.isEnabled = false
            submit.alpha = 0.6f
            ctx.actions.subscribe(spec, email) { error ->
                state[element, "sending"] = false
                submit.isEnabled = true
                submit.alpha = 1f
                if (error == null) {
                    state[element, "done"] = true
                    showDone()
                } else {
                    status.text = error
                    status.visibility = View.VISIBLE
                }
            }
        }

        container.addView(form, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(success, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        success.visibility = View.GONE
        if (state.get<Boolean>(element, "done") == true && (spec.successTitle != null || spec.successMessage != null)) {
            form.visibility = View.GONE
            success.visibility = View.VISIBLE
        }
        return container
    }

    /** Форма профиля: до 8 полей, согласие, ошибка, отправка ([PopupV3Actions.saveProfile]), успех. */
    @SuppressLint("SetTextI18n")
    private fun form(element: PopupV3Element, ctx: PopupV3RenderContext): View {
        val spec = PopupV3Specs.form(element, ctx.texts)
        val box = PopupV3Specs.box(element)
        val family = box.fontFamily
        val state = ctx.state
        val values: MutableMap<String, String> = state.get<MutableMap<String, String>>(element, "values") ?: HashMap<String, String>().also {
            state[element, "values"] = it
        }
        val container = FrameLayout(ctx.context)
        val form = column(ctx, 8.0)
        val success = successView(ctx, spec.successTitle, spec.successMessage, family, null)

        spec.fields.forEach { field ->
            val column = column(ctx, 4.0)
            if (field.label.isNotEmpty()) {
                column.addView(PopupV3TextView(ctx.context).apply {
                    PopupV3Text.style(this, family, 11.0, 500.0, box.textColor, 1.35)
                    text = ctx.spans(field.label + if (field.required) " *" else "")
                }, matchWidth())
            }
            val view = input(ctx, spec.input, family).apply {
                hint = ctx.runtime(field.placeholder)
                setText(display(field, values[field.id], ctx.texts))
            }
            when (field.kind) {
                "email" -> view.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                "phone" -> view.inputType = InputType.TYPE_CLASS_PHONE
                "first_name", "last_name" -> view.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                    InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                "birthday", "gender" -> {
                    view.isFocusable = false
                    view.isCursorVisible = false
                    view.isClickable = true
                    view.setOnClickListener {
                        if (field.kind == "birthday") pickDate(ctx, values[field.id]) { value ->
                            values[field.id] = value
                            view.setText(value)
                        } else pickGender(ctx, view) { value ->
                            values[field.id] = value
                            view.setText(display(field, value, ctx.texts))
                        }
                    }
                }
                else -> view.inputType = InputType.TYPE_CLASS_TEXT
            }
            if (field.kind != "birthday" && field.kind != "gender") view.onText { values[field.id] = it.trim() }
            column.addView(view, matchWidth())
            form.addView(column, matchWidth())
        }
        spec.consentLabel?.let { label ->
            form.addView(consentRow(ctx, label, family, box.textColor, spec.input, spec.button, state.get<Boolean>(element, "consent") == true) {
                state[element, "consent"] = it
            }, matchWidth())
        }
        val status = statusView(ctx, family)
        form.addView(status, matchWidth())
        val submit = submitButton(ctx, spec.button, family, spec.submitText)
        form.addView(submit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        submit.setOnClickListener {
            if (state.get<Boolean>(element, "sending") == true) return@setOnClickListener
            val filled = spec.fields.associate { it.id to values[it.id].orEmpty().trim() }
            val invalid = spec.fields.any { field ->
                val value = filled.getValue(field.id)
                (field.required && value.isEmpty()) ||
                    (value.isNotEmpty() && field.kind == "email" && !isEmail(value)) ||
                    (value.isNotEmpty() && field.kind == "phone" && !isPhone(value))
            }
            if (invalid) {
                status.text = ctx.spans(spec.errorMessage)
                status.visibility = View.VISIBLE
                return@setOnClickListener
            }
            status.visibility = View.GONE
            state[element, "sending"] = true
            submit.isEnabled = false
            submit.alpha = 0.6f
            val profile = LinkedHashMap<String, String>()
            spec.fields.forEach { field ->
                val value = filled.getValue(field.id)
                if (value.isNotEmpty() && field.profileKey.isNotEmpty()) profile[field.profileKey] = value
            }
            ctx.actions.saveProfile(spec, profile, state.get<Boolean>(element, "consent") == true) { error ->
                state[element, "sending"] = false
                submit.isEnabled = true
                submit.alpha = 1f
                if (error == null) {
                    state[element, "done"] = true
                    if (spec.successTitle != null || spec.successMessage != null) {
                        form.visibility = View.GONE
                        success.visibility = View.VISIBLE
                    }
                } else {
                    status.text = error
                    status.visibility = View.VISIBLE
                }
            }
        }
        container.addView(form, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(success, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        success.visibility = View.GONE
        if (state.get<Boolean>(element, "done") == true && (spec.successTitle != null || spec.successMessage != null)) {
            form.visibility = View.GONE
            success.visibility = View.VISIBLE
        }
        return container
    }

    private fun display(field: PopupV3Specs.Field, value: String?, texts: PopupV3Texts): String = when {
        value == null -> ""
        field.kind == "gender" -> when (value) {
            "m" -> texts.genderMale
            "f" -> texts.genderFemale
            else -> ""
        }
        else -> value
    }

    private fun pickDate(ctx: PopupV3RenderContext, current: String?, onPick: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        current?.split("-")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 }?.let { (year, month, day) ->
            calendar.set(year, month - 1, day)
        }
        DatePickerDialog(
            ctx.context,
            { _, year, month, day -> onPick(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)) },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickGender(ctx: PopupV3RenderContext, anchor: View, onPick: (String) -> Unit) {
        PopupMenu(ctx.context, anchor).apply {
            menu.add(0, 1, 0, ctx.texts.genderMale)
            menu.add(0, 2, 1, ctx.texts.genderFemale)
            setOnMenuItemClickListener { item ->
                onPick(if (item.itemId == 1) "m" else "f")
                true
            }
        }.show()
    }

    // endregion

    /**
     * Промокод: подпись, код моноширинным жирным на плашке и «Скопировать» →
     * «Скопировано», затем ссылка применения, если задана.
     */
    private fun promo(element: PopupV3Element, ctx: PopupV3RenderContext): View? {
        val spec = PopupV3Specs.promo(element, ctx.texts)
        val box = PopupV3Specs.box(element)
        val text = PopupV3Specs.text(element)
        val code = ctx.runtime(spec.code).orEmpty()
        if (code.isEmpty() || code.contains("{{")) {
            ctx.log("popups v3: promo code ${element.id} has no code")
            return null
        }
        val gravity = PopupV3Text.gravity(spec.align)
        val column = column(ctx, 6.0).apply { this.gravity = gravity }
        spec.label?.let { label ->
            column.addView(PopupV3TextView(ctx.context).apply {
                PopupV3Text.style(this, box.fontFamily, text.fontSize, text.fontWeight, box.textColor, box.lineHeight, box.letterSpacing)
                this.gravity = gravity
                this.text = ctx.spans(label)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val plate = LinearLayout(ctx.context).apply {
            orientation = LinearLayout.HORIZONTAL
            this.gravity = Gravity.CENTER_VERTICAL
            background = popupBackground(ctx.context, spec.codeBackground, spec.codeRadius)
            setPadding(ctx.dp(14.0), ctx.dp(8.0), ctx.dp(14.0), ctx.dp(8.0))
        }
        plate.addView(TextView(ctx.context).apply {
            PopupV3Text.style(this, "monospace", spec.codeFontSize, 700.0, spec.codeColor, null, 1.0)
            this.text = code
            setTextIsSelectable(false)
        })
        if (spec.showCopy) {
            val copied = ctx.state.get<Boolean>(element, "copied") == true
            val copy = PopupV3TextView(ctx.context).apply {
                PopupV3Text.style(this, box.fontFamily, 11.0, 500.0, spec.codeColor)
                alpha = 0.8f
                this.text = ctx.spans(if (copied) spec.copiedText else spec.copyText)
            }
            plate.addView(copy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = ctx.dp(10.0)
            })
            plate.isClickable = true
            plate.popupPressFeedback()
            plate.setOnClickListener {
                ctx.actions.click()
                ctx.actions.copy(code)
                ctx.state[element, "copied"] = true
                copy.text = ctx.spans(spec.copiedText)
                spec.applyHref?.let { ctx.actions.openLink(PopupV3Variables.applyRuntime(it, ctx.variables)) }
            }
        }
        column.addView(plate, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return column
    }

    /**
     * Мини-опрос: вопрос, шкала (NPS 0–10, CSAT 1–5, CES 1–7), подписи краёв; после оценки —
     * комментарий и кнопка; отправка ([PopupV3Actions.review]) → «спасибо», ошибка — текстом.
     */
    private fun survey(element: PopupV3Element, ctx: PopupV3RenderContext): View {
        val spec = PopupV3Specs.survey(element, ctx.texts)
        val box = PopupV3Specs.box(element)
        val family = box.fontFamily
        val state = ctx.state
        val column = column(ctx, 10.0)
        spec.question?.let {
            column.addView(PopupV3TextView(ctx.context).apply {
                PopupV3Text.style(this, family, box.fontSize, 600.0, box.textColor, box.lineHeight)
                text = ctx.spans(it)
            }, matchWidth())
        }
        val thanks = PopupV3TextView(ctx.context).apply {
            PopupV3Text.style(this, family, box.fontSize, 600.0, box.textColor, box.lineHeight)
            text = ctx.spans(spec.thanks)
        }
        val interactive = column(ctx, 10.0)
        val scale = PersonalizationFlowLayout(ctx.context, ctx.dp(6.0), ctx.dp(6.0))
        val formView = column(ctx, 8.0)
        val comment = AppCompatEditText(ctx.context).apply {
            PopupV3Text.style(this, family, 13.0, 400.0, box.textColor, 1.35)
            setHintTextColor((box.textColor and 0x00FFFFFF) or (0x80 shl 24))
            background = popupBackground(ctx.context, 0xFFFFFFFF.toInt(), 10.0, 1.0, 0xFFCBD5E1.toInt())
            setPadding(ctx.dp(12.0), ctx.dp(8.0), ctx.dp(12.0), ctx.dp(8.0))
            hint = ctx.runtime(spec.commentPlaceholder)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setText(state.get<String>(element, "comment").orEmpty())
            onText { state[element, "comment"] = it }
        }
        val status = statusView(ctx, family)
        val submit = submitButton(ctx, spec.button, family, spec.submitText)
        formView.addView(comment, matchWidth())
        formView.addView(status, matchWidth())
        formView.addView(submit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scores = spec.scale.map { score ->
            PopupV3TextView(ctx.context).apply {
                PopupV3Text.style(this, family, 13.0, 600.0, spec.scaleColor)
                gravity = Gravity.CENTER
                minWidth = ctx.dp(30.0)
                minimumWidth = ctx.dp(30.0)
                setPadding(ctx.dp(4.0), 0, ctx.dp(4.0), 0)
                text = score.toString()
                isClickable = true
                popupPressFeedback()
            }
        }
        fun paint() {
            val rate = state.get<Int>(element, "rate")
            scores.forEachIndexed { index, view ->
                val active = spec.scale.first + index == rate
                view.background = popupBackground(ctx.context, if (active) spec.activeBackground else spec.scaleBackground, 8.0)
                view.setTextColor(if (active) spec.activeColor else spec.scaleColor)
            }
            formView.visibility = if (rate != null) View.VISIBLE else View.GONE
        }
        scores.forEachIndexed { index, view ->
            view.setOnClickListener {
                if (state.get<Boolean>(element, "sending") == true) return@setOnClickListener
                state[element, "rate"] = spec.scale.first + index
                paint()
            }
            scale.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(30.0)))
        }
        interactive.addView(scale, matchWidth())
        if (spec.minLabel != null || spec.maxLabel != null) {
            interactive.addView(LinearLayout(ctx.context).apply {
                orientation = LinearLayout.HORIZONTAL
                alpha = 0.7f
                addView(PopupV3TextView(ctx.context).apply {
                    PopupV3Text.style(this, family, 11.0, 400.0, box.textColor)
                    text = ctx.spans(spec.minLabel)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(PopupV3TextView(ctx.context).apply {
                    PopupV3Text.style(this, family, 11.0, 400.0, box.textColor)
                    gravity = Gravity.END
                    text = ctx.spans(spec.maxLabel)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }, matchWidth())
        }
        interactive.addView(formView, matchWidth())
        paint()

        submit.setOnClickListener {
            val rate = state.get<Int>(element, "rate") ?: return@setOnClickListener
            if (state.get<Boolean>(element, "sending") == true) return@setOnClickListener
            state[element, "sending"] = true
            submit.isEnabled = false
            submit.alpha = 0.6f
            status.visibility = View.GONE
            ctx.actions.review(spec, rate, comment.text?.toString()) { error ->
                state[element, "sending"] = false
                submit.isEnabled = true
                submit.alpha = 1f
                if (error == null) {
                    state[element, "done"] = true
                    interactive.visibility = View.GONE
                    thanks.visibility = View.VISIBLE
                } else {
                    status.text = error
                    status.visibility = View.VISIBLE
                }
            }
        }
        val done = state.get<Boolean>(element, "done") == true
        interactive.visibility = if (done) View.GONE else View.VISIBLE
        thanks.visibility = if (done) View.VISIBLE else View.GONE
        column.addView(interactive, matchWidth())
        column.addView(thanks, matchWidth())
        return column
    }

    /**
     * Лента рекомендаций кита по коду блока; до ответа — место 40, клик — источник `dynamic`.
     * Тема кита — по фону попапа, а не системы.
     */
    private fun recommendation(element: PopupV3Element, ctx: PopupV3RenderContext): View? {
        val code = PopupV3Specs.recommenderCode(element) ?: return null
        return FrameLayout(ctx.context).apply {
            minimumHeight = ctx.dp(40.0)
            val block = PersonalizationRecommenderBlock(ctx.kit(element)).apply {
                layout = PersonalizationProductsBlock.Layout.CAROUSEL
                this.code = code
                onProductClick = { product -> ctx.actions.product(product) }
                onError = { errorCode, message -> ctx.log("popups v3: recommendations $code failed: $errorCode $message") }
            }
            addView(block, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            ctx.sdk?.let(block::attach)
        }
    }

    fun isEmail(value: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(value).matches()

    fun isPhone(value: String): Boolean = value.count(Char::isDigit) in 7..15 && value.matches(Regex("^\\+?[0-9 ()\\-]+$"))

    private const val ERROR_COLOR = 0xFFB91C1C.toInt()
}

/**
 * Картинка элемента: на ширину ячейки, высота — `auto` (по соотношению сторон или по самой
 * картинке), px, % или `vh` (от высоты экрана). Грузится Glide без привязки к размеру вью:
 * у `auto` высоты до загрузки нет.
 */
@SuppressLint("ViewConstructor")
internal class PopupV3ImageView(context: Context, private val spec: PopupV3Specs.Image, private val screenHeightPx: Int) :
    AppCompatImageView(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = when (spec.heightMode) {
            "px" -> context.popupDp(spec.height)
            "%", "vh" -> (screenHeightPx * spec.height / 100.0).toInt()
            else -> spec.aspectRatio?.let { (width / it).toInt() } ?: drawable?.let { image ->
                if (image.intrinsicWidth > 0 && image.intrinsicHeight > 0) width * image.intrinsicHeight / image.intrinsicWidth else 0
            } ?: 0
        }
        setMeasuredDimension(width, height)
    }

    fun load(url: String, maxWidth: Int, maxHeight: Int) {
        Glide.with(context)
            .load(url)
            .downsample(DownsampleStrategy.AT_MOST)
            .into(object : CustomTarget<Drawable>(maxWidth.coerceAtLeast(1), maxHeight.coerceAtLeast(1)) {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    setImageDrawable(resource)
                    (resource as? Animatable)?.start()
                    requestLayout()
                }

                override fun onLoadCleared(placeholder: Drawable?) = setImageDrawable(placeholder)

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    SDK.debug("popups v3: image $url failed to load")
                }
            })
    }
}
