package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.ImageViewCompat
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Низ слайда с товарами из кадра: кнопка «Используемые товары» и шторка с каруселью.
 *
 * Источник: Figma, Card/Used Products (238:7108) и Button/Used Products (345:14578).
 * Свёрнуто — строка высотой 64 с отступами 16: по центру кнопка SM Secondary со
 * скруглением Rounded и стрелкой вверх ([showText]). Развёрнуто — подложка
 * Background/Card: карусель карточек (отступы 16/0/16/16, шаг 16) и та же строка
 * с кнопкой [hideText] и стрелкой вниз.
 *
 * В начале строки — кнопка звука видео-слайда ([showSoundButton], [muted]):
 * в макете она живёт здесь же.
 */
@InternalPersonalizationUiApi
class PersonalizationStoryUsedProducts @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val carousel = PersonalizationProductsCarousel(context)

    private val row = FrameLayout(context)
    private val toggle = PersonalizationButton(context)
    private val sound = AppCompatImageView(context)

    var products: List<PersonalizationProduct>
        get() = carousel.products
        set(value) {
            carousel.products = value
            applyState()
        }

    var imageLoader: ((ImageView, PersonalizationProduct) -> Unit)?
        get() = carousel.imageLoader
        set(value) {
            carousel.imageLoader = value
        }

    var onProductClick: ((PersonalizationProduct) -> Unit)?
        get() = carousel.onProductClick
        set(value) {
            carousel.onProductClick = value
        }

    var showText: CharSequence = "Used products"
        set(value) {
            field = value
            applyState()
        }

    var hideText: CharSequence = "Hide products"
        set(value) {
            field = value
            applyState()
        }

    var expanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyState()
            onExpandedChange?.invoke(value)
        }

    var onExpandedChange: ((Boolean) -> Unit)? = null

    var showSoundButton: Boolean = false
        set(value) {
            field = value
            applyState()
        }

    var muted: Boolean = false
        set(value) {
            field = value
            applyState()
        }

    var onSoundClick: (() -> Unit)? = null

    /** Описание кнопки звука для TalkBack: звук включён — [soundOnDescription], выключен — [soundOffDescription]. */
    var soundOnDescription: CharSequence = "Sound on"
    var soundOffDescription: CharSequence = "Sound off"

    init {
        orientation = VERTICAL

        carousel.setPadding(dp(16), dp(16), 0, dp(16))
        carousel.clipToPadding = false
        addView(carousel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        toggle.size = PersonalizationButton.Size.SM
        toggle.buttonView = PersonalizationButton.ButtonView.SECONDARY
        toggle.rounded = true
        toggle.setOnClickListener { expanded = !expanded }
        row.addView(
            toggle,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        ImageViewCompat.setImageTintList(
            sound,
            PersonalizationTheme.colorStateList(context, R.color.personalization_text_secondary)
        )
        sound.setOnClickListener { onSoundClick?.invoke() }
        row.addView(
            sound,
            FrameLayout.LayoutParams(dp(SOUND_DP), dp(SOUND_DP), Gravity.START or Gravity.CENTER_VERTICAL)
        )
        row.setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_DP)))

        applyState()
    }

    private fun applyState() {
        val hasProducts = carousel.products.isNotEmpty()
        val open = expanded && hasProducts
        carousel.visibility = if (open) View.VISIBLE else View.GONE
        setBackgroundColor(
            if (open) PersonalizationTheme.color(context, R.color.personalization_background_card) else 0
        )

        toggle.visibility = if (hasProducts) View.VISIBLE else View.GONE
        toggle.text = if (open) hideText else showText
        toggle.iconEnd = if (open) R.drawable.personalization_ic_angle_down else R.drawable.personalization_ic_angle_up

        sound.visibility = if (showSoundButton) View.VISIBLE else View.GONE
        sound.setImageResource(if (muted) R.drawable.personalization_ic_speaker_off else R.drawable.personalization_ic_speaker)
        // В макете выключенный звук — иконка на 65%, включённый — непрозрачная.
        sound.alpha = if (muted) MUTED_ALPHA else 1f
        sound.contentDescription = if (muted) soundOffDescription else soundOnDescription
        // Состояние переключателя для TalkBack: звук включён — кнопка выбрана (F1).
        sound.isSelected = !muted
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val ROW_DP = 64
        const val SOUND_DP = 32
        const val MUTED_ALPHA = 0.65f
    }
}
