package com.personalization.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Штрихкод Code 128.
 *
 * Источник: Figma Mobile SDK UI Kit, компонент Wallet/Code (520:8886): штрихкод 232×42
 * на белой подложке. Здесь только штрихи — подложку с полями рисует тот, кто кладёт
 * штрихкод (карта лояльности, промокод).
 *
 * Ширина модуля — целое число пикселей: дробные модули на экране размываются, и сканер
 * читает их хуже. Поэтому штрихкод не растягивается ровно на 232, а берёт наибольший
 * целый модуль, при котором в 232 помещается, — итоговая ширина чуть меньше и зависит
 * от длины кода. Штрихи чёрные в любой теме: сканеру нужен контраст, а не палитра.
 *
 * Строку, которую Code 128 не несёт (пустую, не ASCII), не рисует — размер нулевой.
 */
@InternalPersonalizationUiApi
class PersonalizationBarcode @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Кодируемая строка — номер карты, промокод. */
    var code: String? = null
        set(value) {
            field = value
            modules = value?.let(PersonalizationCode128::encode)
            contentDescription = value
            requestLayout()
            invalidate()
        }

    /** Есть ли что рисовать: строка задана и кодируется. */
    val hasBars: Boolean
        get() = modules != null

    private var modules: BooleanArray? = null

    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val preferredWidth = dp(232)
    private val preferredHeight = dp(42)

    /** Ширина модуля в пикселях при данной доступной ширине. */
    private fun moduleWidth(available: Int): Int {
        val count = modules?.size ?: return 0
        return maxOf(1, available / count)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = modules?.size
        if (count == null) {
            setMeasuredDimension(0, 0)
            return
        }
        val available = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> preferredWidth
            else -> minOf(preferredWidth, MeasureSpec.getSize(widthMeasureSpec))
        }
        val width = count * moduleWidth(available)
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(preferredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val bars = modules ?: return
        val module = moduleWidth(minOf(width, preferredWidth))
        // По центру, если разметка дала больше, чем занимают модули.
        var x = (width - bars.size * module) / 2
        var i = 0
        while (i < bars.size) {
            var run = 1
            while (i + run < bars.size && bars[i + run] == bars[i]) run++
            if (bars[i]) {
                canvas.drawRect(
                    x.toFloat(), 0f, (x + run * module).toFloat(), height.toFloat(), paint
                )
            }
            x += run * module
            i += run
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
