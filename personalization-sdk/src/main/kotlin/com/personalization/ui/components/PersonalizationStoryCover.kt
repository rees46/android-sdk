package com.personalization.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Обложка сторис в ленте: кольцо и круглая картинка.
 *
 * Источник: Figma Mobile SDK UI Kit, Story/Cover (393:17570) и Progress/Circular (391:17122):
 * круг 88 с кольцом 4 внутрь, картинка 76 с зазором 2 от кольца. Состояния — [State]:
 * закреплённая (кольцо Semantic/Warning и звезда Favorites справа внизу), обычная
 * (Button/Primary), просмотренная (Semantic/Neutral Subtle) и скелетон загрузки.
 *
 * Кольцо делится на сегменты по числу слайдов ([segments]); просмотренные слайды
 * ([seenSegments], по индексу) — цветом Button/Primary Disabled, как в вариантах
 * Progress/Circular.
 *
 * Картинку кладёт хост в [imageView] — кит ничего не качает.
 */
@InternalPersonalizationUiApi
class PersonalizationStoryCover @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class State { PINNED, DEFAULT, VIEWED, SKELETON }

    var state: State = State.DEFAULT
        set(value) {
            field = value
            applyState()
        }

    /** Сколько сегментов у кольца — обычно число слайдов. 1 — сплошное кольцо. */
    var segments: Int = 1
        set(value) {
            field = value.coerceAtLeast(1)
            invalidate()
        }

    /** Индексы просмотренных сегментов: сегмент i отмечен, если виден слайд i. */
    var seenSegments: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    /** Диаметр обложки в пикселях; толщина кольца и зазор не масштабируются. */
    var coverSize: Int = dp(DEFAULT_SIZE_DP)
        set(value) {
            field = value
            requestLayout()
        }

    /** Круглая картинка обложки. */
    val imageView: ImageView = StoryCircleImageView(context)

    private val favorites = FrameLayout(context)
    private val star = AppCompatImageView(context)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(RING_DP).toFloat()
    }
    private val ringBounds = RectF()

    init {
        setWillNotDraw(false)
        clipChildren = false

        addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        star.setImageResource(R.drawable.personalization_ic_star_fill)
        ImageViewCompat.setImageTintList(
            star,
            PersonalizationTheme.colorStateList(context, R.color.personalization_text_light_primary)
        )
        favorites.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(PersonalizationTheme.color(context, R.color.personalization_semantic_warning))
        }
        favorites.addView(star, LayoutParams(dp(STAR_DP), dp(STAR_DP), Gravity.CENTER))
        addView(favorites, LayoutParams(dp(FAVORITES_DP), dp(FAVORITES_DP), Gravity.BOTTOM or Gravity.END))

        applyState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val inset = dp(RING_DP + GAP_DP)
        val image = MeasureSpec.makeMeasureSpec(coverSize - inset * 2, MeasureSpec.EXACTLY)
        imageView.measure(image, image)
        val badge = MeasureSpec.makeMeasureSpec(dp(FAVORITES_DP), MeasureSpec.EXACTLY)
        favorites.measure(badge, badge)
        setMeasuredDimension(coverSize, coverSize)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val inset = dp(RING_DP + GAP_DP)
        imageView.layout(inset, inset, coverSize - inset, coverSize - inset)
        // В макете звезда стоит в 62,62 при обложке 88 — прижата к правому нижнему углу
        // с отступом 2 от края.
        val badgeEnd = coverSize - dp(FAVORITES_INSET_DP)
        favorites.layout(badgeEnd - dp(FAVORITES_DP), badgeEnd - dp(FAVORITES_DP), badgeEnd, badgeEnd)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val half = ringPaint.strokeWidth / 2f
        ringBounds.set(half, half, width - half, height - half)

        if (state == State.SKELETON) {
            ringPaint.shader = StorySupport.skeletonShader(context, width.toFloat(), height.toFloat())
            canvas.drawOval(ringBounds, ringPaint)
            ringPaint.shader = null
            return
        }

        val main = PersonalizationTheme.color(context, ringColorRes())
        if (segments <= 1 || state == State.VIEWED) {
            ringPaint.color = main
            canvas.drawOval(ringBounds, ringPaint)
            return
        }

        val seen = PersonalizationTheme.color(context, R.color.personalization_button_primary_disabled)
        val sweep = 360f / segments
        val gap = SEGMENT_GAP_DEGREES.coerceAtMost(sweep / 3f)
        for (index in 0 until segments) {
            ringPaint.color = if (index in seenSegments) seen else main
            canvas.drawArc(ringBounds, START_ANGLE + index * sweep + gap / 2f, sweep - gap, false, ringPaint)
        }
    }

    private fun ringColorRes(): Int = when (state) {
        State.PINNED -> R.color.personalization_semantic_warning
        State.VIEWED -> R.color.personalization_semantic_neutral_subtle
        else -> R.color.personalization_button_primary
    }

    private fun applyState() {
        val skeleton = state == State.SKELETON
        imageView.background = if (skeleton) StorySupport.skeletonDrawable(context, GradientDrawable.OVAL) else null
        imageView.visibility = View.VISIBLE
        if (skeleton) imageView.setImageDrawable(null)
        favorites.visibility = if (state == State.PINNED) View.VISIBLE else View.GONE
        invalidate()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val DEFAULT_SIZE_DP = 88
        const val RING_DP = 4
        const val GAP_DP = 2
        const val FAVORITES_DP = 24
        const val FAVORITES_INSET_DP = 2
        const val STAR_DP = 16
        const val START_ANGLE = -90f
        const val SEGMENT_GAP_DEGREES = 8f
    }
}

