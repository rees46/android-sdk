package com.personalization.ui.components

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Инап-попап: карточка с картинкой, заголовком, текстом и кнопками.
 *
 * Источник: Figma Mobile SDK UI Kit, страница InAppPopup / Modal (1:33),
 * компонент-сеты In App Popup/Modal (469:2767) и In App Popup/Fullscreen (469:2835).
 * У обоих четыре вида: картинка сверху, картинка фоном, только текст, иконка.
 *
 * Позицию на экране (верх/центр/низ/во весь экран) компонент не выбирает — это дело
 * того, кто его показывает; здесь только [presentation], от которой зависят метрики:
 * у модалки скругление 24 и отступы 20, у полноэкранной скруглений нет и отступы 24,
 * а кегли на ступень крупнее.
 *
 * Крестик стоит **всегда** и живёт в самом попапе, а не в контейнере картинки: в макете
 * виды «только текст» и «иконка» картинки не имеют, но крестик у них нарисован.
 * Текстовая кнопка закрытия ([closeText]) в макете не нарисована — её даёт админка
 * отдельным тумблером рядом с кнопкой действия, поэтому она здесь вторичной кнопкой
 * под основной. Пустой текст — кнопки нет, остаётся один крестик.
 *
 * Отступ 20 у модалки и интерлиньяж 48 у полноэкранного заголовка вне шкал кита —
 * взяты из макета как есть.
 */
