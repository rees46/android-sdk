@file:SuppressLint("ClickableViewAccessibility", "ViewConstructor", "ResourceAsColor")

package com.personalization.stories.views.storyItem

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.common.base.Strings
import com.personalization.Product
import com.personalization.R
import com.personalization.SDK
import com.personalization.stories.Settings
import com.personalization.stories.StoryState
import com.personalization.stories.models.Slide
import com.personalization.stories.models.elements.ButtonElement
import com.personalization.stories.models.elements.HeaderElement
import com.personalization.stories.models.elements.ProductElement
import com.personalization.stories.models.elements.ProductsElement
import com.personalization.stories.models.elements.TextBlockElement
import com.personalization.stories.viewAdapters.ProductsAdapter
import com.personalization.stories.views.StoryDialog
import com.personalization.ui.utils.ColorUtils
import com.personalization.ui.utils.TextUtils

class StoryItemView(
    private val context: Context,
    private val code: String,
    private val settings: Settings,
    private val itemClickListener: com.personalization.OnClickListener?,
    private val storyStateListener: StoryDialog.OnStoryStateListener,
    private val needOpeningWebView: Boolean
) : ConstraintLayout(context) {

    interface OnPageListener {
        fun onPrev()

        fun onNext()

        fun onPrepared(position: Int)

        fun onLocked(lock: Boolean)
    }

    lateinit var image: ImageView
        private set
    lateinit var video: PlayerView
        private set
    private lateinit var product: ConstraintLayout
    private lateinit var productPriceBox: LinearLayout
    private lateinit var productDiscountBox: LinearLayout
    private lateinit var productOldPrice: TextView
    private lateinit var productDiscount: TextView
    private lateinit var promocodeText: TextView
    private lateinit var productBrand: TextView
    private lateinit var productPrice: TextView
    private lateinit var productName: TextView
    private lateinit var productImage: ImageView

    private var pageListener: OnPageListener? = null
    private lateinit var prev: View
    private lateinit var next: View

    private lateinit var button: Button
    private lateinit var textBlocksLayout: FrameLayout
    lateinit var reload: ImageButton
    lateinit var reloadLayout: View
    private lateinit var reloadText: TextView
    private lateinit var header: ConstraintLayout
    private lateinit var titleTextView: TextView
    private lateinit var subtitleTextView: TextView
    private lateinit var titleCardView: CardView
    private lateinit var titleIconImageView: ImageView
    private lateinit var buttonProducts: Button
    private lateinit var elementsLayout: ViewGroup
    private lateinit var products: RecyclerView
    private lateinit var productsAdapter: ProductsAdapter
    private var buttonTextShow = ""
    private var buttonTextHide = ""

    private var viewHeight = 0
    private var viewTopOffset = 0

    init {
        inflate(context, R.layout.story_item, this)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        initViews()
        setupViews()
    }

    private fun initViews() {
        image = findViewById(android.R.id.background)
        video = findViewById(android.R.id.widget_frame)

        prev = findViewById(R.id.reverse)
        next = findViewById(R.id.skip)

        button = findViewById(android.R.id.button1)
        textBlocksLayout = findViewById(R.id.text_blocks_layout)
        reload = findViewById(R.id.reload)
        reloadLayout = findViewById(R.id.reload_layout)
        reloadText = findViewById(R.id.reload_text)
        header = findViewById(R.id.header)
        titleTextView = findViewById(R.id.title_textView)
        subtitleTextView = findViewById(R.id.subtitle_textView)
        titleIconImageView = findViewById(R.id.title_imageView)
        titleCardView = findViewById(R.id.titleCardView)
        buttonProducts = findViewById(android.R.id.button2)
        elementsLayout = findViewById(R.id.elements_layout)

        products = findViewById(android.R.id.list)

        product = findViewById(R.id.product)

        productPriceBox = findViewById(R.id.product_price_box)
        productBrand = findViewById(R.id.product_brand)
        productName = findViewById(R.id.product_name)
        productOldPrice = findViewById(R.id.product_oldprice)
        productPrice = findViewById(R.id.product_price)
        productDiscount = findViewById(R.id.product_discount)
        productDiscountBox = findViewById(R.id.product_discount_box)
        productImage = findViewById(R.id.product_image)
        promocodeText = findViewById(R.id.promocode_text)
    }

    private fun setupViews() {
        image.visibility = GONE
        video.visibility = GONE

        prev.setOnClickListener {
            pageListener?.onPrev()
        }
        next.setOnClickListener {
            pageListener?.onNext()
        }

        productsAdapter = ProductsAdapter(
            itemClickListener = itemClickListener,
            code = code,
            settings = settings,
            onCloseStories = {
                hideProducts()
            }
        )
        products.adapter = productsAdapter

        product.visibility = GONE
    }

    /**
     * @param listener Event on slide switch message
     */
    fun setOnPageListener(listener: OnPageListener?) {
        pageListener = listener
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setOnTouchListener(listener: OnTouchListener) {
        prev.setOnTouchListener(listener)
        next.setOnTouchListener(listener)
        super.setOnTouchListener { _: View?, _: MotionEvent? -> true }
    }

    fun setViewSize(height: Int, topOffset: Int) {
        viewHeight = height
        viewTopOffset = topOffset
    }

    /**
     * Updates slide data
     *
     * @param slide Story.Slide
     * @param position Story position
     * @param code Stories block code
     * @param storyId Story ID
     */
    @SuppressLint("ResourceAsColor")
    fun update(slide: Slide, position: Int, code: String, storyId: Int) {
        slide.isPrepared = false

        setBackgroundColor(
            ColorUtils.getColor(
                context,
                slide.backgroundColor,
                android.R.color.black
            )
        )

        video.visibility = GONE

        setupReloadView(slide, position, code, storyId)

        setButtonOnClickListener(slide, code, storyId)

        if (slide.type == "image") {
            loadImage(
                url = slide.background,
                listener = object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        reloadLayout.visibility = VISIBLE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        onPreparedSlide(slide, position)
                        return false
                    }
                }
            )
        }

        if (slide.type == "video") {
            video.visibility = VISIBLE

            val preview = slide.preview
            if (preview.isNotEmpty()) {
                loadImage(
                    url = preview,
                    listener = object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }
                    }
                )
            }
        }

        setupElements(slide, code, storyId, position)
    }

    private fun onPreparedSlide(slide: Slide, position: Int) {
        slide.isPrepared = true
        reloadLayout.visibility = GONE
        elementsLayout.visibility = VISIBLE
        if (!buttonProducts.isActivated) {
            pageListener?.onPrepared(position)
        }
    }

    private fun setButtonOnClickListener(slide: Slide, code: String, storyId: Int) {
        button.setOnClickListener {
            try {
                var product: Product? = null
                var link: String? = null

                for (element in slide.getElements()) {
                    when (element) {
                        is ProductElement -> product = element.item
                        is ButtonElement -> link = element.link
                    }
                }
                Log.d(
                    SDK.TAG,
                    "open link: " + link + (if (product != null) " with product: `" + product.id + "`" else "")
                )

                storyStateListener.onStoryStateChanged(StoryState.CLOSE)

                SDK.instance.trackStory(
                    event = "click",
                    code = code,
                    storyId = storyId,
                    slideId = slide.id
                )

                if (needOpeningWebView && link != null) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    )
                }
            } catch (e: ActivityNotFoundException) {
                Log.e(SDK.TAG, e.message, e)
                Toast.makeText(context, "Unknown error", Toast.LENGTH_SHORT).show()
            } catch (e: NullPointerException) {
                Log.e(SDK.TAG, e.message, e)
                Toast.makeText(context, "Unknown error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Loads a slide image
     *
     * @param url      String
     * @param listener RequestListener<Drawable>
     */
    private fun loadImage(url: String, listener: RequestListener<Drawable>) {
        image.visibility = VISIBLE
        Glide.with(context).load(url).listener(listener).into(image)
    }

    private fun updateProduct(element: ProductElement, listener: RequestListener<Drawable>) {
        val item = element.item

        product.visibility = VISIBLE

        Glide.with(context).load(item!!.image).listener(listener).override(Target.SIZE_ORIGINAL)
            .into(
                productImage
            )

        setupDefaultTextView(productBrand, item.brand.isNotEmpty(), item.brand)
        setupDefaultTextView(productName, item.name)
        setupDefaultTextView(productPrice, item.price)
        setupDefaultTextView(productOldPrice, item.oldPrice.isNotEmpty(), item.oldPrice)
        setupDefaultTextView(promocodeText, item.promocode.isNotEmpty(), element.title)
        productOldPrice.paintFlags = productOldPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        productDiscountBox.visibility =
            if (item.discountPercent.isEmpty() && item.promocode.isEmpty()) GONE else VISIBLE

        val radius = resources.getDimension(R.dimen.product_price_box_radius)
        val shapeBox =
            ShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, radius).build()
        val shapeBoxDrawable = MaterialShapeDrawable(shapeBox)
        val shapeDiscount = ShapeAppearanceModel().toBuilder()
            .setTopRightCorner(CornerFamily.ROUNDED, radius)
            .setBottomRightCorner(CornerFamily.ROUNDED, radius)
            .build()
        val shapeDiscountDrawable = MaterialShapeDrawable(shapeDiscount)

        shapeBoxDrawable.fillColor = ContextCompat.getColorStateList(context, android.R.color.white)
        ViewCompat.setBackground(productPriceBox, shapeBoxDrawable)

        var productDiscountText = ""
        var shapeDiscountColor = 0
        if (!Strings.isNullOrEmpty(item.promocode)) {
            productDiscountText = item.promocode
            shapeDiscountColor = R.color.product_promocode_color
            productPrice.text = item.priceWithPromocode
        } else if (!Strings.isNullOrEmpty(item.discountPercent)) {
            productDiscountText = "-" + item.discountPercent + "%"
            shapeDiscountColor = R.color.product_discount_color
        }
        setupDefaultTextView(productDiscount, productDiscountText)
        if (shapeDiscountColor != 0) {
            shapeDiscountDrawable.fillColor =
                ContextCompat.getColorStateList(context, shapeDiscountColor)
        }

        ViewCompat.setBackground(productDiscountBox, shapeDiscountDrawable)
    }

    fun release() {
        video.player = null
    }

    private fun updateHeader(
        element: HeaderElement,
        slideId: String,
        code: String,
        storyId: Int
    ) {
        header.visibility = VISIBLE
        header.setOnTouchListener { _: View?, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (needOpeningWebView) {
                    context.startActivity(
                        Intent(
                            /* action = */ Intent.ACTION_VIEW,
                            /* uri = */ Uri.parse(element.link)
                        )
                    )
                }
                SDK.instance.trackStory(
                    event = "click",
                    code = code,
                    storyId = storyId,
                    slideId = slideId
                )
            }
            true
        }

        if (element.icon.isNotEmpty()) {
            titleCardView.visibility = VISIBLE
            Glide.with(context).load(element.icon).into(titleIconImageView)
        } else {
            titleCardView.visibility = GONE
        }

        val title = element.title
        setupDefaultTextView(titleTextView, title.isNotEmpty(), title)

        val subtitle = element.subtitle
        setupDefaultTextView(subtitleTextView, subtitle.isNotEmpty(), subtitle)
    }

    private fun setupElements(slide: Slide, code: String, storyId: Int, position: Int) {
        header.visibility = GONE
        button.visibility = GONE
        buttonProducts.visibility = GONE
        products.visibility = GONE
        textBlocksLayout.removeAllViews()

        for (element in slide.getElements()) {
            when (element) {
                is HeaderElement -> updateHeader(element, slide.id, code, storyId)
                is TextBlockElement -> updateTextBlock(element)
                is ButtonElement -> updateButton(element)
                is ProductsElement -> updateProducts(element, slide.id, storyId)
                is ProductElement -> {
                    updateProduct(
                        element,
                        object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable?>,
                                isFirstResource: Boolean
                            ): Boolean {
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                slide.isPrepared = true
                                if (pageListener != null) {
                                    pageListener!!.onPrepared(position)
                                }
                                return false
                            }
                        }
                    )
                }
            }
        }
    }

    private fun updateProducts(element: ProductsElement, slideId: String, storyId: Int) {
        productsAdapter.setProducts(element.getProducts(), storyId, slideId)

        buttonTextShow = element.labelShow
        buttonTextHide = element.labelHide

        setupTextView(
            textView = buttonProducts,
            visibility = true,
            text = buttonTextShow,
            typeface = settings.products_button_font_family
        )

        buttonProducts.setOnClickListener {
            buttonProducts.isActivated = !buttonProducts.isActivated
            buttonProducts.text = if (buttonProducts.isActivated) buttonTextHide else buttonTextShow

            toggleProductsVisibility(buttonProducts.isActivated)
        }

        pageListener?.onLocked(buttonProducts.isActivated)
    }

    private fun hideProducts() {
        buttonProducts.isActivated = false
        buttonProducts.text = buttonTextShow
        toggleProductsVisibility(false)
    }

    private fun toggleProductsVisibility(isVisible: Boolean) {
        val set = ConstraintSet()
        set.clone(elementsLayout as ConstraintLayout?)

        val transition: Transition = ChangeBounds().apply {
            addTarget(buttonProducts.id)
            addTarget(button.id)
        }

        val transition2: Transition = androidx.transition.Slide(Gravity.BOTTOM).apply {
            addTarget(products.id)
        }

        val transitions = TransitionSet().apply {
            addTransition(transition)
            addTransition(transition2)
        }

        TransitionManager.beginDelayedTransition(elementsLayout, transitions)
        products.visibility = if (isVisible) View.VISIBLE else View.GONE

        pageListener?.onLocked(isVisible)
    }

    private fun updateButton(element: ButtonElement) {
        button.visibility = VISIBLE
        button.text = element.title

        ColorUtils.setBackgroundButtonColor(
            context = context,
            button = button,
            colorString = element.background,
            defaultColor = R.color.primary
        )
        TextUtils.setTextColor(
            context = context,
            button = button,
            colorString = element.color,
            defaultColor = R.color.white
        )

        button.typeface = Typeface.create(
            /* family = */ settings.button_font_family,
            /* style = */ when {
                element.textBold -> Typeface.BOLD
                else -> Typeface.NORMAL
            }
        )
    }

    private fun updateTextBlock(element: TextBlockElement) {
        val textBlockView = TextBlockView(context)

        textBlockView.updateView(element, viewHeight, viewTopOffset)

        textBlockView.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("TextBlock", element.textInput)
            clipboard.setPrimaryClip(clip)

            Log.d("TextBlockView", "Text copied: ${element.textInput}")
        }

        textBlocksLayout.addView(textBlockView)
    }

    private fun setupReloadView(slide: Slide, position: Int, code: String, storyId: Int) {
        reloadLayout.visibility = GONE

        if (settings.failed_load_text != null) {
            setupTextView(reloadText, settings.failed_load_text!!, settings.failed_load_font_family)
        }
        reloadText.textSize = settings.failed_load_size.toFloat()
        reloadText.setTextColor(Color.parseColor(settings.failed_load_color))

        reload.setOnClickListener { update(slide, position, code, storyId) }
    }

    private fun setupDefaultTextView(textView: TextView, visibility: Boolean, text: String) {
        setupTextView(textView, visibility, text, settings.font_family)
    }

    private fun setupTextView(
        textView: TextView,
        visibility: Boolean,
        text: String,
        typeface: Typeface?
    ) {
        textView.visibility = if (visibility) VISIBLE else GONE

        if (visibility) {
            setupTextView(textView, text, typeface)
        }
    }

    private fun setupDefaultTextView(textView: TextView, text: String) {
        setupTextView(textView, text, settings.font_family)
    }

    private fun setupTextView(textView: TextView, text: String, typeface: Typeface?) {
        textView.text = text
        if (typeface != null) {
            textView.typeface = typeface
        }
    }
}