/**
 * Превью сторис: обложка и подпись под ней.
 *
 * Источник: Figma, Story/Preview (391:17082): колонка шириной с обложку, шаг 8,
 * подпись Regular 12/16 по центру цветом Text/Primary — до двух строк с многоточием
 * в конце, как у старых сторис. У скелетона вместо подписи — полоска 64×8 со скруглением 5.
 */
@InternalPersonalizationUiApi
class PersonalizationStoryPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val cover = PersonalizationStoryCover(context)

    private val label = AppCompatTextView(context)
    private val skeletonLabel = View(context)

    var title: CharSequence?
        get() = label.text
        set(value) {
            label.text = value
        }

    var state: PersonalizationStoryCover.State
        get() = cover.state
        set(value) {
            cover.state = value
            applyState()
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        addView(cover, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        label.includeFontPadding = false
        label.gravity = Gravity.CENTER
        label.maxLines = LABEL_MAX_LINES
        label.ellipsize = TextUtils.TruncateAt.END
        label.typeface = PersonalizationTheme.typeface(context, emphasized = false)
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.personalization_font_size_xs))
        TextViewCompat.setLineHeight(label, resources.getDimensionPixelSize(R.dimen.personalization_line_height_xs))
        label.setLetterSpacingCompat(LABEL_LETTER_SPACING_EM)
        label.setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_primary))
        addView(
            label,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
            }
        )

        skeletonLabel.background = StorySupport.skeletonDrawable(context, GradientDrawable.RECTANGLE, dp(5).toFloat())
        addView(
            skeletonLabel,
            LayoutParams(dp(64), dp(8)).apply {
                // Полоска 8 в строке 16: по 4 сверху и снизу.
                topMargin = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md) + dp(4)
                bottomMargin = dp(4)
            }
        )
        applyState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Ширина превью — ширина обложки, длинная подпись режется многоточием на второй строке.
        super.onMeasure(MeasureSpec.makeMeasureSpec(cover.coverSize, MeasureSpec.EXACTLY), heightMeasureSpec)
    }

    private fun applyState() {
        val skeleton = cover.state == PersonalizationStoryCover.State.SKELETON
        label.visibility = if (skeleton) View.GONE else View.VISIBLE
        skeletonLabel.visibility = if (skeleton) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        /** Трекинг в макете 0.05 — в процентах кегля, то есть почти ноль; в em так же. */
        const val LABEL_LETTER_SPACING_EM = 0.0005f
        const val LABEL_MAX_LINES = 2
    }
}

/** Сторис для ленты: всё, что нужно обложке и подписи. */
@InternalPersonalizationUiApi
data class PersonalizationStoryItem(
    val id: String,
    val title: CharSequence,
    val imageUrl: String? = null,
    val state: PersonalizationStoryCover.State = PersonalizationStoryCover.State.DEFAULT,
    val segments: Int = 1,
    /** Индексы просмотренных слайдов — отмечаются на кольце. */
    val seenSegments: Set<Int> = emptySet()
)

/**
 * Лента сторис — горизонтальный ряд превью.
 *
 * Источник: Figma, Card/Story View=Stories Block (238:6890): превью с шагом 16,
 * отступы 16. Пока данных нет — [showSkeleton].
 */
@InternalPersonalizationUiApi
class PersonalizationStoriesBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val row = LinearLayout(context)

    var items: List<PersonalizationStoryItem> = emptyList()
        set(value) {
            field = value
            render()
        }

    /** Хост кладёт обложку в `ImageView` своим загрузчиком. */
    var imageLoader: ((ImageView, String) -> Unit)? = null
        set(value) {
            field = value
            render()
        }

    var onItemClick: ((index: Int, item: PersonalizationStoryItem) -> Unit)? = null

    /** Диаметр обложек в пикселях. */
    var coverSize: Int = (88 * resources.displayMetrics.density).toInt()
        set(value) {
            field = value
            render()
        }

    private var skeletonCount = 0

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        val padding = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
        setPadding(padding, padding, padding, padding)
        row.orientation = LinearLayout.HORIZONTAL
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    /** Скелетон загрузки: [count] пустых превью. Сбрасывается установкой [items]. */
    fun showSkeleton(count: Int = 4) {
        skeletonCount = count
        row.removeAllViews()
        repeat(count) { index ->
            row.addView(
                PersonalizationStoryPreview(context).apply {
                    cover.coverSize = coverSize
                    state = PersonalizationStoryCover.State.SKELETON
                },
                itemParams(index)
            )
        }
    }

    private fun render() {
        skeletonCount = 0
        row.removeAllViews()
        items.forEachIndexed { index, item ->
            val preview = PersonalizationStoryPreview(context).apply {
                cover.coverSize = coverSize
                title = item.title
                state = item.state
                cover.segments = item.segments
                cover.seenSegments = item.seenSegments
                contentDescription = item.title
                setOnClickListener { onItemClick?.invoke(index, item) }
            }
            item.imageUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                imageLoader?.invoke(preview.cover.imageView, url)
            }
            row.addView(preview, itemParams(index))
        }
    }

    private fun itemParams(index: Int) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            if (index > 0) marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
        }
}
