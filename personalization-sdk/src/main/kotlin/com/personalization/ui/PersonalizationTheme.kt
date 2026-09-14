package com.personalization.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.personalization.R

/**
 * Разрешение токенов дизайн-системы через тему.
 *
 * Компоненты спрашивают значение по ресурсу-умолчанию, а тема при желании его
 * подменяет. Хост переопределяет токены, унаследовав тему от `Theme.Personalization`:
 *
 * ```xml
 * <style name="Theme.MyApp" parent="Theme.MaterialComponents.DayNight">
 *     <item name="personalizationColorBrandPrimary">@color/my_brand</item>
 *     <item name="personalizationFontFamily">@font/inter</item>
 * </style>
 * ```
 *
 * или точечно — обернув контекст в `ContextThemeWrapper`, если разным магазинам
 * нужен разный брендинг в одном приложении.
 *
 * Если атрибут в теме не задан, берётся ресурс по умолчанию, поэтому хосту,
 * которому тема не нужна, делать ничего не надо.
 *
 * Темизуются цвета, радиусы и шрифт. Отступы, тени и кегли остаются ресурсами:
 * это шкала макета, менять её не предполагается.
 */
object PersonalizationTheme {

    private val colorAttributes: Map<Int, Int> = mapOf(
        R.color.personalization_brand_primary to R.attr.personalizationColorBrandPrimary,
        R.color.personalization_semantic_warning to R.attr.personalizationColorSemanticWarning,
        R.color.personalization_background_primary to R.attr.personalizationColorBackgroundPrimary,
        R.color.personalization_background_generic to R.attr.personalizationColorBackgroundGeneric,
        R.color.personalization_background_card to R.attr.personalizationColorBackgroundCard,
        R.color.personalization_background_input to R.attr.personalizationColorBackgroundInput,
        R.color.personalization_background_transparent to R.attr.personalizationColorBackgroundTransparent,
        R.color.personalization_background_input_disabled to R.attr.personalizationColorBackgroundInputDisabled,
        R.color.personalization_button_primary to R.attr.personalizationColorButtonPrimary,
        R.color.personalization_button_primary_focus to R.attr.personalizationColorButtonPrimaryFocus,
        R.color.personalization_button_secondary to R.attr.personalizationColorButtonSecondary,
        R.color.personalization_button_secondary_focus to R.attr.personalizationColorButtonSecondaryFocus,
        R.color.personalization_button_primary_disabled to R.attr.personalizationColorButtonPrimaryDisabled,
        R.color.personalization_button_secondary_disabled to R.attr.personalizationColorButtonSecondaryDisabled,
        R.color.personalization_button_ghost to R.attr.personalizationColorButtonGhost,
        R.color.personalization_line_brand to R.attr.personalizationColorLineBrand,
        R.color.personalization_line_generic to R.attr.personalizationColorLineGeneric,
        R.color.personalization_line_generic_subtle to R.attr.personalizationColorLineGenericSubtle,
        R.color.personalization_line_input to R.attr.personalizationColorLineInput,
        R.color.personalization_line_input_focus to R.attr.personalizationColorLineInputFocus,
        R.color.personalization_text_primary to R.attr.personalizationColorTextPrimary,
        R.color.personalization_text_secondary to R.attr.personalizationColorTextSecondary,
        R.color.personalization_text_hint to R.attr.personalizationColorTextHint,
        R.color.personalization_text_dark_primary to R.attr.personalizationColorTextDarkPrimary,
        R.color.personalization_text_dark_secondary to R.attr.personalizationColorTextDarkSecondary,
        R.color.personalization_text_dark_hint to R.attr.personalizationColorTextDarkHint,
        R.color.personalization_text_light_primary to R.attr.personalizationColorTextLightPrimary,
        R.color.personalization_text_light_secondary to R.attr.personalizationColorTextLightSecondary,
        R.color.personalization_text_light_hint to R.attr.personalizationColorTextLightHint,
        R.color.personalization_text_inverted_primary to R.attr.personalizationColorTextInvertedPrimary,
        R.color.personalization_text_inverted_secondary to R.attr.personalizationColorTextInvertedSecondary,
        R.color.personalization_text_inverted_hint to R.attr.personalizationColorTextInvertedHint,
        R.color.personalization_text_brand to R.attr.personalizationColorTextBrand,
        R.color.personalization_text_link to R.attr.personalizationColorTextLink,
        R.color.personalization_text_link_visited to R.attr.personalizationColorTextLinkVisited,
    )

