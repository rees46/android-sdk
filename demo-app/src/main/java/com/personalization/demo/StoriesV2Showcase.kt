package com.personalization.demo

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import com.bumptech.glide.Glide
import com.personalization.stories.models.Story
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.components.PersonalizationStoriesBar
import com.personalization.ui.components.PersonalizationStoryCard
import com.personalization.ui.components.PersonalizationStoryCover
import com.personalization.ui.components.PersonalizationStoryItem
import com.personalization.ui.components.PersonalizationStoryNavigation
import com.personalization.ui.components.PersonalizationStoryPreview
import com.personalization.ui.components.PersonalizationStoryPrice
import com.personalization.ui.components.PersonalizationStoryTextBlock
import com.personalization.ui.components.PersonalizationStoryTimer
import com.personalization.ui.components.PersonalizationStoryUsedProducts
import com.personalization.ui.widgets.PersonalizationStories
import com.personalization.ui.widgets.PersonalizationStoriesViewer
import org.json.JSONObject

/**
 * "Stories V2" tab: the stories block drawn by the UI kit, next to the legacy one in "Stories".
 *
 * The first exhibit is the live block of the demo shop — it loads through the SDK like the legacy
 * StoriesView. The second opens a bundled sample block (`assets/uikit/stories_sample.json`) that has
 * every slide kind the kit draws — product with a discount, with a promo code, text blocks, used
 * products — so the viewer can be checked without depending on what the shop's block holds today.
 * The rest are the building blocks one by one.
 */
@Composable
internal fun StoriesV2Showcase(storiesCode: String, shopId: String) {
    Showcase {
        Exhibit("Stories V2 — live block of the demo shop") { ctx ->
            PersonalizationStories(ctx).apply {
                code = storiesCode
                this.shopId = shopId
            }
        }
        Exhibit("Stories V2 — sample block with every slide kind (offline)") { ctx -> ctx.sampleStories() }
        Exhibit("Story Preview — pinned, default, viewed, skeleton") { ctx -> ctx.previews() }
        Exhibit("Story Cover — ring split by slides, 2 of 5 seen") { ctx ->
            ctx.row(
                PersonalizationStoryCover(ctx).apply {
                    segments = 5
                    seenSegments = setOf(0, 1)
                    ctx.loadAsset(imageView, "sneakers.jpg")
                },
                PersonalizationStoryCover(ctx).apply {
                    segments = 3
                    seenSegments = setOf(0)
                    state = PersonalizationStoryCover.State.PINNED
                    ctx.loadAsset(imageView, "watch.jpg")
                }
            )
        }
        Exhibit("Stories Bar — loading") { ctx -> PersonalizationStoriesBar(ctx).apply { showSkeleton() } }
        Exhibit("Story Navigation — slide 2 of 5, over a photo (dark tokens)") { ctx ->
            val dark = ctx.darkContext()
            ctx.overPhoto(
                "jacket.jpg",
                PersonalizationStoryNavigation(dark).apply {
                    progress.count = 5
                    progress.current = 1
                    progress.progress = 0.5f
                    onClose = { ctx.toast("Close") }
                },
                heightDp = 96
            )
        }
        Exhibit("Story Price — price, discount, promo code (tap to copy), long code") { ctx -> ctx.prices() }
        Exhibit("Story Timer — merged, separate") { ctx ->
            val end = System.currentTimeMillis() + ((2 * 24 + 18) * 3600L + 33 * 60 + 24) * 1000
            ctx.column(
                PersonalizationStoryTimer(ctx).apply { endTimeMillis = end },
                PersonalizationStoryTimer(ctx).apply {
                    style = PersonalizationStoryTimer.Style.SEPARATE
                    endTimeMillis = end
                }
            )
        }
        Exhibit("Story Text Block — solid large / medium / small, transparent") { ctx -> ctx.textBlocks() }
        Exhibit("Story Used Products — collapsed with sound, expanded (dark tokens)") { ctx -> ctx.usedProducts() }
        Exhibit("Story Card — product with a discount, as on a slide") { ctx ->
            ctx.slideFrame(ctx.storyCard(action = false), photo = null)
        }
        Exhibit("Story Card — action over a photo: badge, timer, no image (dark tokens)") { ctx ->
            ctx.slideFrame(ctx.darkContext().storyCard(action = true), photo = "backpack.jpg")
        }
    }
}