@InternalPersonalizationUiApi
class PersonalizationInAppPopup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Вид попапа: чем занято место над текстом. */
    enum class ContentView { IMAGE, IMAGE_BACKGROUND, TEXT, ICON }

    /** Модалка карточкой или во весь экран — от этого зависят метрики. */
    enum class Presentation { MODAL, FULLSCREEN }

    private val backgroundImage = AppCompatImageView(context)
    private val topImage = AppCompatImageView(context)
    private val iconView = AppCompatImageView(context)
    private val titleView = AppCompatTextView(context)
    private val textView = AppCompatTextView(context)
    private val actionButton = PersonalizationButton(context)
    private val closeButton = PersonalizationButton(context)
    private val closeIcon = PersonalizationButton(context)

    private val column = LinearLayout(context)
    private val body = LinearLayout(context)
    private val content = LinearLayout(context)
    private val textBlock = LinearLayout(context)
    private val buttons = LinearLayout(context)

    var contentView: ContentView = ContentView.IMAGE
        set(value) {
            field = value
            rebuild()
        }

    var presentation: Presentation = Presentation.MODAL
        set(value) {
            field = value
            rebuild()
        }

    var title: CharSequence?
        get() = titleView.text
        set(value) {
            titleView.text = value
            applyVisibility()
        }

    var text: CharSequence?
        get() = textView.text
        set(value) {
            textView.text = value
            applyVisibility()
        }

    /** Подпись кнопки действия. Пусто — кнопки нет. */
    var actionText: CharSequence? = null
        set(value) {
            field = value
            actionButton.text = value
            applyVisibility()
        }

    /** Подпись кнопки закрытия. Пусто — остаётся только крестик. */
    var closeText: CharSequence? = null
        set(value) {
            field = value
            closeButton.text = value
            applyVisibility()
        }

    /** Иконка вида [ContentView.ICON]. */
    var icon: Int? = null
        set(value) {
            field = value
            value?.let(iconView::setImageResource)
        }

    /** Картинку грузит хост — кит не тянет сеть. */
    var imageLoader: ((ImageView) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(if (contentView == ContentView.IMAGE_BACKGROUND) backgroundImage else topImage)
        }

    var onAction: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    init {
        backgroundImage.scaleType = ImageView.ScaleType.CENTER_CROP
        topImage.scaleType = ImageView.ScaleType.CENTER_CROP
        iconView.adjustViewBounds = true

        titleView.includeFontPadding = false
        titleView.typeface = PersonalizationTheme.typeface(context, emphasized = true)
        textView.includeFontPadding = false
        textView.typeface = PersonalizationTheme.typeface(context, emphasized = false)

        actionButton.size = PersonalizationButton.Size.LG
        actionButton.buttonView = PersonalizationButton.ButtonView.PRIMARY
        actionButton.setOnClickListener { onAction?.invoke() }

        closeButton.size = PersonalizationButton.Size.LG
        closeButton.buttonView = PersonalizationButton.ButtonView.SECONDARY
        closeButton.setOnClickListener { onClose?.invoke() }

        closeIcon.size = PersonalizationButton.Size.MD
        closeIcon.buttonView = PersonalizationButton.ButtonView.SECONDARY
        closeIcon.iconStart = R.drawable.personalization_ic_cross_large
        closeIcon.setOnClickListener { onClose?.invoke() }

        column.orientation = LinearLayout.VERTICAL
        body.orientation = LinearLayout.VERTICAL
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER
        textBlock.orientation = LinearLayout.VERTICAL
        buttons.orientation = LinearLayout.VERTICAL

        // Обрезка по скруглению и тень — API 21. На 19–20 модалка остаётся без них: углы
        // картинки не скругляются, но попап цел.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) clipToOutline = true
        rebuild()
    }

    private fun rebuild() {
        removeAllViews()
        column.removeAllViews()
        body.removeAllViews()
        content.removeAllViews()
        textBlock.removeAllViews()
        buttons.removeAllViews()

        textBlock.gravity = Gravity.NO_GRAVITY
        val modal = presentation == Presentation.MODAL
        val pad = dpToPx(if (modal) 20 else 24)
        val gapSection = dpToPx(if (modal) 20 else 24)
        val overImage = contentView == ContentView.IMAGE_BACKGROUND

        applyTypography(modal)
        applyTextColors(overImage)
        applyShape(modal)
        // У видов без картинки текст в макете по центру, с картинкой — по левому краю.
        val centered = contentView == ContentView.TEXT || contentView == ContentView.ICON
        val align = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
        titleView.gravity = align
        textView.gravity = align

        textBlock.addView(titleView, wrap())
        textBlock.addView(textView, wrap().also { it.topMargin = gapText() })

        buttons.addView(actionButton, match())
        buttons.addView(closeButton, match().also { it.topMargin = gapText() })

        when (contentView) {
            ContentView.IMAGE -> {
                // Картинка занимает всё, что остаётся над карточкой с текстом.
                column.addView(topImage, LinearLayout.LayoutParams(MATCH, 0, 1f))
                body.setPadding(pad, pad, pad, pad)
                body.addView(textBlock, match())
                body.addView(buttons, match().also { it.topMargin = dpToPx(if (modal) 16 else 24) })
                column.addView(body, match())
            }

            ContentView.IMAGE_BACKGROUND -> {
                addView(backgroundImage, LayoutParams(MATCH, MATCH))
                column.setPadding(pad, pad, pad, pad)
                // Здесь крестик не накладкой, а первым в колонке: в макете он занимает свою
                // строку, иначе заголовок заезжает под него.
                column.addView(
                    closeIcon,
                    LinearLayout.LayoutParams(WRAP, WRAP).also { it.gravity = Gravity.END }
                )
                // Текст прижат к низу колонки, над кнопками.
                textBlock.gravity = Gravity.BOTTOM
                column.addView(textBlock, match().also { it.weight = 1f })
                column.addView(buttons, match().also { it.topMargin = gapSection })
            }

            ContentView.TEXT -> {
                column.setPadding(pad, pad, pad, pad)
                textBlock.gravity = Gravity.CENTER_VERTICAL
                column.addView(textBlock, match().also { it.weight = 1f })
                column.addView(buttons, match().also { it.topMargin = gapSection })
            }

            ContentView.ICON -> {
                column.setPadding(pad, pad, pad, pad)
                val side = dpToPx(if (modal) 100 else 120)
                content.addView(iconView, LinearLayout.LayoutParams(side, side))
                content.addView(textBlock, match().also { it.topMargin = dpToPx(24) })
                column.addView(content, match().also { it.weight = 1f })
                column.addView(buttons, match().also { it.topMargin = gapSection })
            }
        }

        addView(column, LayoutParams(MATCH, MATCH))
        applyVisibility()

        // Крестик всегда на месте — у видов без картинки тоже. У фона-картинки он уже
        // стоит в колонке, здесь накладкой поверх содержимого.
        if (contentView != ContentView.IMAGE_BACKGROUND) {
            addView(
                closeIcon,
                LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).also {
                    it.topMargin = pad
                    it.marginEnd = pad
                }
            )
        }

        // Поверх картинки контролы берут инвертированную палитру, иначе тёмная подпись
        // вторичной кнопки тонет в фотографии.
        closeIcon.onDark = overImage
        closeButton.onDark = overImage

        imageLoader?.invoke(if (overImage) backgroundImage else topImage)
    }

    private fun applyTypography(modal: Boolean) {
        val titleSize = if (modal) R.dimen.personalization_font_size_xl3 else R.dimen.personalization_font_size_xl4
        val textSize = if (modal) R.dimen.personalization_font_size_lg else R.dimen.personalization_font_size_xl2
        val titleLine = if (modal) resources.getDimensionPixelSize(R.dimen.personalization_line_height_xl3) else dpToPx(48)
        val textLine = if (modal) R.dimen.personalization_line_height_lg else R.dimen.personalization_line_height_xl2

        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(titleSize))
        TextViewCompat.setLineHeight(titleView, titleLine)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(textSize))
        TextViewCompat.setLineHeight(textView, resources.getDimensionPixelSize(textLine))
    }

    /** Поверх картинки текст светлый — в макете это отдельные цвета, не прозрачность. */
    private fun applyTextColors(overImage: Boolean) {
        val titleColor = if (overImage) R.color.personalization_text_light_primary
        else R.color.personalization_text_primary
        val textColor = if (overImage) R.color.personalization_text_light_secondary
        else R.color.personalization_text_secondary
        titleView.setTextColor(PersonalizationTheme.color(context, titleColor))
        textView.setTextColor(PersonalizationTheme.color(context, textColor))
    }

    private fun applyShape(modal: Boolean) {
        val radius = if (modal) {
            resources.getDimension(R.dimen.personalization_radius_xl6)
        } else {
            0f
        }
        // Background/Modal, а не Card: в светлой они совпадают (белый), в тёмной у модалки
        // своя ступень #333333. В Figma компонент привязан к Card, но значение Card в ките
        // отстало от файла (там уже #FFFFFF) — ресинк цветов отдельной задачей.
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(PersonalizationTheme.color(context, R.color.personalization_background_modal))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            elevation = if (modal) resources.getDimension(R.dimen.personalization_elevation_e3) else 0f
        }
    }

    /**
     * Пустое — не показывается: заголовок, текст и обе кнопки. Сеттеры при создании не
     * срабатывают, поэтому это же зовёт [rebuild] — иначе попап без подписей рисовал бы пустые
     * кнопки. Отступ между соседями снимается, когда верхнего нет, а блок кнопок без единой
     * кнопки прячется целиком — иначе его отступ оставлял бы пустую полосу снизу.
     */
    private fun applyVisibility() {
        val hasTitle = !titleView.text.isNullOrEmpty()
        val hasAction = !actionText.isNullOrEmpty()
        val hasClose = !closeText.isNullOrEmpty()
        titleView.isVisible = hasTitle
        textView.isVisible = !textView.text.isNullOrEmpty()
        textView.setTopMargin(if (hasTitle) gapText() else 0)
        actionButton.isVisible = hasAction
        closeButton.isVisible = hasClose
        closeButton.setTopMargin(if (hasAction) gapText() else 0)
        buttons.isVisible = hasAction || hasClose
    }

    private fun View.setTopMargin(value: Int) {
        val params = layoutParams as? MarginLayoutParams ?: return
        if (params.topMargin == value) return
        params.topMargin = value
        layoutParams = params
    }

    private fun gapText(): Int =
        resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)

    private fun match(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, WRAP)

    private fun wrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, WRAP)

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH = LayoutParams.MATCH_PARENT
        const val WRAP = LayoutParams.WRAP_CONTENT
    }
}
