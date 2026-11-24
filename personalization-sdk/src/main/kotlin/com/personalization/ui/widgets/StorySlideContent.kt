package com.personalization.ui.widgets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.personalization.R
import com.personalization.stories.models.elements.HeaderElement
import com.personalization.ui.PersonalizationTheme
import com.personalization.Product
import com.personalization.stories.models.Slide
import com.personalization.stories.models.elements.ProductElement
import com.personalization.ui.components.PersonalizationStoryCard
import com.personalization.ui.components.PersonalizationStoryTextBlock
import com.personalization.ui.components.PersonalizationToast
import kotlin.math.roundToInt

/**
 * Карточка товарного слайда (C8): бренд, название, фото, цена. Кнопки рисует слайд —
 * все кнопки админки, а не одну кнопку карточки. Фото не нажимается (I1): тап по нему —
 * тап по слайду, листание по зонам; товар открывают только кнопки (I2).
 */
internal object StoryProductCard {

    /** [slidePromo] — промокод текстового блока слайда, если у товара своего нет (H2). */
    fun build(context: Context, element: ProductElement, product: Product, host: StorySlideHost, slidePromo: String? = null): View {
        val price = StoriesMapping.price(product, slidePromo)
        return PersonalizationStoryCard(context).apply {
            title = product.brand.takeIf { it.isNotEmpty() }
            subtitle = product.name
            if (product.image.isNotEmpty()) host.loadImage(imageView, product.image)
            imageView.contentDescription = product.name
            this.price.type = price.type
            this.price.price = price.price
            this.price.oldPrice = price.oldPrice
            this.price.discount = price.discount
            this.price.promoCode = price.promoCode
            // Подпись над кодом — из админки, без неё — текст хоста (C9).
            this.price.promoCaption = StoriesMapping.promoCaption(element, host.texts.promoCaption)
            this.price.copiedText = host.texts.promoCopiedText
            this.price.onPromoCopied = { host.onPromoCopied() }
            buttonText = null
        }
    }
}

/**
 * Шапка слайда (`header`, API v3), как в веб-SDK: иконка, заголовок и подзаголовок; нажатие —
 * `click` и переход по ссылке ([StorySlideHost.onHeader]). Вид — токены кита, одинаковый на всех
 * платформах: круглая иконка 40, через 12 заголовок SM (body-small) полужирным основного цвета,
 * подзаголовок XS (caption) вторичным; пустые части не рисуются. Стоит под рядом навигации.
 */
internal object StoryHeader {

    private const val ICON_DP = 40

    fun build(context: Context, header: HeaderElement, host: StorySlideHost): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val gap = context.resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        if (header.icon.isNotBlank()) {
            val icon = AppCompatImageView(context)
            icon.scaleType = ImageView.ScaleType.CENTER_CROP
            icon.outlineProvider = ViewOutlineProvider.BACKGROUND
            icon.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(PersonalizationTheme.color(context, R.color.personalization_neutral_50))
            }
            icon.clipToOutline = true
            icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val size = (ICON_DP * context.resources.displayMetrics.density).roundToInt()
            row.addView(icon, LinearLayout.LayoutParams(size, size).apply { marginEnd = gap })
            host.loadImage(icon, header.icon.trim())
        }
        val texts = LinearLayout(context)
        texts.orientation = LinearLayout.VERTICAL
        header.title.takeIf { it.isNotBlank() }?.let { title ->
            texts.addView(TextView(context).apply {
                text = title
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                PollUi.style(this, R.dimen.personalization_font_size_sm, R.dimen.personalization_line_height_sm, PollWeight.SEMIBOLD,
                    PersonalizationTheme.color(context, R.color.personalization_text_primary))
            })
        }
        header.subtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
            texts.addView(TextView(context).apply {
                text = subtitle
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                PollUi.style(this, R.dimen.personalization_font_size_xs, R.dimen.personalization_line_height_xs, PollWeight.REGULAR,
                    PersonalizationTheme.color(context, R.color.personalization_text_secondary))
            })
        }
        if (texts.childCount > 0) row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener { host.onHeader(header) }
        return row
    }
}

/**
 * Текстовые блоки медиа-слайда — на высоте `y_offset` по [StorySlidePlacement], как карточка опроса:
 * от всей высоты слайда, внутри области между шапкой и кнопками. Блок с промокодом по тапу копирует
 * код и показывает «Copied» сверху (C12).
 */
internal object StoryTextBlocks {

