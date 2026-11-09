package com.personalization.ui.widgets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
                textSizeSp = element.fontSize.toFloat()
                lineHeightSp = StoriesMapping.lineHeightSp(element)
                blockColor = StoriesMapping.textBlockColor(element)
                textColorOverride = StoriesMapping.parseColor(element.textColor)
                align = StoriesMapping.textAlign(element)
                bold = element.isBold
                italic = element.isItalic
                fontFamily = element.fontType.takeIf { it.isNotEmpty() }
            }
            StoriesMapping.textPromocode(element)?.let { code ->
                block.setOnClickListener { copy(it, code, host) }
            }
            layer.add(block, element.offset)
        }
        return layer
    }

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
 * вебе, где оба лежат в контейнере слайда с `top: y_offset%`. Верх — `y_offset` процентов ВСЕЙ
 * высоты слайда; затем элемент сдвигается, чтобы целиком лечь в область между шапкой (прогресс,
 * «Поделиться», крестик) и кнопками или «товарами»: выше области — к её верху, ниже — к низу.
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
        val wanted = slideHeight * (offset ?: 0f).coerceIn(0f, 100f) / 100f - areaTop
        return wanted.coerceAtMost((areaHeight - height).toFloat()).coerceAtLeast(0f).roundToInt()
    }

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
