package com.personalization.ui.widgets

import android.graphics.Color
import com.personalization.Product
import com.personalization.stories.models.Slide
import com.personalization.stories.models.Story
import com.personalization.stories.models.elements.ButtonElement
import com.personalization.stories.models.elements.ProductElement
import com.personalization.stories.models.elements.ProductsElement
import com.personalization.stories.models.elements.TextBlockElement
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.components.PersonalizationStoryCover
import com.personalization.ui.components.PersonalizationStoryItem
import com.personalization.ui.components.PersonalizationStoryPrice
import com.personalization.ui.components.PersonalizationStoryTextBlock

/**
 * Перевод моделей сторис SDK (те же, что у старого `StoriesView`) в данные компонентов кита.
 * Отдельно от вьюх — чтобы проверялось тестами без экрана.
 */
@InternalPersonalizationUiApi
internal object StoriesMapping {

    /** Цена товара на слайде: промокод важнее скидки, скидка важнее просто цены. */
    data class Price(
        val type: PersonalizationStoryPrice.Type,
        val price: String,
        val oldPrice: String?,
        val discount: String?,
        val promoCode: String?
    )

    fun price(product: Product): Price {
        val old = product.oldPrice.takeIf { it.isNotEmpty() && it != product.price }
        val promo = product.promocode.takeIf { it.isNotEmpty() }
        if (promo != null) {
            val withPromo = product.priceWithPromocode.takeIf { it.isNotEmpty() }
            // Без старой цены зачёркивается обычная: промокод снижает именно её.
            val crossed = old ?: product.price.takeIf { withPromo != null && withPromo != it }
            return Price(PersonalizationStoryPrice.Type.PROMO_CODE, withPromo ?: product.price, crossed, null, promo)
        }
        val percent = product.discountPercent.toDoubleOrNull()?.takeIf { it > 0 }
        if (percent != null) {
            val text = if (percent % 1.0 == 0.0) "${percent.toLong()}%" else "$percent%"
            return Price(PersonalizationStoryPrice.Type.DISCOUNT, product.price, old, text, null)
        }
        return Price(PersonalizationStoryPrice.Type.PRICE, product.price, old, null, null)
    }

    /** Товар карусели «Используемые товары» → карточка кита. */
    fun cardProduct(product: Product): PersonalizationProduct {
        val old = product.oldPrice.takeIf { it.isNotEmpty() && it != product.price }
        val discount = product.discount.takeIf { old != null && it.isNotEmpty() && it != "0" }
            ?.let { if (it.endsWith("%")) it else "$it%" }
            ?.let { if (it.startsWith("-")) it else "-$it" }
        return PersonalizationProduct(
            id = product.id,
            name = product.name,
            price = product.price,
            imageUrl = product.image.takeIf { it.isNotEmpty() },
            brand = product.brand.takeIf { it.isNotEmpty() },
            oldPrice = old,
            discount = discount
        )
    }

    /** Состояние обложки: закреплённая остаётся закреплённой и после просмотра. */
    fun item(story: Story): PersonalizationStoryItem {
        val state = when {
            story.isPinned -> PersonalizationStoryCover.State.PINNED
            story.isViewed -> PersonalizationStoryCover.State.VIEWED
            else -> PersonalizationStoryCover.State.DEFAULT
        }
        return PersonalizationStoryItem(
            id = story.id.toString(),
            title = story.name,
            imageUrl = story.avatar.takeIf { it.isNotEmpty() },
            state = state,
            segments = story.slidesCount.coerceAtLeast(1),
            seenSegments = if (story.isViewed) 0 else story.startPosition.coerceIn(0, story.slidesCount)
        )
    }

    /** С какого слайда открыть: с места, где остановились, если сторис не досмотрена. */
    fun startSlide(story: Story): Int =
        if (story.isViewed) 0 else story.startPosition.coerceIn(0, (story.slidesCount - 1).coerceAtLeast(0))

    fun product(slide: Slide): ProductElement? =
        slide.getElements().filterIsInstance<ProductElement>().firstOrNull { it.item != null }

    fun products(slide: Slide): ProductsElement? =
        slide.getElements().filterIsInstance<ProductsElement>().firstOrNull { it.getProducts().isNotEmpty() }

    fun buttons(slide: Slide): List<ButtonElement> =
        slide.getElements().filterIsInstance<ButtonElement>().filter { it.title.isNotBlank() }

    fun textBlocks(slide: Slide): List<TextBlockElement> =
        slide.getElements().filterIsInstance<TextBlockElement>().filter { it.textInput.isNotBlank() }

    /** Медиа-слайд — с картинкой или видео фоном: он рисуется тёмными токенами. */
    fun isMedia(slide: Slide): Boolean = slide.background.isNotEmpty()

    fun isVideo(slide: Slide): Boolean = slide.type == "video" && slide.background.isNotEmpty()

    /** Подложка текстового блока: цвет админки с её прозрачностью; 0% — блок без фона. */
    fun textBlockColor(element: TextBlockElement): Int? {
        val opacity = element.textBackgroundColorOpacity.trim().removeSuffix("%").toIntOrNull() ?: 0
        if (opacity <= 0) return null
        val color = parseColor(element.textBackgroundColor) ?: return null
        val alpha = (opacity.coerceAtMost(100) * 255) / 100
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun textAlign(element: TextBlockElement): PersonalizationStoryTextBlock.Align = when (element.textAlign) {
        "center" -> PersonalizationStoryTextBlock.Align.CENTER
        "right", "end" -> PersonalizationStoryTextBlock.Align.END
        else -> PersonalizationStoryTextBlock.Align.START
    }

    fun parseColor(value: String?): Int? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { Color.parseColor(it.trim()) }.getOrNull() }
}