    fun build(context: Context, slide: Slide, host: StorySlideHost): View {
        val layer = StoryTextBlockLayer(context)
        StoriesMapping.textBlocks(slide).forEach { element ->
            val block = PersonalizationStoryTextBlock(context).apply {
                text = element.textInput
                if (host.v3) {
                    // R4, одно правило на всех платформах: без `font_size` — основной кегль кита.
                    val size = element.ownFontSize ?: bodyTextSp(context)
                    textSizeSp = size
                    lineHeightSp = if (element.textLineSpacing > 0) (size * element.textLineSpacing).toFloat() else null
                    blockColor = StoriesMapping.textBlockColorV3(element)
                    bold = element.boldV3
                    italic = element.italicV3
                    fontFamily = element.fontType.trim().lowercase().takeIf { it.isNotEmpty() }
                } else {
                    textSizeSp = element.fontSize.toFloat()
                    lineHeightSp = StoriesMapping.lineHeightSp(element)
                    blockColor = StoriesMapping.textBlockColor(element)
                    bold = element.isBold
                    italic = element.isItalic
                    fontFamily = element.fontType.takeIf { it.isNotEmpty() }
                }
                textColorOverride = if (host.v3) StoriesColor.parse(element.textColor) else StoriesMapping.parseColor(element.textColor)
                align = StoriesMapping.textAlign(element)
            }
            StoriesMapping.textPromocode(element)?.let { code ->
                block.setOnClickListener { copy(it, code, host) }
            }
            layer.add(block, element.offset)
        }
        return layer
    }

    /** Основной кегль кита (`font_size_base`) в sp. */
    @Suppress("DEPRECATION")
    fun bodyTextSp(context: Context): Float =
        context.resources.getDimension(R.dimen.personalization_font_size_base) / context.resources.displayMetrics.scaledDensity

    private fun copy(anchor: View, code: String, host: StorySlideHost) {
        val clipboard = anchor.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(host.texts.promoCaption, code))
        PersonalizationToast.show(anchor, host.texts.promoCopiedText, PersonalizationToast.Position.TOP)
        host.onPromoCopied()
    }
}

/** Текстовые блоки слайда: каждый во всю ширину области, верх — по [StorySlidePlacement]. */
internal class StoryTextBlockLayer(context: Context) : FrameLayout(context) {

    private val blocks = ArrayList<Pair<View, Float?>>()

    /** [offset] — `y_offset` блока; `null` — сверху. */
    fun add(block: View, offset: Float?) {
        blocks += block to offset
        addView(block, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val (areaTop, slideHeight) = StorySlidePlacement.frame(this)
        blocks.forEach { (block, offset) ->
            val y = StorySlidePlacement.top(offset, slideHeight, areaTop, bottom - top, block.measuredHeight)
            block.layout(block.left, y, block.right, y + block.measuredHeight)
        }
    }
}

/**
 * Высота элемента слайда V2 по `y_offset` — одна для текстовых блоков и карточек опросов, как в
 * вебе, где оба лежат в контейнере слайда с `top: y_offset%`: `y_offset` процентов ВСЕЙ высоты
 * слайда ([offsetY]). Текстовый блок затем сдвигается, чтобы целиком лечь в область между шапкой
 * (прогресс, «Поделиться», крестик) и кнопками или «товарами»: выше области — к её верху, ниже — к
 * низу ([top]); карточку опроса ставит [StoryPollLayer.cardTop].
 */
internal object StorySlidePlacement {

    /** `y_offset` ленты: число или число строкой, зажатое в 0…100; `null` и не число — `null`. */
    fun offset(value: Any?): Float? = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }?.takeIf { !it.isNaN() && !it.isInfinite() }?.toFloat()?.coerceIn(0f, 100f)

    /**
     * Верх элемента высотой [height] в области высотой [areaHeight]; верх области — [areaTop] от
     * верха слайда высотой [slideHeight]. [offset] `null` — 0. Элемент выше области — её верх.
     */
    fun top(offset: Float?, slideHeight: Int, areaTop: Int, areaHeight: Int, height: Int): Int {
        val wanted = offsetY(offset, slideHeight) - areaTop
        return wanted.coerceAtMost((areaHeight - height).toFloat()).coerceAtLeast(0f).roundToInt()
    }

    /** Верх элемента по `y_offset` [offset] от верха слайда высотой [slideHeight]: проценты всей высоты, `null` — 0. */
    fun offsetY(offset: Float?, slideHeight: Int): Float = slideHeight * (offset ?: 0f).coerceIn(0f, 100f) / 100f

    /** Верх [area] в слайде ([StorySlideView]) и высота слайда; [area] не на слайде — она сама. */
    fun frame(area: View): Pair<Int, Int> {
        var offset = 0
        var view: View = area
        while (true) {
            val parent = view.parent as? View ?: return 0 to area.height
            offset += view.top
            if (parent is StorySlideView) return offset to parent.height
            view = parent
        }
    }
}
