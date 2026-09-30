package com.personalization.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Карта лояльности в духе пропуска Apple Wallet.
 *
 * Источник: Figma Mobile SDK UI Kit, страница Loyalty (1:34) — фреймы Wallet/iOS/Light Theme
 * (570:9879) и Dark Theme (570:10131); компонент Wallet (570:9111) и его части: Header,
 * Wallet/Logo (570:9034), поле Wallet (559:8973, размеры MD и SM), Wallet/Stripe (520:8923),
 * Wallet/Stamps (570:10384, от 0 до 5 штампов), Wallet/Info (570:9081), Wallet/Code (520:8886).
 *
 * Сверху вниз: шапка с логотипом и балансом, полоса с картинкой и штампами, поля
 * (владелец, уровень), штрихкод номера карты. Карта держит пропорции пропуска 370×560 —
 * ниже не становится, а свободное место уходит над штрихкодом, как в макете.
 *
 * Данные даёт хост: статус лояльности в SDK отдаёт только уровень, а баланса, штампов
 * и номера карты в нём нет. Картинки грузит тоже хост — через [logoLoader], [stripeLoader]
 * и [emblemLoader], как у попапа: кит сеть не тянет.
 *
 * Фон по умолчанию Background/Card, текст Text/Primary; в тёмном примере макета карта
 * фирменного синего цвета — для этого [cardColor] и [contentColor]. Рамка Line/Generic Subtle,
 * скругление 2XL, тень Elevation 3.
 */