    private val radiusAttributes: Map<Int, Int> = mapOf(
        R.dimen.personalization_radius_xs to R.attr.personalizationRadiusXs,
        R.dimen.personalization_radius_sm to R.attr.personalizationRadiusSm,
        R.dimen.personalization_radius_md to R.attr.personalizationRadiusMd,
        R.dimen.personalization_radius_lg to R.attr.personalizationRadiusLg,
        R.dimen.personalization_radius_xl to R.attr.personalizationRadiusXl,
        R.dimen.personalization_radius_xl2 to R.attr.personalizationRadiusXl2,
        R.dimen.personalization_radius_xl3 to R.attr.personalizationRadiusXl3,
        R.dimen.personalization_radius_xl4 to R.attr.personalizationRadiusXl4,
        R.dimen.personalization_radius_xl5 to R.attr.personalizationRadiusXl5,
        R.dimen.personalization_radius_xl6 to R.attr.personalizationRadiusXl6,
        R.dimen.personalization_radius_rounded to R.attr.personalizationRadiusRounded,
        R.dimen.personalization_radius_button_lg to R.attr.personalizationRadiusButtonLg,
        R.dimen.personalization_radius_button_md to R.attr.personalizationRadiusButtonMd,
        R.dimen.personalization_radius_button_sm to R.attr.personalizationRadiusButtonSm,
        R.dimen.personalization_radius_segmented_md to R.attr.personalizationRadiusSegmentedMd,
        R.dimen.personalization_radius_segmented_sm to R.attr.personalizationRadiusSegmentedSm,
    )

    /** Цвет токена: из темы, иначе из ресурса. */
    @ColorInt
    fun color(context: Context, @ColorRes fallback: Int): Int {
        val attribute = colorAttributes[fallback] ?: return ContextCompat.getColor(context, fallback)
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attribute, value, true)) {
            return ContextCompat.getColor(context, fallback)
        }
        return if (value.resourceId != 0) {
            ContextCompat.getColor(context, value.resourceId)
        } else {
            value.data
        }
    }

    /** То же, одним цветом без состояний — для тинта иконок. */
    fun colorStateList(context: Context, @ColorRes fallback: Int): ColorStateList =
        ColorStateList.valueOf(color(context, fallback))

    /** Радиус в пикселях: из темы, иначе из ресурса. */
    fun radius(context: Context, @DimenRes fallback: Int): Float {
        val attribute = radiusAttributes[fallback] ?: return context.resources.getDimension(fallback)
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attribute, value, true)) {
            return context.resources.getDimension(fallback)
        }
        return TypedValue.complexToDimension(value.data, context.resources.displayMetrics)
    }

    /**
     * Шрифт дизайн-системы. Inter в SDK не поставляется: по умолчанию системный,
     * хост может подставить свой через `personalizationFontFamily` —
     * и строкой с именем семейства, и ссылкой на `@font/`.
     */
    fun typeface(context: Context, emphasized: Boolean): Typeface? {
        val attribute = if (emphasized) {
            R.attr.personalizationFontFamilyEmphasized
        } else {
            R.attr.personalizationFontFamily
        }
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attribute, value, true)) {
            return systemTypeface(emphasized)
        }
        if (value.resourceId != 0) {
            val font = runCatching { ResourcesCompat.getFont(context, value.resourceId) }.getOrNull()
            if (font != null) return font
        }
        val family = value.string?.toString()
        return if (family.isNullOrEmpty()) {
            systemTypeface(emphasized)
        } else {
            Typeface.create(family, Typeface.NORMAL)
        }
    }

    private fun systemTypeface(emphasized: Boolean): Typeface =
        Typeface.create(if (emphasized) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
}
