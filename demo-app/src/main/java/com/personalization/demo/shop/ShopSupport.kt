package com.personalization.demo.shop

import android.content.Context
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.personalization.R as SdkR
import com.personalization.api.responses.product.Product
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationEmptyState
import com.personalization.ui.components.PersonalizationLoader
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.components.PersonalizationTitle
import com.personalization.ui.widgets.PersonalizationListingScreen
import java.math.BigDecimal

/** Recommender block of the demo shop (c1140c…) that has products: "Популярные" on home and on a product. */
internal const val SHOP_BLOCK_CODE = "e6249bb15043644bf25b135006149962"

/** Product page blocks driven by the product (`item`): "Похожие" and "С этим товаром также покупают". */
internal const val SIMILAR_BLOCK_CODE = "007b87897f244b2a2a123567b3242a4b"
internal const val ALSO_BOUGHT_BLOCK_CODE = "fa6e8cd83434273dd8d47293329e00dd"

internal const val ADD_TO_CART_TEXT = "Add to cart"
internal const val ERROR_TEXT = "Something went wrong. Check your connection and try again."
internal const val RETRY_TEXT = "Retry"

/**
 * API product → kit card. The kit's own mapper is internal to the SDK, so the demo keeps a small one
 * by the same rules:
 * - price — `price_formatted`, otherwise the number with the currency;
 * - old price — `oldprice_formatted`, only when `oldprice` is greater than the price;
 * - discount badge — only next to an old price and only for a positive number (`discount`, otherwise
 *   `discount_percent`): `discount_formatted` for `discount`, otherwise "N%", with exactly one "-";
 * - image — `picture` (resized copy), otherwise `image_url`; links — `deeplink_android` and `url`.
 */
internal fun Product.toCard(actionText: CharSequence? = ADD_TO_CART_TEXT): PersonalizationProduct {
    // Gson leaves null in fields the answer lacks, the non-null types notwithstanding.
    val productId: String? = id
    val title: String? = name
    val priceText: String? = priceFormatted
    val currencyText: String? = currency
    val brandName: String? = brand
    val pictureUrl: String? = picture
    val originalUrl: String? = imageUrl
    val pageUrl: String? = url
    val hasOldPrice = (oldPrice ?: 0.0) > price && !oldPriceFormatted.isNullOrEmpty()
    val ownDiscount = discount?.takeIf { it > 0 }
    val discountNumber = (ownDiscount ?: discountPercent?.takeIf { it > 0 })?.takeIf { hasOldPrice }
    val discountText = discountNumber?.let { number ->
        val formatted = discountFormatted?.takeIf { ownDiscount != null }
            ?.trimStart { it == '-' || it.isWhitespace() }
            ?.takeIf { it.isNotEmpty() }
        "-" + (formatted ?: "$number%")
    }
    return PersonalizationProduct(
        id = productId.orEmpty(),
        name = title.orEmpty(),
        price = priceText?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(plainNumber(price), currencyText?.takeIf { it.isNotEmpty() }).joinToString(" "),
        imageUrl = pictureUrl?.takeIf { it.isNotEmpty() } ?: originalUrl?.takeIf { it.isNotEmpty() },
        brand = brandName?.takeIf { it.isNotEmpty() },
        oldPrice = if (hasOldPrice) oldPriceFormatted else null,
        discount = discountText,
        actionText = actionText,
        url = pageUrl?.takeIf { it.isNotEmpty() },
        deeplink = deeplinkAndroid?.takeIf { it.isNotEmpty() }
    )
}

/** `1.6499E7` → `16499000`, `90.0` → `90`. */
private fun plainNumber(value: Double): String =
    if (value.isFinite()) BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() else value.toString()

/** A sort of the shop's picker: the label and what goes to the screen's `sortBy` / `sortDir`. */
private class ShopSort(val label: String, val by: String?, val dir: String?)

