package com.personalization.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Плашка цены товара в сторис.
 *
 * Источник: Figma, Card/Story/Price (10:875): скругление 12, заливка #F2F2F2 95%
 * (в макете сырой цвет, одинаковый в обеих темах — поэтому текст всегда Text/Dark).
 * Слева колонка с отступами 12/16: старая цена (SemiBold 14, зачёркнута, группа
 * прозрачна на 50%) и цена (SemiBold 24); валюта у обеих — Regular 14.
 *
 * Справа, по [type]:
 * - [Type.DISCOUNT] — блок Brand/Primary со скидкой SemiBold 36 (`10%`);
 * - [Type.PROMO_CODE] — чёрный блок с подписью «Promo code» и кодом SemiBold 24,
 *   иконка copy в углу. Нажатие копирует код в буфер, показывает тост [copiedText]
 *   (в задаче на странице Stories: «по умолчанию Copied») и зовёт [onPromoCopied];
 *   блок при нажатии чуть увеличивается — кадр Tap Zoom Effect.
 */
@InternalPersonalizationUiApi
class PersonalizationStoryPrice @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Type { PRICE, DISCOUNT, PROMO_CODE }

    var type: Type = Type.PRICE
        set(value) {
            field = value
            render()
        }

    /** Цена в формате сервера — `"1 190 900 ₽"`; валюта отделяется сама. */
    var price: CharSequence? = null
        set(value) {
            field = value
            render()
        }

    /** Старая цена; `null` — строки нет. */
    var oldPrice: CharSequence? = null
        set(value) {
            field = value
            render()
        }

    /** Текст скидки для [Type.DISCOUNT] — `"10%"`. */
    var discount: CharSequence? = null
        set(value) {
            field = value
            render()
        }

    /** Промокод для [Type.PROMO_CODE]. */
    var promoCode: String? = null
        set(value) {
            field = value
            render()
        }

    var promoCaption: CharSequence = "Promo code"
        set(value) {
            field = value
            render()
        }

    /** Текст тоста после копирования. */
    var copiedText: CharSequence = "Copied"

    /** Где показывать тост: в макете два варианта — над плашкой и сверху экрана. */
    var toastPosition: PersonalizationToast.Position = PersonalizationToast.Position.TOP

    /** Код скопирован в буфер. */
    var onPromoCopied: ((String) -> Unit)? = null

    private val priceColumn = LinearLayout(context)
    private val oldPriceView = AppCompatTextView(context)
    private val priceView = AppCompatTextView(context)
    private val discountView = AppCompatTextView(context)
    private val promoBox = FrameLayout(context)
    private val promoCaptionView = AppCompatTextView(context)
    private val promoCodeView = AppCompatTextView(context)
    private val copyIcon = AppCompatImageView(context)

    private val radius = dp(RADIUS_DP).toFloat()
    private val measurePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    init {
        orientation = HORIZONTAL
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(BOX_COLOR)
        }

        priceColumn.orientation = VERTICAL
        priceColumn.gravity = Gravity.CENTER_VERTICAL
        priceColumn.setPadding(dp(16), dp(12), dp(16), dp(12))
        oldPriceView.style(sizeSp = 14f, emphasized = true, colorRes = R.color.personalization_text_dark_secondary)
        oldPriceView.alpha = OLD_PRICE_ALPHA
        priceView.style(sizeSp = 24f, emphasized = true, colorRes = R.color.personalization_text_dark_primary)
        priceColumn.addView(oldPriceView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        priceColumn.addView(priceView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        discountView.style(sizeSp = 36f, emphasized = true, colorRes = R.color.personalization_text_light_primary)
        discountView.gravity = Gravity.CENTER
        discountView.setPadding(dp(16), 0, dp(16), 0)
        discountView.background = endRounded(PersonalizationTheme.color(context, R.color.personalization_brand_primary))

        val promoColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        promoCaptionView.style(sizeSp = 14f, emphasized = true, colorRes = R.color.personalization_text_light_secondary)
        promoCaptionView.gravity = Gravity.CENTER
        promoCodeView.style(sizeSp = 24f, emphasized = true, colorRes = R.color.personalization_text_light_primary)
        promoCodeView.gravity = Gravity.CENTER
        promoCodeView.maxLines = 1
        // Длинный код ужимается, а не режется: его надо прочитать целиком.
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            promoCodeView, 14, 24, 1, TypedValue.COMPLEX_UNIT_SP
        )
        promoColumn.addView(promoCaptionView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        promoColumn.addView(promoCodeView, LayoutParams(LayoutParams.MATCH_PARENT, dp(PROMO_CODE_HEIGHT_DP)))
        promoBox.addView(
            promoColumn,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        copyIcon.setImageResource(R.drawable.personalization_ic_copy)
        ImageViewCompat.setImageTintList(
            copyIcon,
            PersonalizationTheme.colorStateList(context, R.color.personalization_text_light_hint)
        )
        promoBox.addView(
            copyIcon,
            FrameLayout.LayoutParams(dp(COPY_DP), dp(COPY_DP), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(4)
                marginEnd = dp(4)
            }
        )
        promoBox.background = endRounded(PersonalizationTheme.color(context, R.color.personalization_text_dark_primary))
        promoBox.isClickable = true
        promoBox.contentDescription = promoCaption
        setupPromoPress()

        addView(priceColumn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(discountView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(promoBox, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        render()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Промокод делит ширину поровну с ценой, но длинный код берёт сколько нужно —
        // не больше [MAX_PROMO_SHARE] ряда, дальше код ужимается автоподбором кегля.
        if (promoBox.visibility == View.VISIBLE && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val total = MeasureSpec.getSize(widthMeasureSpec)
            measurePaint.typeface = promoCodeView.typeface
            measurePaint.textSize = spPx(24f).toFloat()
            val needed = measurePaint.measureText(promoCode.orEmpty()) + dp(16) * 2
            val width = needed.toInt().coerceIn(total / 2, (total * MAX_PROMO_SHARE).toInt())
            val params = promoBox.layoutParams as LayoutParams
            if (params.width != width) params.width = width
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun render() {
        val currencyPx = spPx(14f)
        val old = oldPrice?.takeIf { it.isNotEmpty() }
        oldPriceView.visibility = if (old != null) View.VISIBLE else View.GONE
        oldPriceView.text = old?.let {
            PersonalizationStoryAmount.parse(it).toSpannable(currencyPx, strikethrough = true)
        }
        priceView.text = price?.let { PersonalizationStoryAmount.parse(it).toSpannable(currencyPx) }

        val showDiscount = type == Type.DISCOUNT && !discount.isNullOrEmpty()
        val showPromo = type == Type.PROMO_CODE && !promoCode.isNullOrEmpty()
        discountView.visibility = if (showDiscount) View.VISIBLE else View.GONE
        discountView.text = discount
        promoBox.visibility = if (showPromo) View.VISIBLE else View.GONE
        promoCaptionView.text = promoCaption
        promoCodeView.text = promoCode
        promoBox.contentDescription = promoCaption

        // Колонка цены тянется, когда справа скидка; с промокодом делит ряд поровну.
        (priceColumn.layoutParams as LayoutParams).apply {
            width = if (showDiscount || showPromo) 0 else LayoutParams.WRAP_CONTENT
            weight = if (showDiscount || showPromo) 1f else 0f
        }
        (promoBox.layoutParams as LayoutParams).weight = 0f
        requestLayout()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPromoPress() {
        promoBox.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).setDuration(PRESS_MS).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(PRESS_MS).start()
            }
            false
        }
        promoBox.setOnClickListener { copyPromo() }
    }

    private fun copyPromo() {
        val code = promoCode?.takeIf { it.isNotEmpty() } ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(promoCaption, code))
        PersonalizationToast.show(this, copiedText, toastPosition)
        onPromoCopied?.invoke(code)
    }

    /** Правый блок повторяет скругление плашки только справа. */
    private fun endRounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        cornerRadii = if (rtl) {
            floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
        } else {
            floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
        }
    }

    private fun AppCompatTextView.style(sizeSp: Float, emphasized: Boolean, colorRes: Int) {
        includeFontPadding = false
        typeface = PersonalizationTheme.typeface(context, emphasized)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(PersonalizationTheme.color(context, colorRes))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun spPx(sp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt()

    private companion object {
        /** #F2F2F2 с прозрачностью 95% — как в макете, без переменной. */
        const val BOX_COLOR = 0xF2F2F2F2.toInt()
        const val RADIUS_DP = 12
        const val OLD_PRICE_ALPHA = 0.5f
        const val COPY_DP = 24
        const val PROMO_CODE_HEIGHT_DP = 29
        const val MAX_PROMO_SHARE = 0.65f
        const val PRESS_SCALE = 1.03f
        const val PRESS_MS = 120L
    }
}
