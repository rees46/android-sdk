package com.personalization.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.ImageViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Метка «в избранном»: звезда в круге цвета Semantic/Warning, 24x24.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Badge, символ Favorites (391:17117).
 */
@InternalPersonalizationUiApi
class PersonalizationFavoritesBadge @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        val inset = resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)
        setPadding(inset, inset, inset, inset)
        setImageResource(R.drawable.personalization_ic_star_fill)
        ImageViewCompat.setImageTintList(
            this,
            PersonalizationTheme.colorStateList(context, R.color.personalization_text_light_primary)
        )
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(PersonalizationTheme.color(context, R.color.personalization_semantic_warning))
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = (SIDE_DP * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(side, widthMeasureSpec),
            resolveSize(side, heightMeasureSpec)
        )
    }

    private companion object {
        /** Звезда 16 плюс поле 4 с каждой стороны. */
        const val SIDE_DP = 24
    }
}