// --- Exhibits -----------------------------------------------------------------------------------

private fun Context.sampleStories(): View {
    val json = assets.open("uikit/stories_sample.json").bufferedReader().use { it.readText() }
    val array = JSONObject(json).getJSONArray("stories")
    val stories = List(array.length()) { Story(array.getJSONObject(it)) }
    val bar = PersonalizationStoriesBar(this)
    fun render() {
        bar.items = stories.map { story ->
            PersonalizationStoryItem(
                id = story.id.toString(),
                title = story.name,
                imageUrl = story.avatar,
                state = when {
                    story.isPinned -> PersonalizationStoryCover.State.PINNED
                    story.isViewed -> PersonalizationStoryCover.State.VIEWED
                    else -> PersonalizationStoryCover.State.DEFAULT
                },
                segments = story.slidesCount,
                seenSegments = if (story.isViewed) emptySet() else (0 until story.startPosition).toSet()
            )
        }
    }
    bar.imageLoader = { view, url -> Glide.with(view).load(url).into(view) }
    bar.onItemClick = { index, _ ->
        PersonalizationStoriesViewer(this, stories, index).apply {
            onLinkClick = { link ->
                toast("Link: $link")
                true
            }
            onStoryProgress = { render() }
            setOnDismissListener { render() }
            show()
        }
    }
    render()
    return bar
}

private fun Context.previews(): View {
    fun preview(title: String, state: PersonalizationStoryCover.State, asset: String?) =
        PersonalizationStoryPreview(this).apply {
            this.title = title
            this.state = state
            asset?.let { loadAsset(cover.imageView, it) }
        }
    return hscrollRow(
        preview("Pinned", PersonalizationStoryCover.State.PINNED, "sneakers.jpg"),
        preview("Default", PersonalizationStoryCover.State.DEFAULT, "jacket.jpg"),
        preview("Viewed", PersonalizationStoryCover.State.VIEWED, "watch.jpg"),
        preview("", PersonalizationStoryCover.State.SKELETON, null)
    )
}

private fun Context.prices(): View {
    fun price(type: PersonalizationStoryPrice.Type, promo: String? = null) = PersonalizationStoryPrice(this).apply {
        this.type = type
        price = "1 190 900 ₽"
        oldPrice = "245 000 ₽"
        discount = "10%"
        promoCode = promo
        onPromoCopied = { toast("Copied: $it") }
    }
    return column(
        price(PersonalizationStoryPrice.Type.PRICE).apply { oldPrice = null },
        price(PersonalizationStoryPrice.Type.PRICE),
        price(PersonalizationStoryPrice.Type.DISCOUNT),
        price(PersonalizationStoryPrice.Type.PROMO_CODE, "PROMO"),
        price(PersonalizationStoryPrice.Type.PROMO_CODE, "YAGODA-MALINA")
    )
}

private fun Context.textBlocks(): View {
    val dark = darkContext()
    fun block(size: PersonalizationStoryTextBlock.Size, color: Int?, align: PersonalizationStoryTextBlock.Align = PersonalizationStoryTextBlock.Align.START) =
        PersonalizationStoryTextBlock(dark).apply {
            text = "Open eat dear winding belonging."
            this.size = size
            blockColor = color
            this.align = align
        }
    val stack = LinearLayout(dark).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        listOf(
            block(PersonalizationStoryTextBlock.Size.LARGE, Color.parseColor("#1BAA1B")),
            block(PersonalizationStoryTextBlock.Size.MEDIUM, Color.parseColor("#F37A17")),
            block(PersonalizationStoryTextBlock.Size.SMALL, Color.parseColor("#0087E8")),
            block(PersonalizationStoryTextBlock.Size.LARGE, null),
            block(PersonalizationStoryTextBlock.Size.MEDIUM, null, PersonalizationStoryTextBlock.Align.CENTER),
            block(PersonalizationStoryTextBlock.Size.SMALL, null, PersonalizationStoryTextBlock.Align.END)
        ).forEachIndexed { index, view ->
            addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = dp(16)
            })
        }
    }
    return overPhoto("bottle.jpg", stack, heightDp = null)
}

