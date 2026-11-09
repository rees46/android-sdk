package com.personalization.demo.shop

import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.personalization.R as SdkR
import com.personalization.api.responses.product.Product
import com.personalization.demo.R
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationEmptyState
import com.personalization.ui.components.PersonalizationLoader
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.widgets.PersonalizationListingScreen
import java.math.BigDecimal

/** Recommender block of the demo shop (c1140c…) that has products: "Популярные" on home and on a product. */
internal const val SHOP_BLOCK_CODE = "e6249bb15043644bf25b135006149962"

/** Product page blocks driven by the product (`item`): "Похожие" and "С этим товаром также покупают". */
internal const val SIMILAR_BLOCK_CODE = "007b87897f244b2a2a123567b3242a4b"
internal const val ALSO_BOUGHT_BLOCK_CODE = "fa6e8cd83434273dd8d47293329e00dd"

internal const val SHOP_TITLE_TEXT = "Shop"
internal const val SORT_TITLE_TEXT = "Sort by"
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

/**
 * Facet titles of the demo shop's feed, by the facet's name in the answer; the rest keep the kit's
 * default. Values of `stickers` already carry their own title, the kit shows it.
 */
private val SHOP_FACET_TITLES = mapOf(
    "stickers" to "Стикеры",
    "stickers_liquid" to "Стикеры",
    "product_type" to "Тип товара"
)

/** Value titles of the demo shop's feed: facet → value → title. */
private val SHOP_FACET_VALUE_TITLES = mapOf(
    "product_type" to mapOf("regular" to "Обычный", "digital" to "Цифровой", "mto" to "Под заказ")
)

/**
 * The integrator's setting for its own feed: the kit knows only the format of the answer, so names
 * of facets and values that read poorly are titled here.
 */
internal fun PersonalizationListingScreen.useShopFacetTitles() {
    val kitTitle = facetTitle
    facetTitle = { name -> SHOP_FACET_TITLES[name] ?: kitTitle(name) }
    facetValueTitle = { facet, value -> SHOP_FACET_VALUE_TITLES[facet]?.get(value) }
}

/** A sort of the shop's picker: the label and what goes to the screen's `sortBy` / `sortDir`. */
private class ShopSort(val label: String, val by: String?, val dir: String?)

private val SHOP_SORTS = listOf(
    ShopSort("Relevance", null, null),
    ShopSort("Price: low to high", "price", "asc"),
    ShopSort("Price: high to low", "price", "desc"),
    ShopSort("Discount", "discount", "desc")
)

/**
 * The sort picker of Search and Category — the kit draws none, so a bottom sheet of the platform on the
 * kit's surface and type: "Sort by", then the sorts, the current one checked. A tap picks and closes;
 * the choice goes to the screen, which asks for the first page again.
 */
internal fun pickSort(context: Context, screen: PersonalizationListingScreen) {
    val current = SHOP_SORTS.indexOfFirst { it.by == screen.sortBy && it.dir == screen.sortDir }
    val dialog = BottomSheetDialog(context, R.style.ThemeOverlay_PersonalizationDemo_ShopSheet)
    val sheetContext = dialog.context
    val edge = sheetContext.spacing(SdkR.dimen.personalization_spacing_padding_modal)
    val list = LinearLayout(sheetContext).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, edge, 0, sheetContext.spacing(SdkR.dimen.personalization_spacing_md))
    }
    list.addView(
        sheetContext.kitText(
            SdkR.dimen.personalization_font_size_lg, SdkR.dimen.personalization_line_height_lg,
            SdkR.color.personalization_text_primary, emphasized = true
        ).apply {
            text = SORT_TITLE_TEXT
            setPadding(edge, 0, edge, sheetContext.spacing(SdkR.dimen.personalization_spacing_md))
        },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    )
    SHOP_SORTS.forEachIndexed { index, sort ->
        list.addView(
            sortRow(sheetContext, sort.label, checked = index == current) {
                screen.sortBy = sort.by
                screen.sortDir = sort.dir
                dialog.dismiss()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }
    dialog.setContentView(list)
    // From API 27 the navigation bar takes dark icons: the sheet runs under a transparent bar.
    dialog.window?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 }?.let { window ->
        window.navigationBarColor = ContextCompat.getColor(context, android.R.color.transparent)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !context.isNight
    }
    dialog.behavior.skipCollapsed = true
    dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    dialog.show()
}

/** A row of the sort sheet: the label, and the brand check mark on the right when it is the current sort. */
private fun sortRow(context: Context, label: String, checked: Boolean, onClick: () -> Unit): View =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(ROW_MIN_HEIGHT_DP)
        val edge = context.spacing(SdkR.dimen.personalization_spacing_padding_modal)
        val gap = context.spacing(SdkR.dimen.personalization_spacing_lg)
        setPadding(edge, gap, edge, gap)
        setSelectableBackground()
        addView(
            context.kitText(
                SdkR.dimen.personalization_font_size_base, SdkR.dimen.personalization_line_height_base,
                SdkR.color.personalization_text_primary
            ).apply { text = label },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(
            AppCompatImageView(context).apply {
                setImageResource(R.drawable.ic_shop_check)
                ImageViewCompat.setImageTintList(
                    this, PersonalizationTheme.colorStateList(context, SdkR.color.personalization_brand_primary)
                )
                visibility = if (checked) View.VISIBLE else View.INVISIBLE
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = gap
            }
        )
        isSelected = checked
        setOnClickListener { onClick() }
    }