private val SHOP_SORTS = listOf(
    ShopSort("Relevance", null, null),
    ShopSort("Price: low to high", "price", "asc"),
    ShopSort("Price: high to low", "price", "desc"),
    ShopSort("Discount", "discount", "desc")
)

/**
 * The sort picker of Search and Category — the kit draws none, so a plain platform dialog. The choice
 * goes to the screen, which asks for the first page again.
 */
internal fun pickSort(context: Context, screen: PersonalizationListingScreen) {
    val current = SHOP_SORTS.indexOfFirst { it.by == screen.sortBy && it.dir == screen.sortDir }
    AlertDialog.Builder(context)
        .setTitle("Sort by")
        .setSingleChoiceItems(SHOP_SORTS.map { it.label }.toTypedArray(), current) { dialog, which ->
            screen.sortBy = SHOP_SORTS[which].by
            screen.sortDir = SHOP_SORTS[which].dir
            dialog.dismiss()
        }
        .show()
}

private val mainHandler = Handler(Looper.getMainLooper())

/** SDK callbacks arrive on a background thread; views are touched on the main one. */
internal fun onMain(block: () -> Unit) {
    mainHandler.post(block)
}

internal fun Context.spacing(res: Int): Int = resources.getDimensionPixelSize(res)

/** Page background of the kit, so its components sit on the surface they were drawn for, light or dark. */
internal fun Context.pageBackground(): Int =
    PersonalizationTheme.color(this, SdkR.color.personalization_background_generic)

/** Text in the kit's type scale and colours — the product page has no ready kit component for these. */
internal fun Context.kitText(
    fontSize: Int,
    lineHeight: Int,
    color: Int,
    emphasized: Boolean = false,
    strikeThrough: Boolean = false
): AppCompatTextView = AppCompatTextView(this).apply {
    includeFontPadding = false
    typeface = PersonalizationTheme.typeface(context, emphasized)
    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(fontSize))
    TextViewCompat.setLineHeight(this, resources.getDimensionPixelSize(lineHeight))
    setTextColor(PersonalizationTheme.color(context, color))
    if (strikeThrough) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
}

/** Kit title row with a back button on the left, as the search results title has. */
internal fun Context.backTitle(text: CharSequence?, onBack: () -> Unit): PersonalizationTitle =
    PersonalizationTitle(this).apply {
        this.text = text
        setLeading(PersonalizationButton(context).apply {
            size = PersonalizationButton.Size.MD
            buttonView = PersonalizationButton.ButtonView.GHOST
            iconStart = SdkR.drawable.personalization_ic_arrow_left
            contentDescription = "Back"
            setOnClickListener { onBack() }
        })
    }

/**
 * Loading and failure of a screen or a section, from kit parts: the loader, or the empty-state text
 * with a Retry button under it (the same pair the catalog shows for a failed first page).
 */
internal class ShopStatus(context: Context) : FrameLayout(context) {

    private val loader = PersonalizationLoader(context)
    private val message = PersonalizationEmptyState(context)
    private val retry = PersonalizationButton(context)
    private val failure = LinearLayout(context)

    init {
        val gap = context.spacing(SdkR.dimen.personalization_spacing_xl3)
        setPadding(0, gap, 0, gap)
        addView(loader, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
        retry.size = PersonalizationButton.Size.MD
        retry.buttonView = PersonalizationButton.ButtonView.SECONDARY
        retry.iconStart = SdkR.drawable.personalization_ic_arrow_rotate_cw
        retry.text = RETRY_TEXT
        failure.orientation = LinearLayout.VERTICAL
        failure.gravity = Gravity.CENTER_HORIZONTAL
        failure.addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        failure.addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(failure, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        isVisible = false
    }

    fun loading() {
        isVisible = true
        loader.isVisible = true
        failure.isVisible = false
    }

    fun failed(onRetry: () -> Unit) {
        isVisible = true
        loader.isVisible = false
        message.text = ERROR_TEXT
        retry.setOnClickListener { onRetry() }
        failure.isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}