private fun Context.usedProducts(): View {
    val dark = darkContext()
    val products = listOf(
        PersonalizationProduct("4", "Рюкзак городской Trail 20 л", "4 990 ₽", "file:///android_asset/uikit/backpack.jpg", "Trail", oldPrice = "5 990 ₽", discount = "-17%"),
        PersonalizationProduct("5", "Очки солнцезащитные Sunny", "2 990 ₽", "file:///android_asset/uikit/sunglasses.jpg", "Sunny"),
        PersonalizationProduct("6", "Бутылка для воды Aqua 0,75 л", "990 ₽", "file:///android_asset/uikit/bottle.jpg", "Aqua")
    )
    fun used(expanded: Boolean) = PersonalizationStoryUsedProducts(dark).apply {
        this.products = products
        showText = "Используемые товары"
        hideText = "Скрыть товары"
        imageLoader = { view, product -> product.imageUrl?.let { Glide.with(view).load(it).into(view) } }
        onProductClick = { toast(it.name.toString()) }
        showSoundButton = true
        onSoundClick = { muted = !muted }
        this.expanded = expanded
    }
    return column(
        overPhoto("jacket.jpg", used(expanded = false), heightDp = null),
        overPhoto("jacket.jpg", used(expanded = true), heightDp = null)
    )
}

private fun Context.storyCard(action: Boolean): PersonalizationStoryCard = PersonalizationStoryCard(this).apply {
    if (action) {
        badgeText = "Sale!"
        title = "Скидки до 10%!"
        subtitle = "На рюкзаки и сумки до конца недели"
        showImage = false
        showTimer = true
        timer.endTimeMillis = System.currentTimeMillis() + 3 * 24 * 3600 * 1000L
        price.type = PersonalizationStoryPrice.Type.DISCOUNT
        price.discount = "10%"
    } else {
        title = "Pegasus"
        subtitle = "Кроссовки беговые Pegasus 41, сетчатый верх"
        loadAsset(imageView, "sneakers.jpg")
        price.type = PersonalizationStoryPrice.Type.DISCOUNT
        price.discount = "23%"
    }
    price.price = "9 990 ₽"
    price.oldPrice = "12 990 ₽"
    buttonText = "More info"
    onButtonClick = { toast("More info") }
}

// --- Helpers ------------------------------------------------------------------------------------

/**
 * The kit draws media slides with its dark tokens; the SDK keeps its helper internal, so the demo
 * forces night resources on a subtree the same way to show those exhibits as they appear in a story.
 */
private fun Context.darkContext(): Context {
    val configuration = Configuration(resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
    }
    return ContextThemeWrapper(this, theme).apply { applyOverrideConfiguration(configuration) }
}

/** Decoded straight from the APK — Glide in a Compose-hosted view may never get its pre-draw. */
private fun Context.loadAsset(view: ImageView, name: String) {
    view.setImageBitmap(assetBitmap("uikit/$name"))
}

/** An exhibit over a photo, the way the component sits on a media slide. */
private fun Context.overPhoto(asset: String, content: View, heightDp: Int?): View = fixedHeight(heightDp, FrameLayout(this).apply {
    addView(ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        loadAsset(this, asset)
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    addView(View(context).apply { setBackgroundColor(Color.argb(77, 0, 0, 0)) },
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        if (heightDp != null) setMargins(dp(16), dp(16), dp(16), dp(16))
    })
})

/** A card at a phone-slide size, on the kit background or over a photo. */
private fun Context.slideFrame(card: View, photo: String?): View = fixedHeight(SLIDE_HEIGHT_DP, FrameLayout(this).apply {
    if (photo != null) {
        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            loadAsset(this, photo)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(View(context).apply { setBackgroundColor(Color.argb(77, 0, 0, 0)) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    } else {
        setBackgroundColor(com.personalization.ui.PersonalizationTheme.color(context, com.personalization.R.color.personalization_background_card))
    }
    setPadding(dp(16), dp(16), dp(16), dp(16))
    addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
})

/**
 * Wraps [content] at a fixed height: AndroidView owns the layout params of the view it hosts, so the
 * height has to live on a child of it.
 */
private fun Context.fixedHeight(heightDp: Int?, content: View): View {
    if (heightDp == null) return content
    return FrameLayout(this).apply {
        addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)))
    }
}

/** A slide of the design is 402×874; the exhibit keeps the card at a phone-like height. */
private const val SLIDE_HEIGHT_DP = 620

private fun Context.hscrollRow(vararg children: View): View =
    android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            children.forEachIndexed { index, child ->
                addView(child, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) marginStart = dp(16)
                })
            }
        })
    }

private fun Context.toast(message: String) =
    android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