private val mainHandler = Handler(Looper.getMainLooper())

/** SDK callbacks arrive on a background thread; views are touched on the main one. */
internal fun onMain(block: () -> Unit) {
    mainHandler.post(block)
}

internal fun Context.spacing(res: Int): Int = resources.getDimensionPixelSize(res)

/** Sizes the kit has no token for, taken from the shop's layout: rows of lists, the round buttons. */
internal const val ROW_MIN_HEIGHT_DP = 52
private const val CIRCLE_BUTTON_DP = 44
private const val ICON_DP = 24

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal val Context.isNight: Boolean
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** Page background of the kit, so its components sit on the surface they were drawn for, light or dark. */
internal fun Context.pageBackground(): Int =
    PersonalizationTheme.color(this, SdkR.color.personalization_background_generic)

/**
 * The system bars the view sits under — [apply] gets the top (status bar, cutout) and the bottom (navigation
 * bar, or the keyboard when it is taller), now and on every change. The shop-only activity draws edge to
 * edge and its screens make room for the bars themselves; in the full demo the activity keeps its content
 * clear of the bars, and both come as zero. The insets go on to the siblings: every screen of the stack
 * gets them.
 */
internal fun View.onSystemBars(apply: (top: Int, bottom: Int) -> Unit) {
    apply(0, 0)
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        apply(bars.top, maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom))
        insets
    }
    // A screen pushed onto a shown stack is not handed the insets by itself.
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}

/** Ripple of the platform on a tap, over whatever the view sits on. */
internal fun View.setSelectableBackground() {
    val value = TypedValue()
    if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
        setBackgroundResource(value.resourceId)
    }
}

/**
 * A rounded rectangle of a kit colour, the corners top-left, top-right, bottom-right, bottom-left. Equal
 * corners go as one radius: only such an outline can clip the view's content.
 */
internal fun Context.surface(colorRes: Int, vararg corners: Float): GradientDrawable = GradientDrawable().apply {
    setColor(PersonalizationTheme.color(this@surface, colorRes))
    if (corners.distinct().size == 1) {
        cornerRadius = corners[0]
    } else {
        cornerRadii = floatArrayOf(
            corners[0], corners[0], corners[1], corners[1], corners[2], corners[2], corners[3], corners[3]
        )
    }
}

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

/**
 * The round back button over the product photo: the chevron on the kit's page background. It comes with
 * its size in [FrameLayout.LayoutParams].
 */
internal fun Context.circleBackButton(onBack: () -> Unit): View = circleButton(
    // The kit has the chevron to the right only; turned round, it points back.
    SdkR.drawable.personalization_ic_angle_large_right, SdkR.color.personalization_background_generic,
    "Back", mirrored = true, onClick = onBack
)

/**
 * A round icon button of the shop's chrome: a 44 circle of a kit surface with the icon in the primary
 * text colour, [label] for accessibility. It comes with its size in [FrameLayout.LayoutParams].
 */
internal fun Context.circleButton(
    icon: Int,
    surfaceRes: Int,
    label: String,
    mirrored: Boolean = false,
    onClick: () -> Unit
): View = FrameLayout(this).apply {
    val size = dp(CIRCLE_BUTTON_DP)
    layoutParams = FrameLayout.LayoutParams(size, size)
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(PersonalizationTheme.color(context, surfaceRes))
    }
    // The ripple of the icon's view stays inside the circle.
    outlineProvider = ViewOutlineProvider.BACKGROUND
    clipToOutline = true
    addView(AppCompatImageView(context).apply {
        setImageResource(icon)
        scaleType = ImageView.ScaleType.FIT_CENTER
        val inset = (size - dp(ICON_DP)) / 2
        setPadding(inset, inset, inset, inset)
        if (mirrored && resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) scaleX = -1f
        ImageViewCompat.setImageTintList(
            this, PersonalizationTheme.colorStateList(context, SdkR.color.personalization_text_primary)
        )
        setSelectableBackground()
        isDuplicateParentStateEnabled = true
    }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    contentDescription = label
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
}

/**
 * Loading and failure of a screen or a section, from kit parts: the loader, or the empty-state text
 * with a Retry button under it (the same pair the catalog shows for a failed first page), centred in
 * the area it is given.
 */
internal class ShopStatus(context: Context) : FrameLayout(context) {

    private val loader = PersonalizationLoader(context)
    private val message = PersonalizationEmptyState(context)
    private val retry = PersonalizationButton(context)
    private val failure = LinearLayout(context)

    init {
        val gap = context.spacing(SdkR.dimen.personalization_spacing_xl3)
        setPadding(0, gap, 0, gap)
        addView(loader, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        retry.size = PersonalizationButton.Size.MD
        retry.buttonView = PersonalizationButton.ButtonView.SECONDARY
        retry.iconStart = SdkR.drawable.personalization_ic_arrow_rotate_cw
        retry.text = RETRY_TEXT
        failure.orientation = LinearLayout.VERTICAL
        failure.gravity = Gravity.CENTER_HORIZONTAL
        failure.addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        failure.addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(failure, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
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