@InternalPersonalizationUiApi
class PersonalizationLoyaltyCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Поле карты: подпись капсом над значением. */
    data class Field(val label: CharSequence, val value: CharSequence)

    private val column = LinearLayout(context)

    private val header = LinearLayout(context)
    private val logoView = AppCompatImageView(context)
    private val balanceBlock = LinearLayout(context)
    private val balanceLabelView = AppCompatTextView(context)
    private val balanceView = AppCompatTextView(context)

    private val stripe = Stripe(context)
    private val info = LinearLayout(context)

    private val codeSection = LinearLayout(context)
    private val barcode = PersonalizationBarcode(context)

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val strokeRect = RectF()
    private val radius = PersonalizationTheme.radius(context, R.dimen.personalization_radius_xl2)

    /** Подпись баланса, например «Бонусы». Показывается капсом. */
    var balanceLabel: CharSequence? = null
        set(value) {
            field = value
            balanceLabelView.text = value
            applyVisibility()
        }

    /** Баланс уже отформатированным — разряды и валюту расставляет хост. */
    var balance: CharSequence? = null
        set(value) {
            field = value
            balanceView.text = value
            applyVisibility()
        }

    /** Поля под полосой по порядку; делят ширину поровну. Пусто — блока нет. */
    var fields: List<Field> = emptyList()
        set(value) {
            field = value
            buildFields()
            applyVisibility()
        }

    /** Собранные штампы; ограничивается [stampsTotal]. */
    var stamps: Int = 0
        set(value) {
            field = value
            buildStamps()
        }

    /** Сколько штампов всего. Ноль — ряда штампов нет. В макете их до пяти. */
    var stampsTotal: Int = 0
        set(value) {
            field = maxOf(0, value)
            buildStamps()
            applyVisibility()
        }

    /** Номер карты — рисуется штрихкодом Code 128. Пусто или не кодируется — блока нет. */
    var code: String? = null
        set(value) {
            field = value
            barcode.code = value
            applyVisibility()
        }

    /** Фон карты; null — Background/Card. */
    @ColorInt
    var cardColor: Int? = null
        set(value) {
            field = value
            applyColors()
        }

    /** Цвет подписей и значений; null — Text/Primary. Логотип им не красится — это картинка хоста. */
    @ColorInt
    var contentColor: Int? = null
        set(value) {
            field = value
            applyColors()
        }

    /** Логотип в шапке, высота 33, ширина по пропорциям картинки. */
    var logoLoader: ((ImageView) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(logoView)
            applyVisibility()
        }

    /** Картинка полосы, заполняет её с обрезкой. */
    var stripeLoader: ((ImageView) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(stripe.image)
            applyVisibility()
        }

    /** Эмблема справа на полосе — например, знак уровня. */
    var emblemLoader: ((ImageView) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(stripe.emblem)
            applyVisibility()
        }

    init {
        column.orientation = LinearLayout.VERTICAL

        val pad = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(pad, pad, pad, pad)
        logoView.adjustViewBounds = true
        logoView.scaleType = ImageView.ScaleType.FIT_START
        header.addView(logoView, LinearLayout.LayoutParams(WRAP, dp(33)))
        balanceBlock.orientation = LinearLayout.VERTICAL
        balanceBlock.gravity = Gravity.END
        styleLabel(balanceLabelView, Gravity.END)
        styleValue(balanceView, 24f, 24f, Gravity.END)
        balanceBlock.addView(balanceLabelView, LinearLayout.LayoutParams(MATCH, WRAP))
        balanceBlock.addView(balanceView, LinearLayout.LayoutParams(MATCH, WRAP))
        header.addView(balanceBlock, LinearLayout.LayoutParams(0, WRAP, 1f))
        column.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        column.addView(stripe, LinearLayout.LayoutParams(MATCH, WRAP))

        info.orientation = LinearLayout.HORIZONTAL
        info.setPadding(pad, pad, pad, pad)
        column.addView(info, LinearLayout.LayoutParams(MATCH, WRAP))

        // Штрихкод прижат к низу: секция забирает всё, что осталось от пропорций пропуска.
        codeSection.orientation = LinearLayout.VERTICAL
        codeSection.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        val codePadV = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl3)
        codeSection.setPadding(pad, codePadV, pad, codePadV)
        val codeBox = FrameLayout(context)
        val boxPad = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl2)
        codeBox.setPadding(boxPad, boxPad, boxPad, boxPad)
        codeBox.background = GradientDrawable().apply {
            cornerRadius = PersonalizationTheme.radius(context, R.dimen.personalization_radius_lg)
            // Brand/White, а не токен темы: сканеру нужен белый фон и в тёмной теме.
            setColor(Color.WHITE)
            setStroke(
                dp(1),
                PersonalizationTheme.color(context, R.color.personalization_line_generic_subtle)
            )
        }
        codeBox.addView(barcode, LayoutParams(WRAP, WRAP))
        codeSection.addView(codeBox, LinearLayout.LayoutParams(WRAP, WRAP))
        column.addView(codeSection, LinearLayout.LayoutParams(MATCH, 0, 1f))

        addView(column, LayoutParams(MATCH, MATCH))

        strokePaint.strokeWidth = dp(1).toFloat()
        strokePaint.color =
            PersonalizationTheme.color(context, R.color.personalization_line_generic_subtle)
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            clipToOutline = true
            elevation = resources.getDimension(R.dimen.personalization_elevation_e3)
        }

        applyColors()
        applyVisibility()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            dp(DESIGN_WIDTH)
        } else {
            MeasureSpec.getSize(widthMeasureSpec)
        }
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        // Сначала по содержимому, потом не ниже пропорций пропуска: тогда секция штрихкода
        // с весом получает остаток и прижимает штрихкод к низу.
        column.measure(exactWidth, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val height = maxOf(column.measuredHeight, width * DESIGN_HEIGHT / DESIGN_WIDTH)
        super.onMeasure(exactWidth, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    /** Рамка поверх содержимого — иначе полоса с картинкой перекрыла бы её по краям. */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val inset = strokePaint.strokeWidth / 2
        strokeRect.set(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(strokeRect, radius - inset, radius - inset, strokePaint)
    }

    private fun applyColors() {
        val background = cardColor
            ?: PersonalizationTheme.color(context, R.color.personalization_background_card)
        this.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(background)
        }
        val content = contentColor
            ?: PersonalizationTheme.color(context, R.color.personalization_text_primary)
        balanceLabelView.setTextColor(content)
        balanceView.setTextColor(content)
        for (i in 0 until info.childCount) {
            val field = info.getChildAt(i) as LinearLayout
            for (j in 0 until field.childCount) (field.getChildAt(j) as AppCompatTextView).setTextColor(content)
        }
    }

    private fun buildFields() {
        info.removeAllViews()
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_gap_modal)
        fields.forEachIndexed { index, field ->
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val label = AppCompatTextView(context).also { styleLabel(it, Gravity.START) }
            val value = AppCompatTextView(context).also { styleValue(it, 28f, 32f, Gravity.START) }
            label.text = field.label
            value.text = field.value
            column.addView(label, LinearLayout.LayoutParams(MATCH, WRAP))
            column.addView(value, LinearLayout.LayoutParams(MATCH, WRAP))
            info.addView(
                column,
                LinearLayout.LayoutParams(0, WRAP, 1f).also { if (index > 0) it.marginStart = gap }
            )
        }
        applyColors()
    }

    private fun buildStamps() {
        val row = stripe.stamps
        row.removeAllViews()
        val collected = stamps.coerceIn(0, stampsTotal)
        val side = dp(32)
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
        for (i in 0 until stampsTotal) {
            val done = i < collected
            val icon = AppCompatImageView(context)
            icon.setImageResource(
                if (done) R.drawable.personalization_ic_check_rosette_fill
                else R.drawable.personalization_ic_rosette
            )
            // Полоса в макете всегда в тёмном режиме, поэтому цвета — светлые постоянные,
            // а не зависящие от темы.
            ImageViewCompat.setImageTintList(
                icon,
                PersonalizationTheme.colorStateList(
                    context,
                    if (done) R.color.personalization_text_light_secondary
                    else R.color.personalization_text_light_hint
                )
            )
            icon.contentDescription = null
            row.addView(
                icon,
                LinearLayout.LayoutParams(side, side).also { if (i > 0) it.marginStart = gap }
            )
        }
        row.contentDescription = if (stampsTotal > 0) "$collected/$stampsTotal" else null
    }

    /** Пустое не показывается: шапка, полоса, поля и штрихкод прячутся порознь. */
    private fun applyVisibility() {
        val hasLogo = logoLoader != null
        val hasBalance = !balanceLabel.isNullOrEmpty() || !balance.isNullOrEmpty()
        logoView.isVisible = hasLogo
        balanceLabelView.isVisible = !balanceLabel.isNullOrEmpty()
        balanceView.isVisible = !balance.isNullOrEmpty()
        balanceBlock.isVisible = hasBalance
        header.isVisible = hasLogo || hasBalance
        stripe.image.isVisible = stripeLoader != null
        stripe.emblem.isVisible = emblemLoader != null
        stripe.isVisible = stripeLoader != null || emblemLoader != null || stampsTotal > 0
        info.isVisible = fields.isNotEmpty()
        codeSection.isVisible = barcode.hasBars
    }

    /** Подпись: 11/14, Semibold, трекинг +4 %, капсом. */
    private fun styleLabel(view: AppCompatTextView, gravity: Int) {
        view.includeFontPadding = false
        view.typeface = PersonalizationTheme.typeface(context, emphasized = true)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        TextViewCompat.setLineHeight(view, spToPx(14f))
        view.setLetterSpacingCompat(0.04f)
        view.isAllCaps = true
        view.gravity = gravity
        view.maxLines = 1
        view.ellipsize = TextUtils.TruncateAt.END
    }

    /** Значение: Regular, трекинг −5 %, в одну строку. */
    private fun styleValue(view: AppCompatTextView, size: Float, line: Float, gravity: Int) {
        view.includeFontPadding = false
        view.typeface = PersonalizationTheme.typeface(context, emphasized = false)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        TextViewCompat.setLineHeight(view, spToPx(line))
        view.setLetterSpacingCompat(-0.05f)
        view.gravity = gravity
        view.maxLines = 1
        view.ellipsize = TextUtils.TruncateAt.END
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun spToPx(sp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt()

    /**
     * Полоса: чёрная подложка, картинка, затемнение 30 %, эмблема и штампы.
     * Высота — ширина / 2.6, пропорция полосы пропуска Apple Wallet (375×144).
     */
    private class Stripe(context: Context) : FrameLayout(context) {

        val image = AppCompatImageView(context)
        val emblem = AppCompatImageView(context)
        val stamps = LinearLayout(context)

        init {
            // Подложка и затемнение — из макета: чёрный под картинкой и чёрный 30 % над ней.
            setBackgroundColor(Color.BLACK)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            addView(image, LayoutParams(MATCH, MATCH))
            addView(View(context).apply { setBackgroundColor(SCRIM) }, LayoutParams(MATCH, MATCH))
            emblem.scaleType = ImageView.ScaleType.FIT_END
            addView(emblem, LayoutParams(0, 0))
            stamps.orientation = LinearLayout.HORIZONTAL
            stamps.gravity = Gravity.CENTER_VERTICAL
            val pad = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
            stamps.setPadding(pad, 0, pad, 0)
            addView(stamps, LayoutParams(WRAP, MATCH, Gravity.START))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = (width / STRIPE_RATIO).toInt()
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
            val emblemHeight = (height * EMBLEM_HEIGHT).toInt()
            // Пока картинки нет — пропорции эмблемы из макета: загрузчику вроде Glide нужен
            // ненулевой размер, иначе он так и не начнёт грузить.
            val drawable = emblem.drawable
            val emblemWidth = if (drawable != null && drawable.intrinsicHeight > 0) {
                emblemHeight * drawable.intrinsicWidth / drawable.intrinsicHeight
            } else {
                (emblemHeight * EMBLEM_ASPECT).toInt()
            }
            emblem.measure(
                MeasureSpec.makeMeasureSpec(emblemWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(emblemHeight, MeasureSpec.EXACTLY)
            )
        }

        /**
         * Эмблема по геометрии макета: выше полосы в 1.45 раза, свисает за правый край
         * на 0.17 высоты и срезана сверху на 0.12 — полоса её обрезает.
         */
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            val h = bottom - top
            val emblemRight = (right - left) + (h * EMBLEM_OVERHANG).toInt()
            val emblemTop = -(h * EMBLEM_TOP).toInt()
            emblem.layout(
                emblemRight - emblem.measuredWidth,
                emblemTop,
                emblemRight,
                emblemTop + emblem.measuredHeight
            )
        }
    }

    private companion object {
        const val MATCH = LayoutParams.MATCH_PARENT
        const val WRAP = LayoutParams.WRAP_CONTENT

        /** Пропуск в макете 370×560. */
        const val DESIGN_WIDTH = 370
        const val DESIGN_HEIGHT = 560

        const val STRIPE_RATIO = 2.6f
        const val EMBLEM_HEIGHT = 1.4545f
        const val EMBLEM_OVERHANG = 0.17f
        const val EMBLEM_TOP = 0.1166f
        /** Эмблема в макете 179.5×205.8. */
        const val EMBLEM_ASPECT = 0.872f

        /** Чёрный 30 % — затемнение картинки полосы. */
        val SCRIM = Color.argb(77, 0, 0, 0)
    }
}
