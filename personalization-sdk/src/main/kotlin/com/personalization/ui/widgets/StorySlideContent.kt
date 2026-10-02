package com.personalization.ui.widgets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.personalization.Product
import com.personalization.stories.models.Slide
import com.personalization.stories.models.elements.ProductElement
import com.personalization.ui.components.PersonalizationStoryCard
import com.personalization.ui.components.PersonalizationStoryTextBlock
import com.personalization.ui.components.PersonalizationToast

/**
 * Карточка товарного слайда (C8): бренд, название, фото, цена. Кнопки рисует слайд —
 * все кнопки админки, а не одну кнопку карточки.
 */
internal object StoryProductCard {

    fun build(context: Context, element: ProductElement, product: Product, host: StorySlideHost): View {
        val price = StoriesMapping.price(product)
        return PersonalizationStoryCard(context).apply {
            title = product.brand.takeIf { it.isNotEmpty() }
            subtitle = product.name
            if (product.image.isNotEmpty()) host.loadImage(imageView, product.image)
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
 * Текстовые блоки медиа-слайда — на своей высоте (`y_offset`, проценты), как их расставила
 * админка. Блок с промокодом по тапу копирует код и показывает «Copied» сверху (C12).
 */
internal object StoryTextBlocks {

    fun build(context: Context, slide: Slide, host: StorySlideHost): View {
        val frame = FrameLayout(context)
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
            frame.addView(block, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            frame.post {
                // Блок не уезжает за низ области: его должно быть видно целиком.
                val top = frame.height * element.yOffset / 100f
                block.y = top.coerceAtMost((frame.height - block.height).toFloat()).coerceAtLeast(0f)
            }
        }
        return frame
    }

    private fun copy(anchor: View, code: String, host: StorySlideHost) {
        val clipboard = anchor.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(host.texts.promoCaption, code))
        PersonalizationToast.show(anchor, host.texts.promoCopiedText, PersonalizationToast.Position.TOP)
        host.onPromoCopied()
    }
}
